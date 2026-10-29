package dev.becerritoo.minemories.paper;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.jetbrains.annotations.NotNull;

final class StaticPhotoMapRenderer extends MapRenderer {
    private final byte[] pixels;
    private boolean rendered;

    StaticPhotoMapRenderer(byte[] pixels) {
        super(false);
        if (pixels.length != 128 * 128) {
            throw new IllegalArgumentException("El mapa debe contener 16384 píxeles");
        }
        this.pixels = pixels.clone();
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (rendered) {
            return;
        }
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                canvas.setPixel(x, y, pixels[y * 128 + x]);
            }
        }
        rendered = true;
    }
}
