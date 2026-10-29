package dev.becerritoo.minemories.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapPaletteCodecTest {
    @Test
    void producesACompleteValidMapPalette() {
        int[] pixels = new int[MapPaletteCodec.PIXEL_COUNT];
        Arrays.fill(pixels, 0xff6da34d);

        byte[] result = MapPaletteCodec.quantizeDithered(pixels, 128, 128);

        assertEquals(MapPaletteCodec.PIXEL_COUNT, result.length);
        assertTrue(MapPaletteCodec.valid(result));
        assertFalse(Arrays.equals(new byte[result.length], result));
    }

    @Test
    void rejectsTransparentAndOutOfRangeIndexes() {
        byte[] pixels = new byte[MapPaletteCodec.PIXEL_COUNT];
        assertFalse(MapPaletteCodec.valid(pixels));
        Arrays.fill(pixels, (byte) 4);
        assertTrue(MapPaletteCodec.valid(pixels));
        pixels[0] = (byte) 248;
        assertFalse(MapPaletteCodec.valid(pixels));
    }

    @Test
    void supportsAValidUnditheredPalette() {
        int[] pixels = new int[MapPaletteCodec.PIXEL_COUNT];
        Arrays.fill(pixels, 0xff4c7f99);

        byte[] result = MapPaletteCodec.quantize(pixels, 128, 128, false);

        assertTrue(MapPaletteCodec.valid(result));
    }

}
