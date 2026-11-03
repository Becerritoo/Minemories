package dev.becerritoo.minemories.client.mixin;

import dev.becerritoo.minemories.client.CameraAimController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class InGameHudMixin {
    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void minemories$hideCameraSpyglassOverlay(GuiGraphics context, float scale,
                                                       CallbackInfo callback) {
        if (CameraAimController.isAimingCamera(Minecraft.getInstance())) {
            callback.cancel();
        }
    }
}
