package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.common.MapPaletteCodec;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstaxEncoderTest {
    @Test
    void createsACompleteMapOnTheClient() {
        BufferedImage source = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
        var graphics = source.createGraphics();
        graphics.setPaint(new java.awt.GradientPaint(0, 0, Color.BLUE, 1920, 1080, Color.GREEN));
        graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
        graphics.dispose();

        byte[] pixels = InstaxEncoder.create(source);

        assertEquals(MapPaletteCodec.PIXEL_COUNT, pixels.length);
        assertTrue(MapPaletteCodec.valid(pixels));
    }

    @Test
    void presetsAndManualOptionsProduceValidDistinctMaps() {
        BufferedImage source = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int checker = ((x / 12) + (y / 12)) & 1;
                source.setRGB(x, y, checker == 0 ? 0xff2e5f91 : 0xffd6a44a);
            }
        }

        byte[] soft = InstaxEncoder.create(source, InstaxSettings.SOFT);
        byte[] balanced = InstaxEncoder.create(source, InstaxSettings.BALANCED);
        byte[] sharpWithoutDithering = InstaxEncoder.create(source,
                new InstaxSettings(InstaxSettings.Filter.LANCZOS, 0.50, 1.025, 1.04, false));

        assertTrue(MapPaletteCodec.valid(soft));
        assertTrue(MapPaletteCodec.valid(balanced));
        assertTrue(MapPaletteCodec.valid(sharpWithoutDithering));
        assertTrue(!Arrays.equals(soft, balanced));
        assertTrue(!Arrays.equals(balanced, sharpWithoutDithering));
    }

    @Test
    void createsAHighResolutionMuralBeforeItIsSplitIntoMaps() {
        BufferedImage source = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, ((x * 255 / source.getWidth()) << 16)
                        | (y * 255 / source.getHeight()));
            }
        }

        byte[] pixels = InstaxEncoder.createMural(source, InstaxSettings.SHARP);

        assertEquals(MapPaletteCodec.MURAL_PIXEL_COUNT, pixels.length);
        assertTrue(MapPaletteCodec.validMural(pixels));
    }
}
