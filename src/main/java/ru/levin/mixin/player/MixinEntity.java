package ru.levin.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.combat.*;
import ru.levin.modules.combat.rotation.RotationController;
import ru.levin.modules.movement.freelook.CameraOverriddenEntity;
import ru.levin.modules.movement.freelook.FreeLookState;
import ru.levin.modules.player.NoPush;
import ru.levin.modules.render.ESP;
import ru.levin.modules.render.Trails;
import ru.levin.util.IEntity;
import ru.levin.util.player.AuraUtil;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("All")
@Mixin(Entity.class)
public abstract class MixinEntity implements IEntity, CameraOverriddenEntity, IMinecraft {

    // ESP «Обводка модели»: цвет ванильной обводки берётся из getTeamColor() — подменяем на цвет темы.
    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void onGetTeamColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (ESP.isOutlineTarget(self)) {
            cir.setReturnValue(ESP.getOutlineColor(self));
        }
    }

    @Unique
    private float cameraYaw;
    @Unique
    private float cameraPitch;

    @Unique
    private List<Trails.Trail> trails = new ArrayList<>();
    @Unique
    private Vec3 lastTrailPos;

    @Shadow
    private AABB bb;

    @Shadow
    protected static Vec3 getInputVector(Vec3 movementInput, float speed, float yaw) {
        double d = movementInput.lengthSqr();
        if (d < 1.0E-7) {
            return Vec3.ZERO;
        } else {
            Vec3 vec3d = (d > 1.0F ? movementInput.normalize() : movementInput).scale(speed);
            float sin = Mth.sin(yaw * ((float) Math.PI / 180F));
            float cos = Mth.cos(yaw * ((float) Math.PI / 180F));
            return new Vec3(vec3d.x * cos - vec3d.z * sin, vec3d.y, vec3d.z * cos + vec3d.x * sin);
        }
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onChangeLookDirection(double deltaX, double deltaY, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (FreeLookState.active && self instanceof LocalPlayer) {
            cameraYaw += (float) deltaX * 0.15F;
            cameraPitch = Mth.clamp(cameraPitch + (float) deltaY * 0.15F, -90.0F, 90.0F);
            ci.cancel();
        }
    }

    @Override
    public float getCameraPitch() {
        return cameraPitch;
    }

    @Override
    public float getCameraYaw() {
        return cameraYaw;
    }

    @Override
    public void setCameraPitch(float pitch) {
        cameraPitch = pitch;
    }

    @Override
    public void setCameraYaw(float yaw) {
        cameraYaw = yaw;
    }

    @ModifyArgs(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void pushAwayFromHook(Args args) {
        Entity self = (Entity)(Object)this;
        NoPush noPush = Manager.FUNCTION_MANAGER.noPush;
        if (self == mc.player && noPush.state && noPush.mods.get("Игроки")) {
            args.set(0, 0d);
            args.set(1, 0d);
            args.set(2, 0d);
        }
    }

    @Override
    public List<Trails.Trail> exosWareFabric1_21_4$getTrails() {
        return trails;
    }

    @Override
    public Vec3 exosWareFabric1_21_4$getLastTrailPos() {
        return lastTrailPos;
    }

    @Override
    public void exosWareFabric1_21_4$setLastTrailPos(Vec3 pos) {
        this.lastTrailPos = pos;
    }

    // Removed for 1.21.1: Entity#move() no longer calls isControlledByLocalInstance(), so there is no valid injection target.

    @Inject(method = "getBoundingBox", at = @At("RETURN"), cancellable = true)
    private void getBoundingBox(CallbackInfoReturnable<AABB> cir) {
        Entity self = (Entity)(Object)this;
        HitBox hitBox = Manager.FUNCTION_MANAGER.xbox;
        if (hitBox.state && mc != null && mc.player != null && self.getId() != mc.player.getId()) {
            float halfSize = hitBox.size.get().floatValue() / 2f;
            AABB expanded = new AABB(bb.minX - halfSize, bb.minY - halfSize, bb.minZ - halfSize, bb.maxX + halfSize, bb.maxY + halfSize, bb.maxZ + halfSize);
            cir.setReturnValue(expanded);
        }
    }

    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void onUpdateVelocity(float speed, Vec3 movementInput, CallbackInfo ci) {
        Entity self = (Entity)(Object)this;
        if (self != mc.player) return;

        Vec3 customVelocity = null;

        AttackAura attackAura = Manager.FUNCTION_MANAGER.attackAura;
        AutoExplosion autoExplosion = Manager.FUNCTION_MANAGER.autoExplosion;
        CrystalAura crystalAura = Manager.FUNCTION_MANAGER.crystalAura;
        TargetStrafe targetStrafe = Manager.FUNCTION_MANAGER.targetStrafe;
        RotationController rotationController = Manager.ROTATION;

        boolean correcting = (attackAura.state && attackAura.correction.get()) || rotationController.isControlling();
        if (correcting) {
            float yaw = rotationController.getYaw();

            if (targetStrafe.state && attackAura.target != null) {
                Entity target = attackAura.target;

                boolean nearTarget = mc.player.getBoundingBox().inflate(targetStrafe.blocks.get().floatValue()).intersects(target.getBoundingBox());
                boolean insideHitbox = mc.player.getBoundingBox().inflate(targetStrafe.hitbox.get().floatValue()).intersects(target.getBoundingBox());

                if (nearTarget) {
                    if (insideHitbox) {
                        customVelocity = AuraUtil.getVelocityTowards(target, targetStrafe.speedSlider.get().floatValue(), false, targetStrafe.predictView.get());
                    } else {
                        if (targetStrafe.ptytag.is("Motion / Velocity")) {
                            mc.options.keyUp.setDown(true);
                            customVelocity = getInputVector(movementInput, speed, yaw);
                        } else {
                            customVelocity = AuraUtil.getVelocityTowards(target, 0.08f, false, targetStrafe.predictView.get());
                        }
                    }
                } else {
                    customVelocity = getInputVector(movementInput, speed, yaw);
                }
            } else {
                customVelocity = getInputVector(movementInput, speed, yaw);
            }
        }
        else if (autoExplosion.check()) {
            customVelocity = getInputVector(movementInput, speed, autoExplosion.serverRot.x);
        }
        else if (crystalAura.check()) {
            customVelocity = getInputVector(movementInput, speed, crystalAura.rotate.x);
        }

        if (customVelocity != null) {
            self.setDeltaMovement(self.getDeltaMovement().add(customVelocity));
            ci.cancel();
        }
    }

}
