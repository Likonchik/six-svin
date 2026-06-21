package ru.levin.mixin.tacz;

import com.tacz.guns.client.event.CameraSetupEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;

// TACZ-only mixin (config exosware.tacz.mixins.json is required:false — skipped if TACZ absent).
// applyCameraRecoil() is where TACZ applies recoil to player.setXRot/setYRot every frame; cancelling
// it = no recoil at all (and keeps the leaked server rotation steady — good for the aimbot too).
@Mixin(value = CameraSetupEvent.class, remap = false)
public class MixinCameraSetupEvent {

    @Inject(method = "applyCameraRecoil", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onApplyCameraRecoil(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER != null
                && Manager.FUNCTION_MANAGER.noRecoil != null
                && Manager.FUNCTION_MANAGER.noRecoil.state) {
            ci.cancel();
        }
    }
}
