package dev.becerritoo.minemories.fabric;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class PersonalWebhookClient {
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "discord.com", "ptb.discord.com", "canary.discord.com", "discordapp.com"
    );
    private static final Pattern WEBHOOK_PATH = Pattern.compile(
            "^/api(?:/v[0-9]+)?/webhooks/[0-9]+/[^/]+/?$"
    );
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private PersonalWebhookClient() {
    }

    public static CompletableFuture<Result> test(String webhookUrl) {
        URI uri;
        try {
            uri = parseWebhook(webhookUrl);
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(new Result(false, exception.getMessage()));
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "Minemories/1.0")
                .GET()
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .handle((response, error) -> {
                    if (error != null) {
                        return new Result(false, "No fue posible conectar con Discord.");
                    }
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return new Result(true, "Webhook válido.");
                    }
                    return new Result(false, response.statusCode() == 404
                            ? "Discord no encontró ese webhook."
                            : "Discord respondió HTTP " + response.statusCode() + ".");
                });
    }

    public static CompletableFuture<Result> send(ClientConfig.PersonalWebhook destination,
                                                  ImageEncoder.EncodedImage image,
                                                  UploadContext context) {
        URI uri;
        try {
            uri = withWait(parseWebhook(destination.webhookUrl));
        } catch (IllegalArgumentException exception) {
            return CompletableFuture.completedFuture(new Result(false, exception.getMessage()));
        }

        String filename = "minemories-" + safeFilename(context.playerName()) + "-"
                + Instant.now().getEpochSecond() + ".jpg";
        String boundary = "Minemories-" + UUID.randomUUID();
        byte[] body;
        try {
            body = multipart(boundary, payloadJson(context, filename), filename, image.bytes());
        } catch (IOException exception) {
            return CompletableFuture.completedFuture(new Result(false, "No se pudo preparar el mensaje."));
        }
        return sendOnce(uri, boundary, body).thenCompose(response -> {
            if (response.statusCode() != 429) {
                return CompletableFuture.completedFuture(result(response.statusCode()));
            }
            long retryMillis = retryMillis(response);
            return CompletableFuture.supplyAsync(() -> null,
                            CompletableFuture.delayedExecutor(retryMillis, TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> sendOnce(uri, boundary, body))
                    .thenApply(retried -> result(retried.statusCode()));
        }).exceptionally(error -> new Result(false, "No fue posible conectar con Discord."));
    }

    static URI parseWebhook(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("El webhook está vacío.");
        }
        URI uri;
        try {
            uri = URI.create(value.strip());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("El webhook no es una URL válida.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOSTS.contains(host)
                || (uri.getPort() != -1 && uri.getPort() != 443)
                || uri.getUserInfo() != null || uri.getFragment() != null
                || !WEBHOOK_PATH.matcher(uri.getPath()).matches()) {
            throw new IllegalArgumentException("Usa un webhook oficial de Discord.");
        }
        return uri;
    }

    private static CompletableFuture<HttpResponse<Void>> sendOnce(URI uri, String boundary, byte[] body) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "Minemories/1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }

    private static Result result(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return new Result(true, "");
        }
        if (statusCode == 404) {
            return new Result(false, "Discord no encontró el webhook.");
        }
        if (statusCode == 429) {
            return new Result(false, "Discord continúa limitando ese webhook.");
        }
        return new Result(false, "Discord respondió HTTP " + statusCode + ".");
    }

    private static long retryMillis(HttpResponse<?> response) {
        String value = response.headers().firstValue("Retry-After").orElse("1");
        try {
            double seconds = Double.parseDouble(value);
            return Math.max(250L, Math.min(10_000L, Math.round(seconds * 1000.0)));
        } catch (NumberFormatException ignored) {
            return 1000L;
        }
    }

    private static byte[] multipart(String boundary, String json, String filename, byte[] image)
            throws IOException {
        byte[] lineBreak = "\r\n".getBytes(StandardCharsets.US_ASCII);
        try (var output = new ByteArrayOutputStream(json.length() + image.length + 1024)) {
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write("Content-Disposition: form-data; name=\"payload_json\"\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            output.write("Content-Type: application/json; charset=utf-8\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.write(lineBreak);
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(("Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + filename
                    + "\"\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write("Content-Type: image/jpeg\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.write(image);
            output.write(lineBreak);
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        }
    }

    private static String payloadJson(UploadContext context, String filename) {
        StringBuilder fields = new StringBuilder();
        fields.append("{\"name\":\"Servidor\",\"value\":\"")
                .append(json(context.serverName())).append("\",\"inline\":true}");
        if (context.includeCoordinates()) {
            fields.append(",{")
                    .append("\"name\":\"Ubicación\",\"value\":\"")
                    .append(json(context.world() + " · " + context.x() + ", " + context.y() + ", " + context.z()))
                    .append("\",\"inline\":true}");
        }
        String description = context.caption() == null || context.caption().isBlank()
                ? "📸 Un nuevo recuerdo" : limitCodePoints(context.caption().strip(), 200);
        return new StringBuilder("{")
                .append("\"username\":\"Minemories\",")
                .append("\"allowed_mentions\":{\"parse\":[]},")
                .append("\"embeds\":[{")
                .append("\"color\":5793266,")
                .append("\"author\":{\"name\":\"").append(json(context.playerName())).append("\"},")
                .append("\"description\":\"").append(json(description)).append("\",")
                .append("\"fields\":[").append(fields).append("],")
                .append("\"image\":{\"url\":\"attachment://").append(json(filename)).append("\"},")
                .append("\"footer\":{\"text\":\"Minemories • destino personal\"},")
                .append("\"timestamp\":\"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\"")
                .append("}]}").toString();
    }

    private static String json(String value) {
        StringBuilder escaped = new StringBuilder(value == null ? 0 : value.length() + 16);
        if (value == null) {
            return "";
        }
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (codePoint < 0x20) {
                        escaped.append(String.format("\\u%04x", codePoint));
                    } else {
                        escaped.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return escaped.toString();
    }

    private static URI withWait(URI original) {
        String separator = original.getQuery() == null ? "?" : "&";
        return URI.create(original + separator + "wait=true");
    }

    private static String safeFilename(String name) {
        String safe = name == null ? "" : name.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "player" : safe;
    }

    private static String limitCodePoints(String value, int maximum) {
        int count = value.codePointCount(0, value.length());
        return count <= maximum ? value : value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    public record UploadContext(String playerName, String serverName, String caption,
                                boolean includeCoordinates, String world, int x, int y, int z) {
    }

    public record Result(boolean success, String reason) {
    }
}
