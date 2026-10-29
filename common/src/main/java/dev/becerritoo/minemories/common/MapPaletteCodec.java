package dev.becerritoo.minemories.common;

/** Conversión independiente de Bukkit a la paleta de mapas de Minecraft 1.21. */
public final class MapPaletteCodec {
    public static final int MAP_SIZE = 128;
    public static final int PIXEL_COUNT = MAP_SIZE * MAP_SIZE;
    public static final int MURAL_SIZE = MAP_SIZE * 2;
    public static final int MURAL_PIXEL_COUNT = MURAL_SIZE * MURAL_SIZE;
    public static final int PHOTO_X = 2;
    public static final int PHOTO_Y = 2;
    public static final int PHOTO_WIDTH = 124;
    public static final int PHOTO_HEIGHT = 112;

    private static final int[] BASE_COLORS = {
            0x7FB238, 0xF7E9A3, 0xC7C7C7, 0xFF0000, 0xA0A0FF, 0xA7A7A7,
            0x007C00, 0xFFFFFF, 0xA4A8B8, 0x976D4D, 0x707070, 0x4040FF,
            0x8F7748, 0xFFFCF5, 0xD87F33, 0xB24CD8, 0x6699D8, 0xE5E533,
            0x7FCC19, 0xF27FA5, 0x4C4C4C, 0x999999, 0x4C7F99, 0x7F3FB2,
            0x334CB2, 0x664C33, 0x667F33, 0x993333, 0x191919, 0xFAEE4D,
            0x5CDBD5, 0x4A80FF, 0x00D93A, 0x815631, 0x700200, 0xD1B1A1,
            0x9F5224, 0x95576C, 0x706C8A, 0xBA8524, 0x677535, 0xA04D4E,
            0x392923, 0x876B62, 0x575C5C, 0x7A4958, 0x4C3E5C, 0x4C3223,
            0x4C522A, 0x8E3C2E, 0x251610, 0xBD3031, 0x943F61, 0x5C191D,
            0x167E86, 0x3A8E8C, 0x562C3E, 0x14B485, 0x646464, 0xD8AF93,
            0x7FA796
    };
    private static final int[] SHADES = {180, 220, 255, 135};
    private static final int[] COLORS = buildPalette();

    private MapPaletteCodec() {
    }

    public static byte[] quantizeDithered(int[] rgb, int width, int height) {
        return quantize(rgb, width, height, true);
    }

    public static byte[] quantize(int[] rgb, int width, int height, boolean dither) {
        if (width < 1 || height < 1 || rgb == null || rgb.length != width * height) {
            throw new IllegalArgumentException("Las dimensiones de la imagen Instax no son válidas");
        }
        byte[] result = new byte[width * height];
        double[] currentR = new double[width + 2];
        double[] currentG = new double[width + 2];
        double[] currentB = new double[width + 2];
        double[] nextR = new double[width + 2];
        double[] nextG = new double[width + 2];
        double[] nextB = new double[width + 2];
        for (int y = 0; y < height; y++) {
            boolean leftToRight = (y & 1) == 0;
            for (int step = 0; step < width; step++) {
                int x = leftToRight ? step : width - 1 - step;
                int value = rgb[y * width + x];
                int red = clamp((int) Math.round(((value >>> 16) & 0xff) + currentR[x + 1]));
                int green = clamp((int) Math.round(((value >>> 8) & 0xff) + currentG[x + 1]));
                int blue = clamp((int) Math.round((value & 0xff) + currentB[x + 1]));
                int index = nearest(red, green, blue);
                result[y * width + x] = (byte) index;
                int matched = COLORS[index];
                if (!dither) {
                    continue;
                }
                double errorR = red - ((matched >>> 16) & 0xff);
                double errorG = green - ((matched >>> 8) & 0xff);
                double errorB = blue - (matched & 0xff);
                int forward = leftToRight ? x + 2 : x;
                int lowerFar = leftToRight ? x : x + 2;
                int lowerNear = leftToRight ? x + 2 : x;
                currentR[forward] += errorR * 7.0 / 16.0;
                currentG[forward] += errorG * 7.0 / 16.0;
                currentB[forward] += errorB * 7.0 / 16.0;
                nextR[lowerFar] += errorR * 3.0 / 16.0;
                nextG[lowerFar] += errorG * 3.0 / 16.0;
                nextB[lowerFar] += errorB * 3.0 / 16.0;
                nextR[x + 1] += errorR * 5.0 / 16.0;
                nextG[x + 1] += errorG * 5.0 / 16.0;
                nextB[x + 1] += errorB * 5.0 / 16.0;
                nextR[lowerNear] += errorR / 16.0;
                nextG[lowerNear] += errorG / 16.0;
                nextB[lowerNear] += errorB / 16.0;
            }
            double[] swapR = currentR;
            double[] swapG = currentG;
            double[] swapB = currentB;
            currentR = nextR;
            currentG = nextG;
            currentB = nextB;
            nextR = swapR;
            nextG = swapG;
            nextB = swapB;
            java.util.Arrays.fill(nextR, 0);
            java.util.Arrays.fill(nextG, 0);
            java.util.Arrays.fill(nextB, 0);
        }
        return result;
    }

    public static boolean valid(byte[] pixels) {
        return valid(pixels, PIXEL_COUNT);
    }

    public static boolean validMural(byte[] pixels) {
        return valid(pixels, MURAL_PIXEL_COUNT);
    }

    private static boolean valid(byte[] pixels, int expectedLength) {
        if (pixels == null || pixels.length != expectedLength) {
            return false;
        }
        for (byte pixel : pixels) {
            int index = Byte.toUnsignedInt(pixel);
            if (index < 4 || index >= COLORS.length) {
                return false;
            }
        }
        return true;
    }

    public static int rgb(byte index) {
        int value = Byte.toUnsignedInt(index);
        if (value >= COLORS.length) {
            throw new IllegalArgumentException("Color de mapa inválido");
        }
        return COLORS[value];
    }

    private static int nearest(int red, int green, int blue) {
        int bestIndex = 4;
        long bestDistance = Long.MAX_VALUE;
        for (int index = 4; index < COLORS.length; index++) {
            int color = COLORS[index];
            int candidateRed = (color >>> 16) & 0xff;
            int candidateGreen = (color >>> 8) & 0xff;
            int candidateBlue = color & 0xff;
            int redSum = red + candidateRed;
            int deltaRed = red - candidateRed;
            int deltaGreen = green - candidateGreen;
            int deltaBlue = blue - candidateBlue;
            long distance = (1024L + redSum) * deltaRed * deltaRed
                    + 2048L * deltaGreen * deltaGreen
                    + (1534L - redSum) * deltaBlue * deltaBlue;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private static int[] buildPalette() {
        int[] colors = new int[4 + BASE_COLORS.length * 4];
        for (int base = 0; base < BASE_COLORS.length; base++) {
            int color = BASE_COLORS[base];
            int red = (color >>> 16) & 0xff;
            int green = (color >>> 8) & 0xff;
            int blue = color & 0xff;
            for (int shade = 0; shade < SHADES.length; shade++) {
                int factor = SHADES[shade];
                colors[4 + base * 4 + shade] = (red * factor / 255 << 16)
                        | (green * factor / 255 << 8) | blue * factor / 255;
            }
        }
        return colors;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
