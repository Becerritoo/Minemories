package dev.becerritoo.minemories.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolTest {
    @Test
    void requestRoundTrip() throws Exception {
        assertEquals(42L, Protocol.decodeRequest(Protocol.encodeRequest(42L)).requestId());
        var camera = Protocol.decodeRequest(Protocol.encodeCameraRequest(43L, "capture", false));
        assertEquals("capture", camera.captureToken());
        assertFalse(camera.publishToDiscord());
    }

    @Test
    void acceptedResponseRoundTrip() throws Exception {
        var input = new Protocol.TicketResponse(7L, true, "ok", "secret", 123L,
                1_048_576, 512_000, 1920, 1080, 0.95f, 30,
                200, true, "Mi servidor");
        var output = Protocol.decodeResponse(Protocol.encodeResponse(input));
        assertTrue(output.accepted());
        assertEquals(input, output);
    }

    @Test
    void deniedResponseRoundTrip() throws Exception {
        var output = Protocol.decodeResponse(Protocol.encodeResponse(Protocol.TicketResponse.denied(8L, "No autorizado")));
        assertFalse(output.accepted());
        assertEquals("No autorizado", output.message());
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] valid = Protocol.encodeRequest(1L);
        byte[] invalid = java.util.Arrays.copyOf(valid, valid.length + 1);
        assertThrows(Protocol.ProtocolException.class, () -> Protocol.decodeRequest(invalid));
    }

    @Test
    void uploadBeginChunkAndResultRoundTrip() throws Exception {
        byte[] instax = new byte[MapPaletteCodec.PIXEL_COUNT];
        java.util.Arrays.fill(instax, (byte) 4);
        var begin = new Protocol.UploadBegin(9L, "token", 30_000, 4, "Un recuerdo", true, instax,
                MapPaletteCodec.MURAL_PIXEL_COUNT);
        var decodedBegin = Protocol.decodeUploadBegin(Protocol.encodeUploadBegin(begin));
        assertEquals(begin.requestId(), decodedBegin.requestId());
        assertEquals(begin.token(), decodedBegin.token());
        assertEquals(begin.totalBytes(), decodedBegin.totalBytes());
        assertEquals(begin.chunkCount(), decodedBegin.chunkCount());
        assertEquals(begin.caption(), decodedBegin.caption());
        assertEquals(begin.includeCoordinates(), decodedBegin.includeCoordinates());
        assertEquals(MapPaletteCodec.MURAL_PIXEL_COUNT, decodedBegin.muralBytes());
        org.junit.jupiter.api.Assertions.assertArrayEquals(instax, decodedBegin.instaxPixels());

        byte[] data = new byte[Protocol.CHUNK_BYTES];
        java.util.Arrays.fill(data, (byte) 42);
        var chunk = Protocol.decodeUploadChunk(Protocol.encodeUploadChunk(9L, 0, data));
        assertEquals(9L, chunk.requestId());
        assertEquals(0, chunk.index());
        org.junit.jupiter.api.Assertions.assertArrayEquals(data, chunk.data());

        var result = new Protocol.UploadResult(9L, true, "Publicado");
        assertEquals(result, Protocol.decodeUploadResult(Protocol.encodeUploadResult(result)));

        var trigger = new Protocol.CameraTrigger("capture", 63, 12);
        assertEquals(trigger, Protocol.decodeCameraTrigger(Protocol.encodeCameraTrigger(trigger)));

        byte[] shutter = Protocol.encodeCameraShutter();
        assertEquals(Protocol.CAMERA_SHUTTER, Protocol.opcode(shutter));
        Protocol.decodeCameraShutter(shutter);
        assertThrows(Protocol.ProtocolException.class,
                () -> Protocol.decodeCameraShutter(new byte[]{Protocol.VERSION, Protocol.CAMERA_SHUTTER, 1}));

        var album = new Protocol.AlbumBegin(11L, 30_000, 2);
        assertEquals(album, Protocol.decodeAlbumBegin(Protocol.encodeAlbumBegin(album)));
        var albumChunk = Protocol.decodeAlbumChunk(Protocol.encodeAlbumChunk(11L, 0, data));
        assertEquals(11L, albumChunk.transferId());
        org.junit.jupiter.api.Assertions.assertArrayEquals(data, albumChunk.data());
    }

    @Test
    void rejectsIntermediateInstaxSizes() {
        assertThrows(IllegalArgumentException.class, () -> new Protocol.UploadBegin(
                9L, "token", 30_000, 2, "", false, new byte[MapPaletteCodec.PIXEL_COUNT * 2], 0));
        assertThrows(IllegalArgumentException.class, () -> new Protocol.UploadBegin(
                9L, "token", 30_000, 2, "", false, new byte[0], 123));
    }
}
