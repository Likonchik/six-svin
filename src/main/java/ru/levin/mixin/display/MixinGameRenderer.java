package ru.levin.mixin.display;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.combat.AttackAura;
import ru.levin.modules.combat.CrystalAura;
import ru.levin.modules.combat.SelfTrap;
import ru.levin.modules.combat.rotation.RotationController;
import ru.levin.modules.misc.AntiScreenshot;
import ru.levin.modules.render.AspectRatio;
import ru.levin.util.math.RayTraceUtil;
import ru.levin.util.render.Render3DUtil;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.vector.VectorUtil;

import java.util.function.Predicate;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer implements IMinecraft {

    @Shadow public abstract float getRenderDistance();

    // конец кадра: антискринилка делает отложенный (чистый) захват через пару кадров после F2
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("TAIL"))
    private void onetap$frameEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        AntiScreenshot.onRenderEnd();
    }

    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void renderWorld(DeltaTracker tickCounter, CallbackInfo callbackInfo, @Local(ordinal = 1) Matrix4f matrix4f) {
        PoseStack matrixStack = new PoseStack();
        matrixStack.mulPose(matrix4f);
        Vec3 cameraPos = mc.getEntityRenderDispatcher().camera.getPosition().reverse();
        matrixStack.translate(cameraPos.x, cameraPos.y, cameraPos.z);
        VectorUtil.previousProjectionMatrix = RenderSystem.getProjectionMatrix();
        VectorUtil.lastWorldSpaceMatrix = matrixStack.last();

        Render3DUtil.setLastProjMat(RenderSystem.getProjectionMatrix());
        Render3DUtil.setLastWorldSpaceMatrix(matrixStack.last());
        EventRender3D event = new EventRender3D(matrixStack, tickCounter);
        if (!AntiScreenshot.hiding) Event.call(event);
        Render3DUtil.onWorldRender(event);
    }

    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z", opcode = Opcodes.GETFIELD, ordinal = 0), method = "renderLevel")
    private void render3dHook(DeltaTracker tickCounter, CallbackInfo ci) {
        PoseStack matrixStack = new PoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        RenderSystem.getModelViewStack().pushMatrix().mul(matrixStack.last().pose());
        matrixStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        matrixStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0f));
        // 1.21.1: no Fog record / RenderSystem.setShaderFog(Fog). Push fog out of range to disable it.
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.setShaderFogEnd(Float.MAX_VALUE);
        RenderUtil.render3D.setTranslation(matrixStack);
        if (!AntiScreenshot.hiding) Event.call(new EventRender3D(matrixStack, tickCounter));
        RenderSystem.getModelViewStack().popMatrix();
        RenderSystem.getModelViewMatrix();
    }

    @Inject(method = "getProjectionMatrix", at = @At("TAIL"), cancellable = true)
    private void getBasicProjectionMatrixHook(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        AspectRatio aspectRatio = Manager.FUNCTION_MANAGER.aspectRatio;
        if (aspectRatio.state) {
            float aspect = 1.0f;
            String mode = aspectRatio.mods.get();

            switch (mode) {
                case "4:3":
                    aspect = 4f / 3f;
                    break;
                case "16:9":
                    aspect = 16f / 9f;
                    break;
                case "1:1":
                    aspect = 1f;
                    break;
                case "16:10":
                    aspect = 16f / 10f;
                    break;
                case "Кастомный":
                    aspect = aspectRatio.slider.get().floatValue();
                    break;
            }

            PoseStack matrixStack = new PoseStack();
            matrixStack.last().pose().identity();
            matrixStack.last().pose().mul(new Matrix4f().setPerspective((float) (fov * (Math.PI / 180.0)), aspect, 0.05f, getRenderDistance() * 4.0f));

            cir.setReturnValue(matrixStack.last().pose());
        }
    }


    @Inject(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At("HEAD"), cancellable = true)
    private void onFindCrosshairTarget(Entity camera, double blockRange, double entityRange, float tickDelta, CallbackInfoReturnable<HitResult> cir) {
        AttackAura attackAura = Manager.FUNCTION_MANAGER.attackAura;
        CrystalAura crystalAura = Manager.FUNCTION_MANAGER.crystalAura;
        SelfTrap selfTrap = Manager.FUNCTION_MANAGER.selfTrap;
        RotationController rotation = Manager.ROTATION;

        float yaw;
        float pitch;

        if (attackAura.state || rotation.isControlling()) {
            yaw = rotation.getYaw();
            pitch = rotation.getPitch();
        } else if (crystalAura.state && crystalAura.rotate != null && crystalAura.closestCrystal != null) {
            yaw = crystalAura.rotate.x;
            pitch = crystalAura.rotate.y;
        } else if (selfTrap.state && selfTrap.active) {
            yaw = selfTrap.rotate.x;
            pitch = crystalAura.rotate.y;
        } else {
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
        }

        double maxRange = Math.max(blockRange, entityRange);
        Vec3 cameraPos = camera.getEyePosition(tickDelta);

        EntityHitResult entityHit = RayTraceUtil.rayCastEntity(maxRange, yaw, pitch, e -> !e.isSpectator() && e.isPickable());
        BlockHitResult blockHit = RayTraceUtil.rayCast(maxRange, yaw, pitch, false);
        if (entityHit != null) {
            double entityDistSq = entityHit.getLocation().distanceToSqr(cameraPos);
            double blockDistSq = blockHit != null ? blockHit.getLocation().distanceToSqr(cameraPos) : Double.MAX_VALUE;

            if (entityDistSq <= entityRange * entityRange && entityDistSq < blockDistSq) {
                cir.setReturnValue(entityHit);
                return;
            }
        }

        if (blockHit != null && blockHit.getLocation().distanceToSqr(cameraPos) <= blockRange * blockRange) {
            cir.setReturnValue(blockHit);
        } else {
            cir.setReturnValue(BlockHitResult.miss(cameraPos, null, null));
        }
    }


    @Redirect(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
    private EntityHitResult findCrosshairTarget(Entity entity, Vec3 start, Vec3 end, AABB box, Predicate<Entity> predicate, double maxDistance) {
        if (!Manager.FUNCTION_MANAGER.noRayTrace.state) {
            return ProjectileUtil.getEntityHitResult(entity, start, end, box, predicate, maxDistance);
        }
        return null;
    }


    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurtHook(PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER.noRender.state && Manager.FUNCTION_MANAGER.noRender.mods.get("Тряска камеры"))
            ci.cancel();
    }
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
    private float badEffects(float delta, float first, float second) {
        if (Manager.FUNCTION_MANAGER.noRender.state && Manager.FUNCTION_MANAGER.noRender.mods.get("Плохие эффекты")) return 0;
        return Mth.lerp(delta, first, second);
    }
}
