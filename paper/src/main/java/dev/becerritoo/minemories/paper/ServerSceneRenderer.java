package dev.becerritoo.minemories.paper;

import dev.becerritoo.minemories.common.MapPaletteCodec;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ServerSceneRenderer {
    static final int MAP_SIZE = MapPaletteCodec.MAP_SIZE;
    private static final int IMAGE_X = MapPaletteCodec.PHOTO_X;
    private static final int IMAGE_Y = MapPaletteCodec.PHOTO_Y;
    private static final int IMAGE_WIDTH = MapPaletteCodec.PHOTO_WIDTH;
    private static final int IMAGE_HEIGHT = MapPaletteCodec.PHOTO_HEIGHT;

    private ServerSceneRenderer() {
    }

    static Scene capture(Player player, Settings settings) {
        World world = player.getWorld();
        var eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector right = forward.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.0001) {
            right = new Vector(1, 0, 0);
        } else {
            right.normalize();
        }
        Vector up = right.clone().crossProduct(forward).normalize();
        int distance = settings.serverRendererDistance();
        int minChunkX = Math.floorDiv(eye.getBlockX() - distance, 16);
        int maxChunkX = Math.floorDiv(eye.getBlockX() + distance, 16);
        int minChunkZ = Math.floorDiv(eye.getBlockZ() - distance, 16);
        int maxChunkZ = Math.floorDiv(eye.getBlockZ() + distance, 16);
        Map<Long, ChunkSnapshot> chunks = new HashMap<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    chunks.put(chunkKey(chunkX, chunkZ),
                            world.getChunkAt(chunkX, chunkZ).getChunkSnapshot(false, false, false));
                }
            }
        }

        List<EntitySnapshot> entities = new ArrayList<>();
        if (settings.serverRendererEntities()) {
            BoundingBox area = BoundingBox.of(eye, distance, distance, distance);
            for (Entity entity : world.getNearbyEntities(area)) {
                if (entity == player || entity.getType() == EntityType.ITEM
                        || entity.getType() == EntityType.EXPERIENCE_ORB) {
                    continue;
                }
                var bounds = entity.getBoundingBox();
                Color primary = entity instanceof Sheep sheep
                        ? dyeColor(sheep.getColor().name()) : entityColor(entity.getType());
                entities.add(new EntitySnapshot(entity.getType(),
                        (bounds.getMinX() + bounds.getMaxX()) * 0.5,
                        bounds.getMinY(),
                        (bounds.getMinZ() + bounds.getMaxZ()) * 0.5,
                        Math.max(0.25, bounds.getWidthX()),
                        Math.max(0.25, bounds.getHeight()), primary.getRGB()));
            }
        }
        return new Scene(eye.getX(), eye.getY(), eye.getZ(), vec(forward), vec(right), vec(up),
                world.getMinHeight(), world.getMaxHeight(), distance, world.getEnvironment(),
                world.getTime(), world.hasStorm(), Map.copyOf(chunks), List.copyOf(entities));
    }

    static Rendered render(Scene scene) {
        BufferedImage photograph = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        double[] depth = new double[IMAGE_WIDTH * IMAGE_HEIGHT];
        double tanVertical = Math.tan(Math.toRadians(70.0 / 2.0));
        double tanHorizontal = tanVertical * IMAGE_WIDTH / (double) IMAGE_HEIGHT;
        for (int y = 0; y < IMAGE_HEIGHT; y++) {
            double screenY = (1.0 - 2.0 * ((y + 0.5) / IMAGE_HEIGHT)) * tanVertical;
            for (int x = 0; x < IMAGE_WIDTH; x++) {
                double screenX = (2.0 * ((x + 0.5) / IMAGE_WIDTH) - 1.0) * tanHorizontal;
                Vec direction = scene.forward().add(scene.right().multiply(screenX))
                        .add(scene.up().multiply(screenY)).normalize();
                Hit hit = cast(scene, direction);
                int rgb;
                if (hit == null) {
                    rgb = sky(scene, y).getRGB();
                    depth[y * IMAGE_WIDTH + x] = Double.POSITIVE_INFINITY;
                } else {
                    rgb = shade(blockColor(hit.material()), hit.brightness() * hit.variation(),
                            hit.distance(), scene.distance()).getRGB();
                    depth[y * IMAGE_WIDTH + x] = hit.distance();
                }
                photograph.setRGB(x, y, rgb);
            }
        }
        accentTerrainEdges(photograph, depth);
        drawEntities(scene, photograph, depth, tanHorizontal, tanVertical);

        BufferedImage instax = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = instax.createGraphics();
        try {
            graphics.setColor(new Color(244, 238, 219));
            graphics.fillRect(0, 0, MAP_SIZE, MAP_SIZE);
            graphics.setColor(new Color(83, 77, 65));
            graphics.drawRect(1, 1, IMAGE_WIDTH + 1, IMAGE_HEIGHT + 1);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(photograph, IMAGE_X, IMAGE_Y, IMAGE_WIDTH, IMAGE_HEIGHT, null);
            graphics.setColor(new Color(103, 95, 79));
            graphics.fillRect(54, 121, 20, 1);
        } finally {
            graphics.dispose();
        }
        int[] rgb = instax.getRGB(0, 0, MAP_SIZE, MAP_SIZE, null, 0, MAP_SIZE);
        return new Rendered(instax, MapPaletteCodec.quantizeDithered(rgb, MAP_SIZE, MAP_SIZE));
    }

    static BufferedImage upscale(BufferedImage source, int factor) {
        int width = source.getWidth() * factor;
        int height = source.getHeight() * factor;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static Hit cast(Scene scene, Vec ray) {
        int x = floor(scene.x());
        int y = floor(scene.y());
        int z = floor(scene.z());
        int stepX = ray.x() < 0 ? -1 : 1;
        int stepY = ray.y() < 0 ? -1 : 1;
        int stepZ = ray.z() < 0 ? -1 : 1;
        double deltaX = ray.x() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / ray.x());
        double deltaY = ray.y() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / ray.y());
        double deltaZ = ray.z() == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / ray.z());
        double maxX = boundary(scene.x(), x, stepX, ray.x());
        double maxY = boundary(scene.y(), y, stepY, ray.y());
        double maxZ = boundary(scene.z(), z, stepZ, ray.z());
        double distance = 0;
        double face = 1.0;
        while (distance <= scene.distance()) {
            if (maxX < maxY && maxX < maxZ) {
                x += stepX;
                distance = maxX;
                maxX += deltaX;
                face = 0.82;
            } else if (maxY < maxZ) {
                y += stepY;
                distance = maxY;
                maxY += deltaY;
                face = stepY < 0 ? 1.0 : 0.55;
            } else {
                z += stepZ;
                distance = maxZ;
                maxZ += deltaZ;
                face = 0.72;
            }
            if (y < scene.minY() || y >= scene.maxY()) {
                continue;
            }
            ChunkSnapshot chunk = scene.chunks().get(chunkKey(Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
            if (chunk == null) {
                continue;
            }
            Material material = chunk.getBlockType(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
            if (material.isAir()) {
                continue;
            }
            int sky = chunk.getBlockSkyLight(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
            int emitted = chunk.getBlockEmittedLight(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
            double light = 0.42 + Math.max(sky, emitted) / 15.0 * 0.58;
            return new Hit(material, Math.max(0.2, face * light), distance, variation(x, y, z));
        }
        return null;
    }

    private static void drawEntities(Scene scene, BufferedImage image, double[] depth,
                                     double tanHorizontal, double tanVertical) {
        for (EntitySnapshot entity : scene.entities()) {
            Vec relative = new Vec(entity.x() - scene.x(), entity.y() - scene.y(), entity.z() - scene.z());
            double cameraZ = relative.dot(scene.forward());
            if (cameraZ <= 0.25 || cameraZ > scene.distance()) {
                continue;
            }
            double cameraX = relative.dot(scene.right());
            double cameraY = relative.dot(scene.up());
            int centerX = (int) Math.round((cameraX / (cameraZ * tanHorizontal) + 1) * IMAGE_WIDTH * 0.5);
            int bottomY = (int) Math.round((1 - cameraY / (cameraZ * tanVertical)) * IMAGE_HEIGHT * 0.5);
            int pixelWidth = Math.max(1, (int) Math.round(entity.width() / (cameraZ * tanHorizontal)
                    * IMAGE_WIDTH * 0.5));
            int pixelHeight = Math.max(2, (int) Math.round(entity.height() / (cameraZ * tanVertical)
                    * IMAGE_HEIGHT * 0.5));
            int left = Math.max(0, centerX - pixelWidth / 2);
            int right = Math.min(IMAGE_WIDTH - 1, centerX + pixelWidth / 2);
            int top = Math.max(0, bottomY - pixelHeight);
            int bottom = Math.min(IMAGE_HEIGHT - 1, bottomY);
            drawSprite(image, depth, entity, left, top, right, bottom, cameraZ);
        }
    }

    private static void drawSprite(BufferedImage image, double[] depth, EntitySnapshot entity,
                                   int left, int top, int right, int bottom, double cameraZ) {
        String name = entity.type().name();
        Color primary = new Color(entity.primaryRgb());
        Color outline = mix(primary, Color.BLACK, 0.58);
        Color shadow = mix(primary, Color.BLACK, 0.28);
        Color light = mix(primary, Color.WHITE, 0.22);

        if (isQuadruped(name)) {
            // Patas detrás del cuerpo.
            spriteRect(image, depth, left, top, right, bottom, 0.13, 0.56, 0.29, 1.0,
                    outline, cameraZ);
            spriteRect(image, depth, left, top, right, bottom, 0.17, 0.58, 0.26, 1.0,
                    shadow, cameraZ - 0.01);
            spriteRect(image, depth, left, top, right, bottom, 0.55, 0.56, 0.72, 1.0,
                    outline, cameraZ);
            spriteRect(image, depth, left, top, right, bottom, 0.59, 0.58, 0.68, 1.0,
                    shadow, cameraZ - 0.01);
            // Cuerpo, cabeza y hocico.
            spriteRect(image, depth, left, top, right, bottom, 0.03, 0.16, 0.78, 0.69,
                    outline, cameraZ - 0.02);
            spriteRect(image, depth, left, top, right, bottom, 0.07, 0.20, 0.74, 0.63,
                    light, cameraZ - 0.03);
            spriteRect(image, depth, left, top, right, bottom, 0.67, 0.08, 0.98, 0.58,
                    outline, cameraZ - 0.04);
            Color face = name.contains("SHEEP") ? new Color(157, 153, 143) : shadow;
            spriteRect(image, depth, left, top, right, bottom, 0.71, 0.13, 0.94, 0.53,
                    face, cameraZ - 0.05);
            spriteRect(image, depth, left, top, right, bottom, 0.88, 0.32, 1.0, 0.53,
                    mix(face, Color.BLACK, 0.2), cameraZ - 0.06);
            spriteRect(image, depth, left, top, right, bottom, 0.77, 0.23, 0.83, 0.30,
                    Color.BLACK, cameraZ - 0.07);
            return;
        }

        if (name.contains("SPIDER")) {
            for (int leg = 0; leg < 4; leg++) {
                double y = 0.28 + leg * 0.13;
                spriteRect(image, depth, left, top, right, bottom, 0.0, y, 0.32, y + 0.07,
                        outline, cameraZ);
                spriteRect(image, depth, left, top, right, bottom, 0.68, y, 1.0, y + 0.07,
                        outline, cameraZ);
            }
            spriteRect(image, depth, left, top, right, bottom, 0.18, 0.22, 0.72, 0.72,
                    outline, cameraZ - 0.02);
            spriteRect(image, depth, left, top, right, bottom, 0.25, 0.28, 0.66, 0.66,
                    primary, cameraZ - 0.03);
            spriteRect(image, depth, left, top, right, bottom, 0.61, 0.31, 0.91, 0.63,
                    shadow, cameraZ - 0.04);
            return;
        }

        if (name.contains("CREEPER")) {
            spriteRect(image, depth, left, top, right, bottom, 0.18, 0.0, 0.82, 0.34,
                    outline, cameraZ);
            spriteRect(image, depth, left, top, right, bottom, 0.23, 0.04, 0.77, 0.30,
                    light, cameraZ - 0.01);
            spriteRect(image, depth, left, top, right, bottom, 0.29, 0.31, 0.71, 0.77,
                    primary, cameraZ - 0.01);
            spriteRect(image, depth, left, top, right, bottom, 0.20, 0.73, 0.43, 1.0,
                    shadow, cameraZ);
            spriteRect(image, depth, left, top, right, bottom, 0.57, 0.73, 0.80, 1.0,
                    shadow, cameraZ);
            spriteRect(image, depth, left, top, right, bottom, 0.31, 0.10, 0.42, 0.19,
                    Color.BLACK, cameraZ - 0.03);
            spriteRect(image, depth, left, top, right, bottom, 0.58, 0.10, 0.69, 0.19,
                    Color.BLACK, cameraZ - 0.03);
            return;
        }

        // Humanoides: cabeza, torso, brazos y piernas separados.
        Color skin = name.contains("SKELETON") ? new Color(196, 196, 187)
                : name.contains("ZOMBIE") ? new Color(91, 139, 91) : new Color(185, 132, 96);
        spriteRect(image, depth, left, top, right, bottom, 0.28, 0.0, 0.72, 0.24,
                outline, cameraZ);
        spriteRect(image, depth, left, top, right, bottom, 0.33, 0.03, 0.67, 0.21,
                skin, cameraZ - 0.01);
        spriteRect(image, depth, left, top, right, bottom, 0.22, 0.22, 0.78, 0.66,
                outline, cameraZ);
        spriteRect(image, depth, left, top, right, bottom, 0.27, 0.25, 0.73, 0.63,
                primary, cameraZ - 0.01);
        spriteRect(image, depth, left, top, right, bottom, 0.05, 0.25, 0.24, 0.72,
                shadow, cameraZ);
        spriteRect(image, depth, left, top, right, bottom, 0.76, 0.25, 0.95, 0.72,
                shadow, cameraZ);
        spriteRect(image, depth, left, top, right, bottom, 0.25, 0.63, 0.48, 1.0,
                outline, cameraZ);
        spriteRect(image, depth, left, top, right, bottom, 0.52, 0.63, 0.75, 1.0,
                outline, cameraZ);
        spriteRect(image, depth, left, top, right, bottom, 0.34, 0.10, 0.40, 0.15,
                Color.BLACK, cameraZ - 0.03);
        spriteRect(image, depth, left, top, right, bottom, 0.60, 0.10, 0.66, 0.15,
                Color.BLACK, cameraZ - 0.03);
    }

    private static void spriteRect(BufferedImage image, double[] depth,
                                   int left, int top, int right, int bottom,
                                   double minX, double minY, double maxX, double maxY,
                                   Color color, double cameraZ) {
        int width = Math.max(1, right - left + 1);
        int height = Math.max(1, bottom - top + 1);
        int fromX = Math.max(left, left + (int) Math.floor(minX * width));
        int toX = Math.min(right, left + (int) Math.ceil(maxX * width) - 1);
        int fromY = Math.max(top, top + (int) Math.floor(minY * height));
        int toY = Math.min(bottom, top + (int) Math.ceil(maxY * height) - 1);
        for (int y = fromY; y <= toY; y++) {
            for (int x = fromX; x <= toX; x++) {
                int index = y * IMAGE_WIDTH + x;
                if (cameraZ < depth[index]) {
                    image.setRGB(x, y, color.getRGB());
                    depth[index] = cameraZ;
                }
            }
        }
    }

    private static boolean isQuadruped(String name) {
        return name.contains("SHEEP") || name.contains("COW") || name.contains("PIG")
                || name.contains("HORSE") || name.contains("DONKEY") || name.contains("MULE")
                || name.contains("WOLF") || name.contains("CAT") || name.contains("FOX")
                || name.contains("GOAT") || name.contains("LLAMA") || name.contains("CAMEL")
                || name.contains("RABBIT") || name.contains("POLAR_BEAR") || name.contains("PANDA");
    }

    private static void accentTerrainEdges(BufferedImage image, double[] depth) {
        int[] source = image.getRGB(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null, 0, IMAGE_WIDTH);
        for (int y = 0; y < IMAGE_HEIGHT - 1; y++) {
            for (int x = 0; x < IMAGE_WIDTH - 1; x++) {
                int index = y * IMAGE_WIDTH + x;
                double current = depth[index];
                double horizontal = depth[index + 1];
                double vertical = depth[index + IMAGE_WIDTH];
                if (edge(current, horizontal) || edge(current, vertical)) {
                    Color color = new Color(source[index]);
                    image.setRGB(x, y, new Color((int) (color.getRed() * 0.72),
                            (int) (color.getGreen() * 0.72),
                            (int) (color.getBlue() * 0.72)).getRGB());
                }
            }
        }
    }

    private static boolean edge(double first, double second) {
        if (Double.isInfinite(first) != Double.isInfinite(second)) {
            return true;
        }
        return !Double.isInfinite(first) && Math.abs(first - second) > 1.15;
    }

    private static double variation(int x, int y, int z) {
        int hash = x * 73_856_093 ^ y * 19_349_663 ^ z * 83_492_791;
        return 0.92 + ((hash >>> 4) & 15) / 100.0;
    }

    private static Color mix(Color first, Color second, double amount) {
        double inverse = 1.0 - amount;
        return new Color(clamp((int) Math.round(first.getRed() * inverse + second.getRed() * amount)),
                clamp((int) Math.round(first.getGreen() * inverse + second.getGreen() * amount)),
                clamp((int) Math.round(first.getBlue() * inverse + second.getBlue() * amount)));
    }

    private static Color blockColor(Material material) {
        String name = material.name();
        if (name.contains("WATER")) return new Color(48, 92, 181);
        if (name.contains("LAVA") || name.contains("MAGMA")) return new Color(225, 87, 18);
        if (name.contains("GRASS") || name.contains("MOSS")) return new Color(91, 139, 56);
        if (name.contains("LEAVES") || name.contains("VINE")) return new Color(55, 112, 48);
        if (name.contains("LOG") || name.contains("WOOD") || name.contains("PLANK")) return new Color(135, 102, 63);
        if (name.contains("SAND") || name.contains("END_STONE")) return new Color(218, 205, 145);
        if (name.contains("SNOW") || name.contains("QUARTZ")) return new Color(234, 235, 232);
        if (name.contains("ICE")) return new Color(142, 181, 232);
        if (name.contains("DIRT") || name.contains("MUD") || name.contains("SOUL")) return new Color(113, 79, 55);
        if (name.contains("DEEPSLATE") || name.contains("BLACKSTONE")) return new Color(62, 62, 68);
        if (name.contains("STONE") || name.contains("COBBLE") || name.contains("ORE")) return new Color(122, 122, 122);
        if (name.contains("BRICK") || name.contains("NETHERRACK")) return new Color(139, 65, 59);
        if (name.contains("GOLD") || name.contains("HONEY")) return new Color(232, 191, 46);
        if (name.contains("COPPER")) return new Color(183, 105, 73);
        if (name.contains("REDSTONE") || name.contains("RED_")) return new Color(176, 48, 43);
        if (name.contains("GLASS")) return new Color(172, 200, 208);
        if (name.contains("WOOL") || name.contains("CONCRETE") || name.contains("TERRACOTTA")) {
            return dyeColor(name);
        }
        if (name.contains("FLOWER") || name.contains("TULIP") || name.contains("DANDELION")) {
            return new Color(218, 101, 88);
        }
        return new Color(145, 137, 125);
    }

    private static Color dyeColor(String name) {
        if (name.startsWith("WHITE")) return new Color(220, 223, 219);
        if (name.startsWith("BLACK")) return new Color(38, 40, 43);
        if (name.startsWith("BLUE")) return new Color(53, 70, 154);
        if (name.startsWith("LIGHT_BLUE")) return new Color(83, 153, 207);
        if (name.startsWith("GREEN")) return new Color(74, 111, 40);
        if (name.startsWith("LIME")) return new Color(112, 185, 25);
        if (name.startsWith("RED")) return new Color(161, 47, 39);
        if (name.startsWith("YELLOW")) return new Color(226, 181, 29);
        if (name.startsWith("ORANGE")) return new Color(225, 111, 24);
        if (name.startsWith("PURPLE") || name.startsWith("MAGENTA")) return new Color(139, 67, 166);
        if (name.startsWith("PINK")) return new Color(217, 129, 153);
        if (name.startsWith("CYAN")) return new Color(36, 133, 145);
        if (name.startsWith("BROWN")) return new Color(112, 72, 42);
        if (name.startsWith("GRAY")) return new Color(83, 87, 87);
        return new Color(145, 137, 125);
    }

    private static Color entityColor(EntityType type) {
        String name = type.name().toUpperCase(Locale.ROOT);
        if (type == EntityType.PLAYER) return new Color(65, 139, 201);
        if (name.contains("CREEPER")) return new Color(81, 161, 65);
        if (name.contains("ZOMBIE")) return new Color(74, 128, 71);
        if (name.contains("SKELETON")) return new Color(185, 185, 177);
        if (name.contains("PIG")) return new Color(222, 151, 151);
        if (name.contains("COW")) return new Color(92, 65, 48);
        if (name.contains("SHEEP")) return new Color(221, 218, 207);
        if (name.contains("VILLAGER")) return new Color(168, 117, 88);
        return new Color(151, 102, 77);
    }

    private static Color shade(Color base, double light, double distance, double maximum) {
        double fog = Math.min(0.52, distance / maximum * 0.42);
        int r = clamp((int) Math.round(base.getRed() * light * (1 - fog) + 169 * fog));
        int g = clamp((int) Math.round(base.getGreen() * light * (1 - fog) + 190 * fog));
        int b = clamp((int) Math.round(base.getBlue() * light * (1 - fog) + 202 * fog));
        return new Color(r, g, b);
    }

    private static Color sky(Scene scene, int y) {
        if (scene.environment() == World.Environment.NETHER) return new Color(70, 28, 25);
        if (scene.environment() == World.Environment.THE_END) return new Color(43, 35, 60);
        boolean night = scene.time() > 13_000 && scene.time() < 23_000;
        if (night) return new Color(22 + y / 10, 29 + y / 10, 55 + y / 8);
        if (scene.storm()) return new Color(105 + y / 8, 116 + y / 8, 126 + y / 8);
        return new Color(104 + y / 5, 158 + y / 6, 214 + y / 8);
    }

    private static double boundary(double origin, int voxel, int step, double direction) {
        if (direction == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? voxel + 1.0 : voxel;
        return (boundary - origin) / direction;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static Vec vec(Vector value) {
        return new Vec(value.getX(), value.getY(), value.getZ());
    }

    record Rendered(BufferedImage image, byte[] mapPixels) {
        Rendered {
            mapPixels = mapPixels.clone();
        }

        @Override
        public byte[] mapPixels() {
            return mapPixels.clone();
        }
    }

    record Scene(double x, double y, double z, Vec forward, Vec right, Vec up,
                 int minY, int maxY, int distance, World.Environment environment,
                 long time, boolean storm, Map<Long, ChunkSnapshot> chunks,
                 List<EntitySnapshot> entities) {
    }

    record EntitySnapshot(EntityType type, double x, double y, double z, double width, double height,
                          int primaryRgb) {
    }

    record Vec(double x, double y, double z) {
        Vec add(Vec other) { return new Vec(x + other.x, y + other.y, z + other.z); }
        Vec multiply(double amount) { return new Vec(x * amount, y * amount, z * amount); }
        double dot(Vec other) { return x * other.x + y * other.y + z * other.z; }
        Vec normalize() {
            double length = Math.sqrt(x * x + y * y + z * z);
            return length == 0 ? this : multiply(1.0 / length);
        }
    }

    private record Hit(Material material, double brightness, double distance, double variation) {
    }
}
