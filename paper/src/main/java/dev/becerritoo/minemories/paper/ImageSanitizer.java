package dev.becerritoo.minemories.paper;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

final class ImageSanitizer {
    private ImageSanitizer() {
    }

    static SanitizedImage sanitize(byte[] source, String declaredType, Settings settings) throws IOException {
        requireMagic(source, declaredType);
        BufferedImage decoded;
        int width;
        int height;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("el archivo no es una imagen compatible");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
                validateDimensions(width, height, settings);
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (decoded == null) {
            throw new IOException("no se pudo decodificar la imagen");
        }

        BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(java.awt.Color.BLACK);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(decoded, 0, 0, null);
        graphics.dispose();

        byte[] encoded = encodeForTarget(rgb, settings.jpegQuality(), settings.targetFileBytes());
        while (encoded == null && rgb.getWidth() > 640 && rgb.getHeight() > 360) {
            int nextWidth = Math.max(640, (int) Math.floor(rgb.getWidth() * 0.88));
            int nextHeight = Math.max(360, (int) Math.floor(rgb.getHeight() * 0.88));
            BufferedImage resized = new BufferedImage(nextWidth, nextHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D resizeGraphics = resized.createGraphics();
            resizeGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            resizeGraphics.drawImage(rgb, 0, 0, nextWidth, nextHeight, null);
            resizeGraphics.dispose();
            rgb = resized;
            encoded = encodeForTarget(rgb, settings.jpegQuality(), settings.targetFileBytes());
        }
        if (encoded == null) {
            encoded = encodeJpeg(rgb, 0.55f);
        }
        if (encoded.length > settings.maxFileBytes()) {
            throw new IOException("la imagen procesada excede el límite del servidor");
        }
        return new SanitizedImage(encoded, rgb.getWidth(), rgb.getHeight(), "image/jpeg", "jpg");
    }

    private static void requireMagic(byte[] source, String declaredType) throws IOException {
        String type = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        boolean png = source.length >= 8
                && (source[0] & 0xff) == 0x89 && source[1] == 0x50 && source[2] == 0x4e && source[3] == 0x47
                && source[4] == 0x0d && source[5] == 0x0a && source[6] == 0x1a && source[7] == 0x0a;
        boolean jpeg = source.length >= 3 && (source[0] & 0xff) == 0xff && (source[1] & 0xff) == 0xd8
                && (source[2] & 0xff) == 0xff;
        if (!(png && "image/png".equals(type)) && !(jpeg && ("image/jpeg".equals(type) || "image/jpg".equals(type)))) {
            throw new IOException("solo se aceptan imágenes PNG o JPEG válidas");
        }
    }

    private static void validateDimensions(int width, int height, Settings settings) throws IOException {
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || width > settings.maxWidth() || height > settings.maxHeight()
                || pixels > settings.maxPixels()) {
            throw new IOException("las dimensiones de la imagen exceden el límite del servidor");
        }
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var output = new ByteArrayOutputStream(); ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            return output.toByteArray();
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

    record SanitizedImage(byte[] bytes, int width, int height, String contentType, String extension) {
    }
}
