package dev.becerritoo.minemories.paper;

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
import java.util.UUID;

final class DiscordWebhook implements DiscordDelivery {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public Result send(Settings settings, TicketService.PhotoTicket ticket,
                       ImageSanitizer.SanitizedImage image, String caption,
                       boolean includeCoordinates) {
        String filename = "minemories-" + safeFilename(ticket.playerName()) + "-" + Instant.now().getEpochSecond()
                + "." + image.extension();
        String boundary = "Minemories-" + UUID.randomUUID();
        byte[] body;
        try {
            body = multipart(boundary, payloadJson(settings, ticket, filename, caption, includeCoordinates),
                    filename, image.contentType(), image.bytes());
        } catch (IOException exception) {
            return new Result(false, "no se pudo preparar el mensaje");
        }

        URI target = withWait(settings.webhookUri());
        HttpRequest request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "Minemories/1.0 (+https://github.com/Becerritoo/Minemories)")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new Result(true, "");
            }
            return new Result(false, "Discord respondió HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new Result(false, "el envío fue interrumpido");
        } catch (IOException exception) {
            return new Result(false, "no fue posible conectar con Discord");
        }
    }

    @Override
    public String description() {
        return "webhook";
    }

    private static byte[] multipart(String boundary, String json, String filename, String contentType, byte[] image)
            throws IOException {
        byte[] lineBreak = "\r\n".getBytes(StandardCharsets.US_ASCII);
        try (var output = new ByteArrayOutputStream(json.length() + image.length + 1024)) {
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes(StandardCharsets.US_ASCII));
            output.write("Content-Type: application/json; charset=utf-8\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.write(json.getBytes(StandardCharsets.UTF_8));
            output.write(lineBreak);
            output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(("Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + filename
                    + "\"\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(image);
            output.write(lineBreak);
            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return output.toByteArray();
        }
    }

    private static String payloadJson(Settings settings, TicketService.PhotoTicket ticket, String filename,
                                      String caption, boolean includeCoordinates) {
        var fields = new StringBuilder();
        fields.append("{\"name\":\"Servidor\",\"value\":\"")
                .append(json(settings.serverName())).append("\",\"inline\":true}");
        if (includeCoordinates && settings.allowCoordinates()) {
            TicketService.LocationSnapshot location = ticket.location();
            fields.append(",{")
                    .append("\"name\":\"Ubicación\",\"value\":\"")
                    .append(json(location.world() + " · " + location.x() + ", " + location.y() + ", " + location.z()))
                    .append("\",\"inline\":true}");
        }
        if (ticket.discordId() != null && !ticket.discordId().isBlank()) {
            fields.append(",{")
                    .append("\"name\":\"Discord vinculado\",\"value\":\"Sí\",\"inline\":true}");
        }

        String description = caption == null || caption.isBlank() ? "📸 Un nuevo recuerdo" : caption;
        String footer = "Minemories";
        if (settings.showPlayerUuid()) {
            footer += " • " + ticket.playerId();
        }
        StringBuilder root = new StringBuilder("{");
        root.append("\"username\":\"").append(json(settings.webhookUsername())).append("\",");
        if (!settings.webhookAvatarUrl().isBlank()) {
            root.append("\"avatar_url\":\"").append(json(settings.webhookAvatarUrl())).append("\",");
        }
        root.append("\"allowed_mentions\":{\"parse\":[]},")
                .append("\"embeds\":[{")
                .append("\"color\":").append(settings.embedColor()).append(',')
                .append("\"author\":{\"name\":\"").append(json(ticket.playerName())).append("\"},")
                .append("\"description\":\"").append(json(description)).append("\",")
                .append("\"fields\": [").append(fields).append("],")
                .append("\"image\":{\"url\":\"attachment://").append(json(filename)).append("\"},")
                .append("\"footer\":{\"text\":\"").append(json(footer)).append("\"},")
                .append("\"timestamp\":\"").append(DateTimeFormatter.ISO_INSTANT.format(Instant.now())).append("\"")
                .append("}]} ");
        return root.toString();
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
        String safe = name.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe.isBlank() ? "player" : safe;
    }

}
