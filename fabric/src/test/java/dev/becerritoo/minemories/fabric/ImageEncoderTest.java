package dev.becerritoo.minemories.fabric;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageEncoderTest {
    @Test
    void constrainsAHighEntropyCapture() throws Exception {
        int width = 1280;
        int height = 720;
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                source.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(source, "png", output);
            png = output.toByteArray();
        }

        ImageEncoder.EncodedImage encoded = ImageEncoder.prepare(
                new PhotoData(png, width, height, 0L),
                new ImageEncoder.Limits(500 * 1024, 200 * 1024, 1920, 1080, 0.95f));

        assertTrue(encoded.bytes().length <= 500 * 1024);
        assertTrue(encoded.width() <= 1920);
        assertTrue(encoded.height() <= 1080);
        assertEquals((double) width / height, (double) encoded.width() / encoded.height(), 0.01);
    }

    @Test
    void reusesTheExactInstaxPixelsShownInThePreview() throws Exception {
        BufferedImage source = new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB);
        byte[] png;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(source, "png", output);
            png = output.toByteArray();
        }
        byte[] previewPixels = new byte[128 * 128];
        Arrays.fill(previewPixels, (byte) 4);
        previewPixels[1234] = 7;

        ImageEncoder.EncodedImage encoded = ImageEncoder.prepare(
                new PhotoData(png, 320, 180, 0L, "camera-token"),
                new ImageEncoder.Limits(500 * 1024, 200 * 1024, 1920, 1080, 0.95f),
                InstaxSettings.SHARP, previewPixels);

        assertArrayEquals(previewPixels, encoded.instaxPixels());
    }
}
