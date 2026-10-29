package dev.becerritoo.minemories.fabric.mixin;

import dev.becerritoo.minemories.fabric.CameraAimController;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getFovMultiplier", at = @At("RETURN"), cancellable = true)
    private void minemories$disableCameraZoom(boolean firstPerson, float fovEffectScale,
                                               CallbackInfoReturnable<Float> callback) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        if (firstPerson && CameraAimController.isCamera(player.getActiveItem())) {
            callback.setReturnValue(1.0F);
        }
    }
}
