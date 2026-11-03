package dev.becerritoo.minemories.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.becerritoo.minemories.client.CaptureManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void minemories$replaceScreenshot(long window, int action, KeyEvent input, CallbackInfo callback) {
        if (window != minecraft.getWindow().handle() || action != InputConstants.PRESS || input.hasControlDownWithQuirk()) {
            return;
        }
        if (minecraft.options.keyScreenshot.matches(input) && CaptureManager.start(minecraft)) {
            callback.cancel();
        }
    }
}
