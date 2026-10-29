package dev.becerritoo.minemories.fabric.mixin;

import dev.becerritoo.minemories.fabric.CaptureManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
abstract class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void minemories$replaceScreenshot(long window, int action, KeyInput input, CallbackInfo callback) {
        if (window != client.getWindow().getHandle() || action != InputUtil.GLFW_PRESS || input.hasCtrlOrCmd()) {
            return;
        }
        if (client.options.screenshotKey.matchesKey(input) && CaptureManager.start(client)) {
            callback.cancel();
        }
    }
}
