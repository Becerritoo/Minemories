package dev.becerritoo.minemories.client;

import dev.becerritoo.minemories.client.net.NetworkBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CameraAimController {
    private static final Identifier CAMERA_MODEL = Identifier.fromNamespaceAndPath("minemories", "camera");
    private static boolean shutterSent;

    private CameraAimController() {
    }

    public static void tick(Minecraft client) {
        if (!isAimingCamera(client)) {
            shutterSent = false;
            return;
        }
        if (client.screen != null) {
            return;
        }

        boolean pressed = false;
        while (client.options.keyAttack.consumeClick()) {
            pressed = true;
        }
        if (pressed && !shutterSent && !client.player.isShiftKeyDown()
                && NetworkBridge.requestCameraShutter()) {
            shutterSent = true;
        }
    }

    public static boolean isAimingCamera(Minecraft client) {
        return client.player != null && client.player.isUsingItem()
                && isCamera(client.player.getUseItem());
    }

    public static boolean isCamera(ItemStack stack) {
        return stack.is(Items.SPYGLASS)
                && CAMERA_MODEL.equals(stack.get(DataComponents.ITEM_MODEL));
    }

    public static void reset() {
        shutterSent = false;
    }
}
