package dev.becerritoo.minemories.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public int configVersion = 4;
    public boolean cleanHud = true;
    public boolean includeCoordinates = false;
    public boolean saveLocalCopy = true;
    public int maxUploadWidth = 1920;
    public int maxUploadHeight = 1080;
    public float jpegQuality = 0.95f;
    public int maxQueuedPhotos = 5;
    public DestinationMode destinationMode = DestinationMode.AUTOMATIC;
    public int personalTargetFileBytes = 200 * 1024;
    public int personalCooldownSeconds = 3;
    public List<PersonalWebhook> personalWebhooks = new ArrayList<>();
    public InstaxSettings.Filter instaxFilter = InstaxSettings.Filter.LANCZOS;
    public double instaxSharpness = 0.50;
    public double instaxContrast = 1.025;
    public double instaxSaturation = 1.04;
    public boolean instaxDithering = true;

    static ClientConfig load() {
        Path path = path();
        if (Files.isRegularFile(path)) {
            try {
                ClientConfig loaded = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ClientConfig.class);
                if (loaded != null) {
                    if (loaded.configVersion < 2) {
                        loaded.maxUploadWidth = 1920;
                        loaded.maxUploadHeight = 1080;
                        loaded.jpegQuality = 0.95f;
                        loaded.maxQueuedPhotos = 5;
                    }
                    if (loaded.configVersion < 3) {
                        loaded.destinationMode = DestinationMode.AUTOMATIC;
                        loaded.personalTargetFileBytes = 200 * 1024;
                        loaded.personalCooldownSeconds = 3;
                        loaded.personalWebhooks = new ArrayList<>();
                    }
                    if (loaded.configVersion < 4) {
                        loaded.applyInstax(InstaxSettings.SHARP);
                    }
                    loaded.configVersion = 4;
                    loaded.normalize();
                    loaded.save();
                    return loaded;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        ClientConfig created = new ClientConfig();
        created.save();
        return created;
    }

    public synchronized void save() {
        normalize();
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Path path() {
        return MinemoriesClient.platform().configDirectory().resolve("minemories.json");
    }

    private void normalize() {
        configVersion = 4;
        maxUploadWidth = Math.max(640, Math.min(7680, maxUploadWidth));
        maxUploadHeight = Math.max(360, Math.min(4320, maxUploadHeight));
        jpegQuality = Math.max(0.5f, Math.min(0.98f, jpegQuality));
        maxQueuedPhotos = Math.max(1, Math.min(20, maxQueuedPhotos));
        personalTargetFileBytes = Math.max(64 * 1024, Math.min(500 * 1024, personalTargetFileBytes));
        personalCooldownSeconds = Math.max(0, Math.min(300, personalCooldownSeconds));
        applyInstax(instaxSettings());
        if (destinationMode == null) {
            destinationMode = DestinationMode.AUTOMATIC;
        }
        if (personalWebhooks == null) {
            personalWebhooks = new ArrayList<>();
        }
        if (personalWebhooks.size() > 10) {
            personalWebhooks = new ArrayList<>(personalWebhooks.subList(0, 10));
        }
        personalWebhooks.removeIf(webhook -> webhook == null);
        personalWebhooks.forEach(PersonalWebhook::normalize);
    }

    public boolean usesServer(boolean serverAvailable) {
        return serverAvailable && destinationMode != DestinationMode.PERSONAL_ONLY;
    }

    public boolean usesPersonal(boolean serverAvailable) {
        return switch (destinationMode) {
            case AUTOMATIC -> !serverAvailable;
            case SERVER_ONLY -> false;
            case PERSONAL_ONLY, SERVER_AND_PERSONAL -> true;
        };
    }

    public List<PersonalWebhook> enabledPersonalWebhooks() {
        if (personalWebhooks == null) {
            return List.of();
        }
        return personalWebhooks.stream()
                .filter(webhook -> webhook != null && webhook.enabled && !webhook.webhookUrl.isBlank())
                .map(PersonalWebhook::copy)
                .toList();
    }

    public InstaxSettings instaxSettings() {
        return new InstaxSettings(instaxFilter, instaxSharpness, instaxContrast,
                instaxSaturation, instaxDithering);
    }

    public void applyInstax(InstaxSettings settings) {
        InstaxSettings normalized = settings == null ? InstaxSettings.SHARP : settings;
        instaxFilter = normalized.filter();
        instaxSharpness = normalized.sharpness();
        instaxContrast = normalized.contrast();
        instaxSaturation = normalized.saturation();
        instaxDithering = normalized.dithering();
    }

    public InstaxSettings.Preset instaxPreset() {
        InstaxSettings settings = instaxSettings();
        if (same(settings, InstaxSettings.SOFT)) {
            return InstaxSettings.Preset.SOFT;
        }
        if (same(settings, InstaxSettings.BALANCED)) {
            return InstaxSettings.Preset.BALANCED;
        }
        if (same(settings, InstaxSettings.SHARP)) {
            return InstaxSettings.Preset.SHARP;
        }
        return InstaxSettings.Preset.CUSTOM;
    }

    private static boolean same(InstaxSettings left, InstaxSettings right) {
        return left.filter() == right.filter()
                && Math.abs(left.sharpness() - right.sharpness()) < 0.0001
                && Math.abs(left.contrast() - right.contrast()) < 0.0001
                && Math.abs(left.saturation() - right.saturation()) < 0.0001
                && left.dithering() == right.dithering();
    }

    public enum DestinationMode {
        AUTOMATIC("Automático"),
        SERVER_ONLY("Solo servidor"),
        PERSONAL_ONLY("Solo personal"),
        SERVER_AND_PERSONAL("Servidor y personales");

        private final String label;

        DestinationMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public DestinationMode next() {
            DestinationMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public static final class PersonalWebhook {
        public String id = UUID.randomUUID().toString();
        public String name = "Mi Discord";
        public String webhookUrl = "";
        public boolean enabled = true;

        public PersonalWebhook() {
        }

        public PersonalWebhook(String id, String name, String webhookUrl, boolean enabled) {
            this.id = id;
            this.name = name;
            this.webhookUrl = webhookUrl;
            this.enabled = enabled;
            normalize();
        }

        private void normalize() {
            if (id == null || id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            name = name == null || name.isBlank() ? "Mi Discord" : limit(name.strip(), 48);
            webhookUrl = webhookUrl == null ? "" : limit(webhookUrl.strip(), 512);
        }

        public PersonalWebhook copy() {
            return new PersonalWebhook(id, name, webhookUrl, enabled);
        }

        private static String limit(String value, int maximum) {
            return value.length() <= maximum ? value : value.substring(0, maximum);
        }
    }
}
