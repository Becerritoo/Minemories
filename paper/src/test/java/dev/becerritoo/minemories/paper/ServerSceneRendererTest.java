package dev.becerritoo.minemories.paper;

import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ServerSceneRendererTest {
    @Test
    void createsPersistentInstaxSizedMapWithoutLoadedChunks() {
        ServerSceneRenderer.Scene scene = new ServerSceneRenderer.Scene(
                0.5, 64, 0.5,
                new ServerSceneRenderer.Vec(0, 0, -1),
                new ServerSceneRenderer.Vec(1, 0, 0),
                new ServerSceneRenderer.Vec(0, 1, 0),
                -64, 320, 48, World.Environment.NORMAL, 6_000, false,
                Map.of(), List.of());

        ServerSceneRenderer.Rendered rendered = ServerSceneRenderer.render(scene);

        assertEquals(128, rendered.image().getWidth());
        assertEquals(128, rendered.image().getHeight());
        assertEquals(128 * 128, rendered.mapPixels().length);
        assertNotEquals(rendered.image().getRGB(0, 0), rendered.image().getRGB(64, 40));
    }

    @Test
    void upscalesWithHardPixelEdges() {
        BufferedImage source = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xff112233);

        BufferedImage enlarged = ServerSceneRenderer.upscale(source, 4);

        assertEquals(8, enlarged.getWidth());
        assertEquals(8, enlarged.getHeight());
        assertEquals(source.getRGB(0, 0), enlarged.getRGB(3, 3));
    }
}
