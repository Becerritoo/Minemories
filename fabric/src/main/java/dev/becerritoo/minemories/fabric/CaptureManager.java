package dev.becerritoo.minemories.fabric;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CaptureManager {
    private static final AtomicBoolean CAPTURING = new AtomicBoolean();
    private static volatile boolean waitingForCleanFrame;
    private static volatile boolean previousHudHidden;
    private static volatile String captureToken = "";

    private CaptureManager() {
    }

    public static boolean start(MinecraftClient client) {
        return start(client, "");
    }

    public static boolean start(MinecraftClient client, String cameraCaptureToken) {
        if (client.player == null || client.world == null || client.currentScreen != null) {
            return false;
        }
        if (!CAPTURING.compareAndSet(false, true)) {
            return true;
        }
        captureToken = cameraCaptureToken == null ? "" : cameraCaptureToken;
        if (MinemoriesClient.config().cleanHud) {
            previousHudHidden = client.options.hudHidden;
            client.options.hudHidden = true;
            waitingForCleanFrame = true;
        } else {
            captureFramebuffer(client);
        }
        return true;
    }

    public static void afterFrameRendered(MinecraftClient client) {
        if (!waitingForCleanFrame) {
            return;
        }
        waitingForCleanFrame = false;
        client.options.hudHidden = previousHudHidden;
        captureFramebuffer(client);
    }

    private static void captureFramebuffer(MinecraftClient client) {
        try {
            ScreenshotRecorder.takeScreenshot(client.getFramebuffer(), image -> onImage(client, image));
        } catch (RuntimeException exception) {
            CAPTURING.set(false);
            captureToken = "";
            notifyFailure(client, exception.getMessage());
        }
    }

    private static void onImage(MinecraftClient client, NativeImage image) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("minemories-preview-", ".png");
            image.writeTo(temporary);
            byte[] bytes = Files.readAllBytes(temporary);
            String token = captureToken;
            captureToken = "";
            PhotoData photo = new PhotoData(bytes, image.getWidth(), image.getHeight(),
                    System.currentTimeMillis(), token);
            client.execute(() -> {
                CAPTURING.set(false);
                if (client.player == null || client.world == null) {
                    image.close();
                    return;
                }
                client.setScreen(new PreviewScreen(photo, image));
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

    private static void notifyFailure(MinecraftClient client, String reason) {
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cMinemories no pudo capturar la imagen: " + reason), false);
            }
        });
    }
}
