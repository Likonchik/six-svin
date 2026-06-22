package ru.levin.mixin.tacz;

import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.modules.combat.NoSway;

// No-Sway Hook 2: applyGunMovements(BedrockGunModel,float,float) (private static) = процедурная отдача-качание
// (applyShootSwayAndRotation, Perlin-шум) + качание прыжка/приземления (applyJumpingSway). Отменяем целиком по
// флагу NoSway -> ствол не дёргается при выстреле и прыжке. ОТДЕЛЬНЫЙ класс от MixinFirstPersonRenderGunEvent
// (там ModifyExpressionValue для NoVisualADS — не смешивать, оба целят FirstPersonRenderGunEvent). private
// static -> только @Inject HEAD (Redirect приватного статика снаружи невозможен). remap=false.
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public class MixinApplyGunMovements {

    @Inject(method = "applyGunMovements(Lcom/tacz/guns/client/model/BedrockGunModel;FF)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onetap$noShootJumpSway(CallbackInfo ci) {
        try {
            NoSway noSway = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.noSway : null;
            if (noSway != null && noSway.suppressGunMovements()) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
