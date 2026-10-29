package dev.becerritoo.minemories.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class AlbumArchive {
    private static final int FORMAT = 1;
    private static final int MAX_ROLLS = 8;
    private static final int MAX_PHOTOS = 96;
    private static final int MAX_IMAGE_BYTES = 64 * 1024;
    private static final int MAX_STRING_BYTES = 2 * 1024;

    private AlbumArchive() {
    }

    public static byte[] encode(Album album) {
        if (album.rolls().size() > MAX_ROLLS) {
            throw new IllegalArgumentException("Demasiados rollos");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(FORMAT);
                out.writeInt(album.rolls().size());
                int photoCount = 0;
                for (Roll roll : album.rolls()) {
                    photoCount += roll.photos().size();
                    if (photoCount > MAX_PHOTOS) {
                        throw new IllegalArgumentException("Demasiadas fotografías");
                    }
                    writeString(out, roll.id());
                    out.writeInt(roll.exposures());
                    out.writeInt(roll.capacity());
                    out.writeInt(roll.photos().size());
                    for (Photo photo : roll.photos()) {
                        writeString(out, photo.id());
                        out.writeInt(photo.slot());
                        out.writeLong(photo.createdAtEpochMillis());
                        writeString(out, photo.playerName());
                        writeString(out, photo.caption());
                        if (photo.jpeg().length < 1 || photo.jpeg().length > MAX_IMAGE_BYTES) {
                            throw new IllegalArgumentException("Miniatura fuera de límites");
                        }
                        out.writeInt(photo.jpeg().length);
                        out.write(photo.jpeg());
                    }
                }
            }
            return bytes.toByteArray();
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static Album decode(byte[] data) throws IOException {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readUnsignedByte() != FORMAT) {
                throw new IOException("Formato de álbum incompatible");
            }
            int rollCount = bounded(in.readInt(), 0, MAX_ROLLS, "rollos");
            List<Roll> rolls = new ArrayList<>(rollCount);
            int photoCount = 0;
            for (int r = 0; r < rollCount; r++) {
                String id = readString(in);
                int exposures = bounded(in.readInt(), 0, 10_000, "exposiciones");
                int capacity = bounded(in.readInt(), 1, 10_000, "capacidad");
                int count = bounded(in.readInt(), 0, MAX_PHOTOS, "fotografías");
                photoCount += count;
                if (photoCount > MAX_PHOTOS) {
                    throw new IOException("Demasiadas fotografías");
                }
                List<Photo> photos = new ArrayList<>(count);
                for (int p = 0; p < count; p++) {
                    String photoId = readString(in);
                    int slot = bounded(in.readInt(), 1, 10_000, "posición");
                    long created = in.readLong();
                    String player = readString(in);
                    String caption = readString(in);
                    int length = bounded(in.readInt(), 1, MAX_IMAGE_BYTES, "miniatura");
                    if (length > in.available()) {
                        throw new IOException("Miniatura truncada");
                    }
                    photos.add(new Photo(photoId, slot, created, player, caption, in.readNBytes(length)));
                }
                rolls.add(new Roll(id, exposures, capacity, List.copyOf(photos)));
            }
            if (in.available() != 0) {
                throw new IOException("Datos adicionales en el álbum");
            }
            return new Album(List.copyOf(rolls));
        } catch (IllegalArgumentException exception) {
            throw new IOException("Álbum inválido", exception);
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IllegalArgumentException("Texto de álbum demasiado largo");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = bounded(in.readInt(), 0, MAX_STRING_BYTES, "texto");
        if (length > in.available()) {
            throw new IOException("Texto truncado");
        }
        return new String(in.readNBytes(length), StandardCharsets.UTF_8);
    }

    private static int bounded(int value, int minimum, int maximum, String label) throws IOException {
        if (value < minimum || value > maximum) {
            throw new IOException("Cantidad de " + label + " fuera de límites");
        }
        return value;
    }

    public record Album(List<Roll> rolls) {
        public Album {
            rolls = List.copyOf(rolls);
        }
    }

    public record Roll(String id, int exposures, int capacity, List<Photo> photos) {
        public Roll {
            photos = List.copyOf(photos);
        }
    }

    public record Photo(String id, int slot, long createdAtEpochMillis, String playerName,
                        String caption, byte[] jpeg) {
        public Photo {
            jpeg = jpeg.clone();
        }

        @Override
        public byte[] jpeg() {
            return jpeg.clone();
        }
    }
}
