package dev.becerritoo.minemories.client.net;

import dev.becerritoo.minemories.common.AlbumArchive;
import dev.becerritoo.minemories.common.Protocol;
import dev.becerritoo.minemories.client.AlbumScreen;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class AlbumReceiver {
    private static Transfer transfer;

    private AlbumReceiver() {
    }

    static void receive(byte[] payload) {
        try {
            int opcode = Protocol.opcode(payload);
            if (opcode == Protocol.ALBUM_BEGIN) {
                Protocol.AlbumBegin begin = Protocol.decodeAlbumBegin(payload);
                transfer = new Transfer(begin.transferId(), new byte[begin.totalBytes()], begin.chunkCount());
                return;
            }
            if (opcode != Protocol.ALBUM_CHUNK || transfer == null) {
                return;
            }
            Protocol.AlbumChunk chunk = Protocol.decodeAlbumChunk(payload);
            if (chunk.transferId() != transfer.id || chunk.index() != transfer.nextChunk) {
                transfer = null;
                notifyFailure("Los datos del álbum llegaron fuera de orden.");
                return;
            }
            int offset = chunk.index() * Protocol.CHUNK_BYTES;
            if (offset + chunk.data().length > transfer.data.length) {
                transfer = null;
                notifyFailure("El álbum recibido no es válido.");
                return;
            }
            System.arraycopy(chunk.data(), 0, transfer.data, offset, chunk.data().length);
            transfer.nextChunk++;
            transfer.received += chunk.data().length;
            if (transfer.nextChunk == transfer.chunkCount && transfer.received == transfer.data.length) {
                AlbumArchive.Album album = AlbumArchive.decode(transfer.data);
                transfer = null;
                Minecraft client = Minecraft.getInstance();
                client.setScreen(new AlbumScreen(album));
            }
        } catch (Protocol.ProtocolException | IOException | RuntimeException exception) {
            transfer = null;
            notifyFailure("No se pudo abrir el álbum de la cámara.");
        }
    }

    private static void notifyFailure(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("§c" + message), false);
        }
    }

    private static final class Transfer {
        private final long id;
        private final byte[] data;
        private final int chunkCount;
        private int nextChunk;
        private int received;

        private Transfer(long id, byte[] data, int chunkCount) {
            this.id = id;
            this.data = data;
            this.chunkCount = chunkCount;
        }
    }
}
