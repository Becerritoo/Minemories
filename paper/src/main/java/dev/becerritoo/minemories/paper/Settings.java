package dev.becerritoo.minemories.paper;

import org.bukkit.configuration.file.FileConfiguration;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record Settings(
        String serverName,
        int maxFileBytes,
        int targetFileBytes,
        int maxWidth,
        int maxHeight,
        long maxPixels,
        float jpegQuality,
        int maxCaptionCharacters,
        int ticketLifetimeSeconds,
        int cooldownSeconds,
        int globalUploadsPerMinute,
        int maxConcurrentUploads,
        boolean cameraEnabled,
        boolean requireCameraForServer,
        int cameraDurability,
        int cameraRollSlots,
        int rollCapacity,
        int repairPerGold,
        String cameraCaptureMode,
        boolean serverRendererEnabled,
        int serverRendererDistance,
        int serverRendererQueue,
        int serverRendererUpscale,
        boolean serverRendererEntities,
        boolean serverRendererPublish,
        boolean serverRendererGiveMap,
        boolean allowCoordinates,
        boolean showPlayerUuid,
        URI webhookUri,
        String webhookUsername,
        String webhookAvatarUrl,
        int embedColor,
        String discordSrvChannelId,
        boolean requireDiscordLink,
        boolean requireAuthMe,
        String uploadedMessage,
        String uploadFailedMessage,
        String configurationError
) {
    private static final int HARD_MAX_FILE_BYTES = 500 * 1024;
    private static final int DEFAULT_TARGET_FILE_BYTES = 200 * 1024;
    private static final int MIN_TARGET_FILE_BYTES = 64 * 1024;
    private static final Pattern BYTE_SIZE = Pattern.compile(
            "^([0-9]+(?:\\.[0-9]+)?)\\s*(B|K|KB|KIB|M|MB|MIB|G|GB|GIB)?$",
            Pattern.CASE_INSENSITIVE
    );

    public static Settings load(FileConfiguration config) {
        String webhook = config.getString("discord.webhook-url", "").strip();
        ParsedByteSize targetFileSize = parseFileSize(
                "limits.target-file-bytes", config.get("limits.target-file-bytes"),
                DEFAULT_TARGET_FILE_BYTES,
                MIN_TARGET_FILE_BYTES, HARD_MAX_FILE_BYTES, "64K", "500K"
        );
        return new Settings(
                config.getString("server-name", "Minecraft"),
                HARD_MAX_FILE_BYTES,
                targetFileSize.bytes(),
                clamp(config.getInt("limits.max-width", 1920), 320, 8192),
                clamp(config.getInt("limits.max-height", 1080), 240, 8192),
                clamp(config.getLong("limits.max-pixels", 2_073_600L), 76_800L, 33_554_432L),
                (float) clamp(config.getDouble("limits.jpeg-quality", 0.95), 0.45, 0.98),
                clamp(config.getInt("limits.max-caption-characters", 200), 0, 1000),
                clamp(config.getInt("limits.ticket-lifetime-seconds", 30), 5, 300),
                clamp(config.getInt("limits.cooldown-seconds", 10), 0, 86_400),
                clamp(config.getInt("limits.global-uploads-per-minute", 20), 1, 10_000),
                clamp(config.getInt("limits.max-concurrent-uploads", 2), 1, 16),
                config.getBoolean("camera.enabled", true),
                config.getBoolean("camera.required-for-server", true),
                clamp(config.getInt("camera.durability", 64), 8, 1024),
                clamp(config.getInt("camera.roll-slots", 3), 1, 8),
                clamp(config.getInt("camera.roll-capacity", 12), 1, 64),
                clamp(config.getInt("camera.repair-per-gold", 8), 1, 64),
                captureMode(config.getString("camera.capture-mode", "AUTO")),
                config.getBoolean("camera.server-renderer.enabled", true),
                clamp(config.getInt("camera.server-renderer.max-distance", 48), 16, 64),
                clamp(config.getInt("camera.server-renderer.max-queue", 4), 1, 16),
                clamp(config.getInt("camera.server-renderer.discord-upscale", 4), 1, 8),
                config.getBoolean("camera.server-renderer.render-entities", true),
                config.getBoolean("camera.server-renderer.publish-to-discord", true),
                config.getBoolean("camera.server-renderer.give-map", true),
                config.getBoolean("privacy.allow-coordinates", true),
                config.getBoolean("privacy.show-player-uuid", false),
                parseUri(webhook),
                config.getString("discord.username", "Minemories"),
                config.getString("discord.avatar-url", ""),
                config.getInt("discord.embed-color", 5_793_266),
                config.getString("discordsrv.channel-id", "").strip(),
                config.getBoolean("discordsrv.require-linked-account", false),
                config.getBoolean("authme.require-authenticated", true),
                config.getString("messages.uploaded", "§aTu recuerdo fue publicado en Discord."),
                config.getString("messages.upload-failed", "§cNo se pudo publicar tu recuerdo: {reason}"),
                targetFileSize.error()
        );
    }

    public String validationError() {
        if (configurationError != null) {
            return configurationError;
        }
        if (!discordSrvChannelId.isBlank() && !discordSrvChannelId.matches("[0-9]{17,20}")) {
            return "discordsrv.channel-id debe ser un ID numérico de canal o quedar vacío";
        }
        return null;
    }

    public boolean webhookAvailable() {
        return webhookUri != null && ("http".equalsIgnoreCase(webhookUri.getScheme())
                || "https".equalsIgnoreCase(webhookUri.getScheme()));
    }

    public boolean useClientRenderer() {
        return "CLIENT".equals(cameraCaptureMode);
    }

    public boolean forceServerRenderer() {
        return "SERVER".equals(cameraCaptureMode);
    }

    private static String captureMode(String configured) {
        String mode = configured == null ? "AUTO" : configured.strip().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "CLIENT", "SERVER" -> mode;
            default -> "AUTO";
        };
    }

    private static ParsedByteSize parseFileSize(String path, Object configured, int defaultBytes,
                                                int minimum, int maximum, String minimumLabel,
                                                String maximumLabel) {
        if (configured == null) {
            return new ParsedByteSize(defaultBytes, null);
        }

        String value = configured.toString().strip().toUpperCase(Locale.ROOT);
        Matcher matcher = BYTE_SIZE.matcher(value);
        if (!matcher.matches()) {
            return invalidFileSize(path, value, defaultBytes, minimumLabel, maximumLabel);
        }

        long multiplier = switch (matcher.group(2) == null ? "B" : matcher.group(2)) {
            case "K", "KB", "KIB" -> 1024L;
            case "M", "MB", "MIB" -> 1024L * 1024L;
            case "G", "GB", "GIB" -> 1024L * 1024L * 1024L;
            default -> 1L;
        };
        try {
            long bytes = new BigDecimal(matcher.group(1))
                    .multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (bytes < minimum || bytes > maximum) {
                return invalidFileSize(path, value, defaultBytes, minimumLabel, maximumLabel);
            }
            return new ParsedByteSize((int) bytes, null);
        } catch (ArithmeticException ignored) {
            return invalidFileSize(path, value, defaultBytes, minimumLabel, maximumLabel);
        }
    }

    private static ParsedByteSize invalidFileSize(String path, String value, int defaultBytes,
                                                  String minimumLabel, String maximumLabel) {
        return new ParsedByteSize(defaultBytes,
                path + " ('" + value + "') debe estar entre " + minimumLabel + " y " + maximumLabel);
    }

    private static URI parseUri(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record ParsedByteSize(int bytes, String error) {
    }
}
