package dev.becerritoo.minemories.fabric.mixin;

import dev.becerritoo.minemories.fabric.CameraAimController;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
abstract class InGameHudMixin {
    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void minemories$hideCameraSpyglassOverlay(DrawContext context, float scale,
                                                       CallbackInfo callback) {
        if (CameraAimController.isAimingCamera(MinecraftClient.getInstance())) {
            callback.cancel();
        }
    }
}
