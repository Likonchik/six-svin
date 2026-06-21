package ru.levin.mixin.tacz;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tacz.guns.resource.pojo.data.gun.InaccuracyType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.levin.manager.Manager;

// GunNoSpread "Инстант": the server picks shoot spread in InaccuracyType.getInaccuracyType() purely
// from getSynAimingProgress(); AIM (~0.15, pinpoint) requires == 1.0f. We force the result to AIM for
// the local player only, so spread vanishes WITHOUT ever setting isAiming (=> no movement slowdown,
// no FOV zoom). The method is static and runs for every shooter on the integrated server, so we scope
// by UUID (the shooter is the ServerPlayer, mc.player is the client LocalPlayer — different objects,
// same UUID). remap=false: TACZ is not Mojmap-remapped.
@Mixin(value = InaccuracyType.class, remap = false)
public class MixinInaccuracyType {

    @ModifyReturnValue(
            method = "getInaccuracyType(Lnet/minecraft/world/entity/LivingEntity;)Lcom/tacz/guns/resource/pojo/data/gun/InaccuracyType;",
            at = @At("RETURN"),
            remap = false)
    private static InaccuracyType onapixGetInaccuracyType(InaccuracyType original, LivingEntity living) {
        try {
            if (living != null
                    && Manager.FUNCTION_MANAGER != null
                    && Manager.FUNCTION_MANAGER.gunNoSpread != null
                    && Manager.FUNCTION_MANAGER.gunNoSpread.forceAimSpread()) {
                LocalPlayer self = Minecraft.getInstance().player;
                if (self != null && living.getUUID().equals(self.getUUID())) {
                    return InaccuracyType.AIM;
                }
            }
        } catch (Throwable ignored) {}
        return original;
    }
}
