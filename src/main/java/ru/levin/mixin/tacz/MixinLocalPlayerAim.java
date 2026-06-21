package ru.levin.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tacz.guns.client.gameplay.LocalPlayerAim;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import ru.levin.manager.Manager;

// FastADS: getClientAimingProgress() is the single value every first-person ADS visual reads (FOV zoom,
// gun pose, scope lens, crosshair-hide). Snap it to 1.0 while actually aiming so the scope opens with no
// aimTime ramp. While not aiming we leave the original (so the close still ramps down). remap=false;
// try/catch -> inert without TACZ.
@Mixin(value = LocalPlayerAim.class, remap = false)
public abstract class MixinLocalPlayerAim {

    @Shadow
    public abstract boolean isAim();

    @ModifyReturnValue(method = "getClientAimingProgress(F)F", at = @At("RETURN"), remap = false)
    private float onapixGetClientAimingProgress(float original) {
        try {
            if (Manager.FUNCTION_MANAGER != null
                    && Manager.FUNCTION_MANAGER.fastAds != null
                    && Manager.FUNCTION_MANAGER.fastAds.instantClient()
                    && isAim()) {
                return 1.0f;
            }
        } catch (Throwable ignored) {}
        return original;
    }
}
