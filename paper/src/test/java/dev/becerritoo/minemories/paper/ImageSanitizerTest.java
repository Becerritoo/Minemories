package dev.becerritoo.minemories.paper;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSanitizerTest {
    @Test
    void reencodesAndDropsTrailingPayload() throws Exception {
        byte[] marker = "NOT_A_REAL_DOCUMENT_PAYLOAD".getBytes(StandardCharsets.UTF_8);
        byte[] png = png(32, 24);
        byte[] polyglot = java.util.Arrays.copyOf(png, png.length + marker.length);
        System.arraycopy(marker, 0, polyglot, png.length, marker.length);

        var result = ImageSanitizer.sanitize(polyglot, "image/png", settings(100, 100, 10_000));

        assertEquals("image/jpeg", result.contentType());
        assertEquals(32, result.width());
        assertEquals(24, result.height());
        assertTrue(indexOf(result.bytes(), marker) < 0);
    }

    @Test
    void rejectsDimensionsBeforeFullDecode() throws Exception {
        byte[] png = png(80, 80);
        assertThrows(IOException.class,
                () -> ImageSanitizer.sanitize(png, "image/png", settings(64, 64, 4096)));
    }

    @Test
    void rejectsMismatchedContentType() throws Exception {
        assertThrows(IOException.class,
                () -> ImageSanitizer.sanitize(png(16, 16), "image/jpeg", settings(100, 100, 10_000)));
    }

    @Test
    void adjustsJpegQualityToStayUnderTargetWithoutReducingResolution() throws Exception {
        BufferedImage noisy = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42L);
        for (int y = 0; y < noisy.getHeight(); y++) {
            for (int x = 0; x < noisy.getWidth(); x++) {
                noisy.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        byte[] source;
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(noisy, "png", output);
            source = output.toByteArray();
        }

        var result = ImageSanitizer.sanitize(source, "image/png", settings(640, 360, 230_400, 150_000));

        assertTrue(result.bytes().length <= 150_000);
        assertEquals(640, result.width());
        assertEquals(360, result.height());
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(30, 80, 140));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    private static Settings settings(int maxWidth, int maxHeight, long maxPixels) {
        return settings(maxWidth, maxHeight, maxPixels, 500_000);
    }

    private static Settings settings(int maxWidth, int maxHeight, long maxPixels, int targetBytes) {
        return new Settings("Test", 1_000_000, targetBytes,
                maxWidth, maxHeight, maxPixels, 0.95f, 200, 30, 30, 20, 2,
                true, true, 64, 3, 12, 8,
                "AUTO", true, 48, 4, 4, true, true, true,
                true, false, URI.create("http://localhost/webhook"), "Minemories", "", 1,
                "", false, false, "ok", "error", null);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
