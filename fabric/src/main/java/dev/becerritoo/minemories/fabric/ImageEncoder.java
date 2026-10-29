package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.common.MapPaletteCodec;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ImageEncoder {
    private ImageEncoder() {
    }

    public static EncodedImage prepare(PhotoData photo, Limits limits) {
        return prepare(photo, limits, InstaxSettings.SHARP);
    }

    public static EncodedImage prepare(PhotoData photo, Limits limits, InstaxSettings instaxSettings) {
        return prepare(photo, limits, instaxSettings, null);
    }

    public static EncodedImage prepare(PhotoData photo, Limits limits, InstaxSettings instaxSettings,
                                       byte[] exactInstaxPixels) {
        if (limits.maximumBytes() <= 0 || limits.targetBytes() <= 0
                || limits.maximumWidth() <= 0 || limits.maximumHeight() <= 0) {
            throw new IllegalStateException("Los límites de imagen no son válidos.");
        }
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(photo.pngBytes()));
            if (source == null) {
                throw new IOException("No se pudo decodificar la captura.");
            }
            byte[] instaxPixels = photo.cameraCapture()
                    ? (MapPaletteCodec.valid(exactInstaxPixels)
                    ? exactInstaxPixels.clone() : InstaxEncoder.create(source, instaxSettings))
                    : new byte[0];
            byte[] muralPixels = photo.cameraCapture()
                    ? InstaxEncoder.createMural(source, instaxSettings) : new byte[0];
            BufferedImage current = resizeIfNeeded(source, limits.maximumWidth(), limits.maximumHeight());
            byte[] encoded = encodeForTarget(current, limits.maximumQuality(), limits.targetBytes());
            int minimumWidth = Math.min(640, limits.maximumWidth());
            int minimumHeight = Math.min(360, limits.maximumHeight());
            while (encoded == null && current.getWidth() > minimumWidth && current.getHeight() > minimumHeight) {
                int width = Math.max(minimumWidth, (int) (current.getWidth() * 0.88));
                int height = Math.max(minimumHeight, (int) (current.getHeight() * 0.88));
                current = resize(current, width, height);
                encoded = encodeForTarget(current, limits.maximumQuality(), limits.targetBytes());
            }
            if (encoded == null) {
                encoded = encodeJpeg(current, 0.55f);
            }
            if (encoded.length > limits.maximumBytes()) {
                throw new IOException("La fotografía supera el límite permitido.");
            }
            return new EncodedImage(encoded, current.getWidth(), current.getHeight(), instaxPixels, muralPixels);
        } catch (IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private static BufferedImage resizeIfNeeded(BufferedImage source, int maximumWidth, int maximumHeight) {
        double scale = Math.min(1.0, Math.min(
                maximumWidth / (double) source.getWidth(),
                maximumHeight / (double) source.getHeight()
        ));
        if (scale >= 1.0) {
            return toRgb(source);
        }
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return resize(source, width, height);
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setColor(java.awt.Color.BLACK);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        return resize(source, source.getWidth(), source.getHeight());
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var bytes = new ByteArrayOutputStream(); ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
            output.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static byte[] encodeForTarget(BufferedImage image, float maximumQuality, int targetBytes)
            throws IOException {
        byte[] maximum = encodeJpeg(image, maximumQuality);
        if (maximum.length <= targetBytes) {
            return maximum;
        }
        float low = 0.55f;
        byte[] best = encodeJpeg(image, low);
        if (best.length > targetBytes) {
            return null;
        }
        float high = maximumQuality;
        for (int attempt = 0; attempt < 6; attempt++) {
            float quality = (low + high) / 2.0f;
            byte[] candidate = encodeJpeg(image, quality);
            if (candidate.length <= targetBytes) {
                best = candidate;
                low = quality;
            } else {
                high = quality;
            }
        }
        return best;
    }

    public record Limits(int maximumBytes, int targetBytes, int maximumWidth,
                         int maximumHeight, float maximumQuality) {
        public Limits combine(Limits other) {
            return new Limits(
                    Math.min(maximumBytes, other.maximumBytes),
                    Math.min(targetBytes, other.targetBytes),
                    Math.min(maximumWidth, other.maximumWidth),
                    Math.min(maximumHeight, other.maximumHeight),
                    Math.min(maximumQuality, other.maximumQuality)
            );
        }
    }

    public record EncodedImage(byte[] bytes, int width, int height, byte[] instaxPixels, byte[] muralPixels) {
        public EncodedImage {
            bytes = bytes.clone();
            instaxPixels = instaxPixels == null ? new byte[0] : instaxPixels.clone();
            muralPixels = muralPixels == null ? new byte[0] : muralPixels.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public byte[] instaxPixels() {
            return instaxPixels.clone();
        }

        @Override
        public byte[] muralPixels() {
            return muralPixels.clone();
        }
    }
}
