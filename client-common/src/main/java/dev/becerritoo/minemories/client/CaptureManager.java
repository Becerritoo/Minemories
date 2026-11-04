package dev.becerritoo.minemories.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

public final class CaptureManager {
    private static final AtomicBoolean CAPTURING = new AtomicBoolean();
    private static volatile boolean waitingForCleanFrame;
    private static volatile boolean previousHudHidden;
    private static volatile String captureToken = "";

    private CaptureManager() {
    }

    public static boolean start(Minecraft client) {
        return start(client, "");
    }

    public static boolean start(Minecraft client, String cameraCaptureToken) {
        if (client.player == null || client.level == null || client.gui.screen() != null) {
            return false;
        }
        if (!CAPTURING.compareAndSet(false, true)) {
            return true;
        }
        captureToken = cameraCaptureToken == null ? "" : cameraCaptureToken;
        if (MinemoriesClient.config().cleanHud) {
            previousHudHidden = client.gui.hud.isHidden();
            if (!previousHudHidden) {
                client.gui.hud.toggle();
            }
            waitingForCleanFrame = true;
        } else {
            captureFramebuffer(client);
        }
        return true;
    }

    public static void afterFrameRendered(Minecraft client) {
        if (!waitingForCleanFrame) {
            return;
        }
        waitingForCleanFrame = false;
        if (client.gui.hud.isHidden() != previousHudHidden) {
            client.gui.hud.toggle();
        }
        captureFramebuffer(client);
    }

    private static void captureFramebuffer(Minecraft client) {
        try {
            Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), image -> onImage(client, image));
        } catch (RuntimeException exception) {
            CAPTURING.set(false);
            captureToken = "";
            notifyFailure(client, exception.getMessage());
        }
    }

    private static void onImage(Minecraft client, NativeImage image) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("minemories-preview-", ".png");
            image.writeToFile(temporary);
            byte[] bytes = Files.readAllBytes(temporary);
            String token = captureToken;
            captureToken = "";
            PhotoData photo = new PhotoData(bytes, image.getWidth(), image.getHeight(),
                    System.currentTimeMillis(), token);
            client.execute(() -> {
                CAPTURING.set(false);
                if (client.player == null || client.level == null) {
                    image.close();
                    return;
                }
                client.gui.setScreen(new PreviewScreen(photo, image));
            });
        } catch (IOException exception) {
            image.close();
            CAPTURING.set(false);
            captureToken = "";
            notifyFailure(client, exception.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void notifyFailure(Minecraft client, String reason) {
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§cMinemories no pudo capturar la imagen: " + reason));
            }
        });
    }
}
