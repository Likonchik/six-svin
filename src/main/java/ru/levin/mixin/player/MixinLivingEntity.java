package ru.levin.mixin.player;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.combat.AttackAura;
import ru.levin.modules.combat.AutoExplosion;
import ru.levin.modules.combat.CrystalAura;
import ru.levin.modules.combat.rotation.RotationController;
import ru.levin.modules.render.SwingAnimations;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements IMinecraft {
    // Yarn getHandSwingDuration -> Mojmap getCurrentSwingDuration (private in 1.21.1; mixin can target it).
    @Inject(method = "getCurrentSwingDuration", at = {@At("HEAD")}, cancellable = true)
    private void getArmSwingAnimationEnd(final CallbackInfoReturnable<Integer> info) {
        SwingAnimations swingAnimations = Manager.FUNCTION_MANAGER.swingAnimations;
        if (swingAnimations.state && swingAnimations.slowAnimation.get()) {
            info.setReturnValue(swingAnimations.slowAnimationSpeed.get().intValue());
        }
    }

    @Shadow
    protected abstract float getJumpPower();

    // Yarn jump -> Mojmap jumpFromGround
    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void onJump(CallbackInfo ci) {
        if ((Object) this != mc.player) return;

        AttackAura attackAura = Manager.FUNCTION_MANAGER.attackAura;
        AutoExplosion autoExplosion = Manager.FUNCTION_MANAGER.autoExplosion;
        RotationController rotationController = Manager.ROTATION;
        CrystalAura crystalAura = Manager.FUNCTION_MANAGER.crystalAura;
        Float yaw = null;
        if ((attackAura.state && attackAura.correction.get()) || rotationController.isControlling()) {
            yaw = rotationController.getYaw();
        } else if (autoExplosion.check()) {
            yaw = autoExplosion.serverRot.x;
        } else if (crystalAura.check()) {
            yaw = crystalAura.rotate.x;
        }

        if (yaw == null) {
            return;
        }

        float jumpVelocity = getJumpPower();
        if (jumpVelocity <= 1.0E-5F) {
            ci.cancel();
            return;
        }

        Vec3 currentVelocity = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(currentVelocity.x, Math.max(jumpVelocity, currentVelocity.y), currentVelocity.z);

        if (mc.player.isSprinting()) {
            float yawRad = yaw * ((float) Math.PI / 180.0F);
            double x = -Mth.sin(yawRad) * 0.2;
            double z = Mth.cos(yawRad) * 0.2;
            mc.player.addDeltaMovement(new Vec3(x, 0.0, z));
        }

        mc.player.hasImpulse = true;
        ci.cancel();
    }

    // Elytra glide steering (1.21.1 has no calcGlidingVelocity — that was 1.21.2). Redirect the single
    // getLookAngle() call inside the fall-flying branch of travel() toward the rotation-controller's aim,
    // so vanilla's elytra physics steer the glide toward the ElytraTarget/AttackAura target.
    @ModifyExpressionValue(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 onElytraGlideLook(Vec3 original) {
        if ((Object) this != mc.player) return original;
        AttackAura attackAura = Manager.FUNCTION_MANAGER.attackAura;
        RotationController rotationController = Manager.ROTATION;
        if ((attackAura.state && attackAura.correction.get()) || rotationController.isControlling()) {
            return Vec3.directionFromRotation(rotationController.getPitch(), rotationController.getYaw());
        }
        return original;
    }
}
