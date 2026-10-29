package dev.becerritoo.minemories.fabric;

import dev.becerritoo.minemories.fabric.net.NetworkBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

public final class CameraAimController {
    private static final Identifier CAMERA_MODEL = Identifier.of("minemories", "camera");
    private static boolean shutterSent;

    private CameraAimController() {
    }

    public static void tick(MinecraftClient client) {
        if (!isAimingCamera(client)) {
            shutterSent = false;
            return;
        }
        if (client.currentScreen != null) {
            return;
        }

        boolean pressed = false;
        while (client.options.attackKey.wasPressed()) {
            pressed = true;
        }
        if (pressed && !shutterSent && !client.player.isSneaking()
                && NetworkBridge.requestCameraShutter()) {
            shutterSent = true;
        }
    }

    public static boolean isAimingCamera(MinecraftClient client) {
        return client.player != null && client.player.isUsingItem()
                && isCamera(client.player.getActiveItem());
    }

    public static boolean isCamera(ItemStack stack) {
        return stack.isOf(Items.SPYGLASS)
                && CAMERA_MODEL.equals(stack.get(DataComponentTypes.ITEM_MODEL));
    }

    public static void reset() {
        shutterSent = false;
    }
}
