package dev.becerritoo.minemories.client.mixin;

import dev.becerritoo.minemories.client.CameraAimController;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void minemories$disableCameraZoom(boolean firstPerson, float fovEffectScale,
                                               CallbackInfoReturnable<Float> callback) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (firstPerson && CameraAimController.isCamera(player.getUseItem())) {
            callback.setReturnValue(1.0F);
        }
    }
}
