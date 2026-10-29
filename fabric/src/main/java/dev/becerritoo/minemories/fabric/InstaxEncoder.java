package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.common.MapPaletteCodec;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class InstaxEncoder {
    private static final int LANCZOS_RADIUS = 3;
    private static final double[] SRGB_TO_LINEAR = buildLinearTable();

    private InstaxEncoder() {
    }

    static byte[] create(BufferedImage source) {
        return create(source, InstaxSettings.SHARP);
    }

    static byte[] create(BufferedImage source, InstaxSettings settings) {
        return create(source, settings, 1);
    }

    static byte[] createMural(BufferedImage source, InstaxSettings settings) {
        return create(source, settings, 2);
    }

    private static byte[] create(BufferedImage source, InstaxSettings settings, int scale) {
        InstaxSettings normalized = settings == null ? InstaxSettings.SHARP : settings;
        BufferedImage photograph = cropAndResize(source,
                MapPaletteCodec.PHOTO_WIDTH * scale, MapPaletteCodec.PHOTO_HEIGHT * scale, normalized);
        int canvasSize = MapPaletteCodec.MAP_SIZE * scale;
        BufferedImage instax = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = instax.createGraphics();
        try {
            graphics.setColor(new Color(244, 238, 219));
            graphics.fillRect(0, 0, instax.getWidth(), instax.getHeight());
            graphics.setColor(new Color(83, 77, 65));
            graphics.drawRect(scale, scale, (MapPaletteCodec.PHOTO_WIDTH + 1) * scale - 1,
                    (MapPaletteCodec.PHOTO_HEIGHT + 1) * scale - 1);
            graphics.drawImage(photograph, MapPaletteCodec.PHOTO_X * scale,
                    MapPaletteCodec.PHOTO_Y * scale, null);
            graphics.setColor(new Color(103, 95, 79));
            graphics.fillRect(54 * scale, 121 * scale, 20 * scale, scale);
        } finally {
            graphics.dispose();
        }
        int[] rgb = instax.getRGB(0, 0, canvasSize, canvasSize, null, 0, canvasSize);
        return MapPaletteCodec.quantize(rgb, canvasSize, canvasSize, normalized.dithering());
    }

    private static BufferedImage cropAndResize(BufferedImage source, int targetWidth, int targetHeight,
                                                InstaxSettings settings) {
        double targetAspect = targetWidth / (double) targetHeight;
        double sourceAspect = source.getWidth() / (double) source.getHeight();
        int cropWidth = source.getWidth();
        int cropHeight = source.getHeight();
        if (sourceAspect > targetAspect) {
            cropWidth = Math.max(1, (int) Math.round(cropHeight * targetAspect));
        } else if (sourceAspect < targetAspect) {
            cropHeight = Math.max(1, (int) Math.round(cropWidth / targetAspect));
        }
        int cropX = Math.max(0, (source.getWidth() - cropWidth) / 2);
        int cropY = Math.max(0, (source.getHeight() - cropHeight) / 2);
        BufferedImage resized = settings.filter() == InstaxSettings.Filter.BICUBIC
                ? bicubicResize(source, cropX, cropY, cropWidth, cropHeight, targetWidth, targetHeight)
                : lanczosResize(source, cropX, cropY, cropWidth, cropHeight, targetWidth, targetHeight);
        return enhanceDetails(resized, settings);
    }

    private static BufferedImage bicubicResize(BufferedImage source, int cropX, int cropY,
                                                int cropWidth, int cropHeight,
                                                int targetWidth, int targetHeight) {
        BufferedImage current = source.getSubimage(cropX, cropY, cropWidth, cropHeight);
        while (current.getWidth() / 2 >= targetWidth && current.getHeight() / 2 >= targetHeight) {
            current = awtResize(current, Math.max(targetWidth, current.getWidth() / 2),
                    Math.max(targetHeight, current.getHeight() / 2),
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        return awtResize(current, targetWidth, targetHeight,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    private static BufferedImage awtResize(BufferedImage source, int width, int height, Object interpolation) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage lanczosResize(BufferedImage source, int cropX, int cropY,
                                                int cropWidth, int cropHeight,
                                                int targetWidth, int targetHeight) {
        Filter[] horizontalFilters = filters(cropWidth, targetWidth);
        float[] horizontal = new float[targetWidth * cropHeight * 3];
        for (int y = 0; y < cropHeight; y++) {
            for (int x = 0; x < targetWidth; x++) {
                Filter filter = horizontalFilters[x];
                double red = 0;
                double green = 0;
                double blue = 0;
                for (int sample = 0; sample < filter.indices.length; sample++) {
                    int color = source.getRGB(cropX + filter.indices[sample], cropY + y);
                    double weight = filter.weights[sample];
                    red += SRGB_TO_LINEAR[(color >>> 16) & 0xff] * weight;
                    green += SRGB_TO_LINEAR[(color >>> 8) & 0xff] * weight;
                    blue += SRGB_TO_LINEAR[color & 0xff] * weight;
                }
                int offset = (y * targetWidth + x) * 3;
                horizontal[offset] = (float) red;
                horizontal[offset + 1] = (float) green;
                horizontal[offset + 2] = (float) blue;
            }
        }

        Filter[] verticalFilters = filters(cropHeight, targetHeight);
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < targetHeight; y++) {
            Filter filter = verticalFilters[y];
            for (int x = 0; x < targetWidth; x++) {
                double red = 0;
                double green = 0;
                double blue = 0;
                for (int sample = 0; sample < filter.indices.length; sample++) {
                    int offset = (filter.indices[sample] * targetWidth + x) * 3;
                    double weight = filter.weights[sample];
                    red += horizontal[offset] * weight;
                    green += horizontal[offset + 1] * weight;
                    blue += horizontal[offset + 2] * weight;
                }
                target.setRGB(x, y, linearToRgb(red, green, blue));
            }
        }
        return target;
    }

    private static BufferedImage enhanceDetails(BufferedImage source, InstaxSettings settings) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] original = source.getRGB(0, 0, width, height, null, 0, width);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] enhanced = new int[original.length];
        int[] kernel = {1, 2, 1, 2, 4, 2, 1, 2, 1};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int blurRed = 0;
                int blurGreen = 0;
                int blurBlue = 0;
                int kernelIndex = 0;
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    int sampleY = Math.max(0, Math.min(height - 1, y + offsetY));
                    for (int offsetX = -1; offsetX <= 1; offsetX++) {
                        int sampleX = Math.max(0, Math.min(width - 1, x + offsetX));
                        int color = original[sampleY * width + sampleX];
                        int weight = kernel[kernelIndex++];
                        blurRed += ((color >>> 16) & 0xff) * weight;
                        blurGreen += ((color >>> 8) & 0xff) * weight;
                        blurBlue += (color & 0xff) * weight;
                    }
                }
                int color = original[y * width + x];
                double red = sharpen((color >>> 16) & 0xff, blurRed / 16.0, settings.sharpness());
                double green = sharpen((color >>> 8) & 0xff, blurGreen / 16.0, settings.sharpness());
                double blue = sharpen(color & 0xff, blurBlue / 16.0, settings.sharpness());
                double luminance = red * 0.2126 + green * 0.7152 + blue * 0.0722;
                red = (luminance + (red - luminance) * settings.saturation() - 128.0)
                        * settings.contrast() + 128.0;
                green = (luminance + (green - luminance) * settings.saturation() - 128.0)
                        * settings.contrast() + 128.0;
                blue = (luminance + (blue - luminance) * settings.saturation() - 128.0)
                        * settings.contrast() + 128.0;
                enhanced[y * width + x] = clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
            }
        }
        result.setRGB(0, 0, width, height, enhanced, 0, width);
        return result;
    }

    private static double sharpen(int original, double blurred, double amount) {
        return original + (original - blurred) * amount;
    }

    private static Filter[] filters(int sourceSize, int targetSize) {
        Filter[] result = new Filter[targetSize];
        double scale = sourceSize / (double) targetSize;
        double filterScale = Math.max(1.0, scale);
        double support = LANCZOS_RADIUS * filterScale;
        for (int target = 0; target < targetSize; target++) {
            double center = (target + 0.5) * scale - 0.5;
            int first = (int) Math.ceil(center - support);
            int last = (int) Math.floor(center + support);
            int[] indices = new int[last - first + 1];
            double[] weights = new double[indices.length];
            double total = 0;
            for (int sample = first; sample <= last; sample++) {
                int offset = sample - first;
                indices[offset] = Math.max(0, Math.min(sourceSize - 1, sample));
                double weight = lanczos((sample - center) / filterScale);
                weights[offset] = weight;
                total += weight;
            }
            for (int index = 0; index < weights.length; index++) {
                weights[index] /= total;
            }
            result[target] = new Filter(indices, weights);
        }
        return result;
    }

    private static double lanczos(double value) {
        value = Math.abs(value);
        if (value < 1.0e-9) {
            return 1.0;
        }
        if (value >= LANCZOS_RADIUS) {
            return 0.0;
        }
        double piValue = Math.PI * value;
        return Math.sin(piValue) / piValue
                * Math.sin(piValue / LANCZOS_RADIUS) / (piValue / LANCZOS_RADIUS);
    }

    private static double[] buildLinearTable() {
        double[] table = new double[256];
        for (int value = 0; value < table.length; value++) {
            double normalized = value / 255.0;
            table[value] = normalized <= 0.04045
                    ? normalized / 12.92
                    : Math.pow((normalized + 0.055) / 1.055, 2.4);
        }
        return table;
    }

    private static int linearToRgb(double red, double green, double blue) {
        return linearToSrgb(red) << 16 | linearToSrgb(green) << 8 | linearToSrgb(blue);
    }

    private static int linearToSrgb(double value) {
        value = Math.max(0.0, Math.min(1.0, value));
        double encoded = value <= 0.0031308
                ? value * 12.92
                : 1.055 * Math.pow(value, 1.0 / 2.4) - 0.055;
        return clamp(encoded * 255.0);
    }

    private static int clamp(double value) {
        return Math.max(0, Math.min(255, (int) Math.round(value)));
    }

    private record Filter(int[] indices, double[] weights) {
    }
}
