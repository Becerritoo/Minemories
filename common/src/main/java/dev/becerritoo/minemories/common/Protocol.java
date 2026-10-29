package dev.becerritoo.minemories.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Protocol {
    public static final String CHANNEL = "minemories:control";
    public static final int VERSION = 5;
    public static final int REQUEST_TICKET = 1;
    public static final int TICKET_RESPONSE = 2;
    public static final int UPLOAD_BEGIN = 3;
    public static final int UPLOAD_CHUNK = 4;
    public static final int UPLOAD_RESULT = 5;
    public static final int CAMERA_TRIGGER = 6;
    public static final int ALBUM_BEGIN = 7;
    public static final int ALBUM_CHUNK = 8;
    public static final int CAMERA_SHUTTER = 9;
    public static final int MAX_PACKET_BYTES = 32_768;
    public static final int CHUNK_BYTES = 24 * 1024;
    private static final int MAX_STRING_BYTES = 16_384;

    private Protocol() {
    }

    public static byte[] encodeRequest(long requestId) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(REQUEST_TICKET);
                out.writeLong(requestId);
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static byte[] encodeCameraRequest(long requestId, String captureToken, boolean publishToDiscord) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(REQUEST_TICKET);
                out.writeLong(requestId);
                writeString(out, captureToken);
                out.writeBoolean(publishToDiscord);
            }
            return requirePacketSize(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static TicketRequest decodeRequest(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, REQUEST_TICKET);
            long requestId = in.readLong();
            if (in.available() == 0) {
                return new TicketRequest(requestId, "", true);
            }
            String captureToken = readString(in);
            boolean publishToDiscord = in.readBoolean();
            requireFullyRead(in);
            return new TicketRequest(requestId, captureToken, publishToDiscord);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Solicitud inválida", exception);
        }
    }

    public static byte[] encodeResponse(TicketResponse response) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(TICKET_RESPONSE);
                out.writeLong(response.requestId());
                out.writeBoolean(response.accepted());
                writeString(out, response.message());
                if (response.accepted()) {
                    writeString(out, response.token());
                    out.writeLong(response.expiresAtEpochMillis());
                    out.writeInt(response.maxBytes());
                    out.writeInt(response.targetBytes());
                    out.writeInt(response.maxWidth());
                    out.writeInt(response.maxHeight());
                    out.writeFloat(response.jpegQuality());
                    out.writeInt(response.cooldownSeconds());
                    out.writeInt(response.maxCaptionCharacters());
                    out.writeBoolean(response.coordinatesAllowed());
                    writeString(out, response.serverName());
                }
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo codificar la respuesta", exception);
        }
    }

    public static TicketResponse decodeResponse(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, TICKET_RESPONSE);
            long requestId = in.readLong();
            boolean accepted = in.readBoolean();
            String message = readString(in);
            TicketResponse response;
            if (accepted) {
                response = new TicketResponse(
                        requestId,
                        true,
                        message,
                        readString(in),
                        in.readLong(),
                        in.readInt(),
                        in.readInt(),
                        in.readInt(),
                        in.readInt(),
                        in.readFloat(),
                        in.readInt(),
                        in.readInt(),
                        in.readBoolean(),
                        readString(in)
                );
            } else {
                response = TicketResponse.denied(requestId, message);
            }
            requireFullyRead(in);
            return response;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Respuesta inválida", exception);
        }
    }

    public static byte[] encodeUploadBegin(UploadBegin begin) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(UPLOAD_BEGIN);
                out.writeLong(begin.requestId());
                writeString(out, begin.token());
                out.writeInt(begin.totalBytes());
                out.writeInt(begin.chunkCount());
                writeString(out, begin.caption());
                out.writeBoolean(begin.includeCoordinates());
                out.writeInt(begin.instaxPixels().length);
                out.write(begin.instaxPixels());
                out.writeInt(begin.muralBytes());
            }
            return requirePacketSize(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static UploadBegin decodeUploadBegin(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, UPLOAD_BEGIN);
            long requestId = in.readLong();
            String token = readString(in);
            int totalBytes = in.readInt();
            int chunkCount = in.readInt();
            String caption = readString(in);
            boolean includeCoordinates = in.readBoolean();
            int instaxLength = in.readInt();
            if (instaxLength != 0 && instaxLength != MapPaletteCodec.PIXEL_COUNT) {
                throw new IllegalArgumentException("Tamaño de Instax inválido");
            }
            byte[] instaxPixels = in.readNBytes(instaxLength);
            if (instaxPixels.length != instaxLength) {
                throw new IllegalArgumentException("Instax incompleta");
            }
            int muralBytes = in.readInt();
            if (muralBytes != 0 && muralBytes != MapPaletteCodec.MURAL_PIXEL_COUNT) {
                throw new IllegalArgumentException("Tamaño de ampliación inválido");
            }
            UploadBegin begin = new UploadBegin(requestId, token, totalBytes, chunkCount, caption,
                    includeCoordinates, instaxPixels, muralBytes);
            requireFullyRead(in);
            return begin;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Inicio de subida inválido", exception);
        }
    }

    public static byte[] encodeUploadChunk(long requestId, int index, byte[] data) {
        if (data == null || data.length < 1 || data.length > CHUNK_BYTES) {
            throw new IllegalArgumentException("Tamaño de fragmento inválido");
        }
        try {
            var bytes = new ByteArrayOutputStream(data.length + 18);
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(UPLOAD_CHUNK);
                out.writeLong(requestId);
                out.writeInt(index);
                out.writeInt(data.length);
                out.write(data);
            }
            return requirePacketSize(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static UploadChunk decodeUploadChunk(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, UPLOAD_CHUNK);
            long requestId = in.readLong();
            int index = in.readInt();
            int length = in.readInt();
            if (index < 0 || length < 1 || length > CHUNK_BYTES || length > in.available()) {
                throw new IllegalArgumentException("Fragmento fuera de límites");
            }
            byte[] data = in.readNBytes(length);
            requireFullyRead(in);
            return new UploadChunk(requestId, index, data);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Fragmento inválido", exception);
        }
    }

    public static byte[] encodeUploadResult(UploadResult result) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(UPLOAD_RESULT);
                out.writeLong(result.requestId());
                out.writeBoolean(result.success());
                writeString(out, result.message());
            }
            return requirePacketSize(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static UploadResult decodeUploadResult(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, UPLOAD_RESULT);
            UploadResult result = new UploadResult(in.readLong(), in.readBoolean(), readString(in));
            requireFullyRead(in);
            return result;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Resultado de subida inválido", exception);
        }
    }

    public static byte[] encodeCameraTrigger(CameraTrigger trigger) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(CAMERA_TRIGGER);
                writeString(out, trigger.captureToken());
                out.writeInt(trigger.remainingDurability());
                out.writeInt(trigger.remainingExposures());
            }
            return requirePacketSize(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static CameraTrigger decodeCameraTrigger(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, CAMERA_TRIGGER);
            CameraTrigger trigger = new CameraTrigger(readString(in), in.readInt(), in.readInt());
            requireFullyRead(in);
            return trigger;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Activación de cámara inválida", exception);
        }
    }

    public static byte[] encodeCameraShutter() {
        return new byte[]{VERSION, CAMERA_SHUTTER};
    }

    public static void decodeCameraShutter(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, CAMERA_SHUTTER);
            requireFullyRead(in);
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Disparo de cámara inválido", exception);
        }
    }

    public static byte[] encodeAlbumBegin(AlbumBegin begin) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(ALBUM_BEGIN);
                out.writeLong(begin.transferId());
                out.writeInt(begin.totalBytes());
                out.writeInt(begin.chunkCount());
            }
            return requirePacketSize(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static AlbumBegin decodeAlbumBegin(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, ALBUM_BEGIN);
            AlbumBegin begin = new AlbumBegin(in.readLong(), in.readInt(), in.readInt());
            if (begin.totalBytes() < 1 || begin.totalBytes() > 2 * 1024 * 1024
                    || begin.chunkCount() < 1
                    || begin.chunkCount() != (begin.totalBytes() + CHUNK_BYTES - 1) / CHUNK_BYTES) {
                throw new IllegalArgumentException("Tamaño de álbum inválido");
            }
            requireFullyRead(in);
            return begin;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Inicio de álbum inválido", exception);
        }
    }

    public static byte[] encodeAlbumChunk(long transferId, int index, byte[] data) {
        if (data == null || data.length < 1 || data.length > CHUNK_BYTES) {
            throw new IllegalArgumentException("Tamaño de fragmento inválido");
        }
        try {
            var bytes = new ByteArrayOutputStream(data.length + 18);
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(VERSION);
                out.writeByte(ALBUM_CHUNK);
                out.writeLong(transferId);
                out.writeInt(index);
                out.writeInt(data.length);
                out.write(data);
            }
            return requirePacketSize(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static AlbumChunk decodeAlbumChunk(byte[] payload) throws ProtocolException {
        try (var in = new DataInputStream(new ByteArrayInputStream(payload))) {
            requireHeader(in, ALBUM_CHUNK);
            long transferId = in.readLong();
            int index = in.readInt();
            int length = in.readInt();
            if (index < 0 || length < 1 || length > CHUNK_BYTES || length > in.available()) {
                throw new IllegalArgumentException("Fragmento de álbum fuera de límites");
            }
            AlbumChunk chunk = new AlbumChunk(transferId, index, in.readNBytes(length));
            requireFullyRead(in);
            return chunk;
        } catch (IOException | IllegalArgumentException exception) {
            throw new ProtocolException("Fragmento de álbum inválido", exception);
        }
    }

    public static int opcode(byte[] payload) throws ProtocolException {
        if (payload == null || payload.length < 2 || payload.length > MAX_PACKET_BYTES
                || Byte.toUnsignedInt(payload[0]) != VERSION) {
            throw new ProtocolException("Paquete incompatible", null);
        }
        return Byte.toUnsignedInt(payload[1]);
    }

    private static void requireHeader(DataInputStream in, int expectedOpcode) throws IOException {
        int version = in.readUnsignedByte();
        int opcode = in.readUnsignedByte();
        if (version != VERSION || opcode != expectedOpcode) {
            throw new IllegalArgumentException("Versión u operación incompatible");
        }
    }

    private static void requireFullyRead(DataInputStream in) throws IOException {
        if (in.available() != 0) {
            throw new IllegalArgumentException("Datos adicionales no permitidos");
        }
    }

    private static byte[] requirePacketSize(byte[] payload) {
        if (payload.length > MAX_PACKET_BYTES) {
            throw new IllegalArgumentException("Paquete Minemories demasiado grande");
        }
        return payload;
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Cadena demasiado larga");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_STRING_BYTES || length > in.available()) {
            throw new IllegalArgumentException("Longitud de cadena inválida");
        }
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    public record TicketRequest(long requestId, String captureToken, boolean publishToDiscord) {
        public TicketRequest(long requestId) {
            this(requestId, "", true);
        }

        public boolean cameraCapture() {
            return captureToken != null && !captureToken.isBlank();
        }
    }

    public record TicketResponse(
            long requestId,
            boolean accepted,
            String message,
            String token,
            long expiresAtEpochMillis,
            int maxBytes,
            int targetBytes,
            int maxWidth,
            int maxHeight,
            float jpegQuality,
            int cooldownSeconds,
            int maxCaptionCharacters,
            boolean coordinatesAllowed,
            String serverName
    ) {
        public static TicketResponse denied(long requestId, String message) {
            return new TicketResponse(requestId, false, message, "", 0L,
                    0, 0, 0, 0, 0.0f, 0, 0, false, "");
        }
    }

    public record UploadBegin(long requestId, String token, int totalBytes, int chunkCount,
                              String caption, boolean includeCoordinates, byte[] instaxPixels,
                              int muralBytes) {
        public UploadBegin {
            instaxPixels = instaxPixels == null ? new byte[0] : instaxPixels.clone();
            if (instaxPixels.length != 0 && instaxPixels.length != MapPaletteCodec.PIXEL_COUNT) {
                throw new IllegalArgumentException("Tamaño de Instax inválido");
            }
            if (muralBytes != 0 && muralBytes != MapPaletteCodec.MURAL_PIXEL_COUNT) {
                throw new IllegalArgumentException("Tamaño de ampliación inválido");
            }
        }

        @Override
        public byte[] instaxPixels() {
            return instaxPixels.clone();
        }
    }

    public record UploadChunk(long requestId, int index, byte[] data) {
        public UploadChunk {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }

    public record UploadResult(long requestId, boolean success, String message) {
    }

    public record CameraTrigger(String captureToken, int remainingDurability, int remainingExposures) {
    }

    public record AlbumBegin(long transferId, int totalBytes, int chunkCount) {
    }

    public record AlbumChunk(long transferId, int index, byte[] data) {
        public AlbumChunk {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }

    public static final class ProtocolException extends Exception {
        public ProtocolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
