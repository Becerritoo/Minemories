package dev.becerritoo.minemories.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.becerritoo.minemories.common.MapPaletteCodec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

final class InstaxPreviewGenerator {
    private InstaxPreviewGenerator() {
    }

    static byte[] generate(PhotoData photo, InstaxSettings settings) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(photo.pngBytes()));
            if (source == null) {
                throw new IOException("Captura no reconocida");
            }
            return InstaxEncoder.create(source, settings);
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo preparar la vista Instax", exception);
        }
    }

    static NativeImage nativeImage(byte[] pixels) {
        if (!MapPaletteCodec.valid(pixels)) {
            throw new IllegalArgumentException("Píxeles Instax inválidos");
        }
        NativeImage image = new NativeImage(MapPaletteCodec.MAP_SIZE, MapPaletteCodec.MAP_SIZE, false);
        for (int y = 0; y < MapPaletteCodec.MAP_SIZE; y++) {
            for (int x = 0; x < MapPaletteCodec.MAP_SIZE; x++) {
                int rgb = MapPaletteCodec.rgb(pixels[y * MapPaletteCodec.MAP_SIZE + x]);
                image.setPixel(x, y, 0xff000000 | rgb);
            }
        }
        return image;
    }
}
