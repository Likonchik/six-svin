package ru.levin.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.levin.manager.Manager;

// NoVisualADS (pose): applyFirstPersonGunTransform reads getClientAimingProgress once and feeds it to the
// gun pose / sway / animation. Force THAT read to 0 so the gun stays in idle/hip position (the idle
// positioning at line 197 still applies; only the aim-snap at line 198, weighted by aimingProgress, is
// zeroed). The FOV zoom reads getClientAimingProgress from a SEPARATE call in CameraSetupEvent, so it is
// untouched — screen still zooms. Composes with FastADS (which makes the zoom's read 1.0). remap=false.
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public class MixinFirstPersonRenderGunEvent {

    @ModifyExpressionValue(
            method = "applyFirstPersonGunTransform",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/api/client/gameplay/IClientPlayerGunOperator;getClientAimingProgress(F)F"),
            remap = false)
    private static float onapixSuppressAimPose(float original) {
        try {
            if (Manager.FUNCTION_MANAGER != null
                    && Manager.FUNCTION_MANAGER.noVisualAds != null
                    && Manager.FUNCTION_MANAGER.noVisualAds.suppressPose()) {
                return 0.0f;
            }
        } catch (Throwable ignored) {}
        return original;
    }
}
