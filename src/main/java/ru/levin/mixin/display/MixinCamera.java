package ru.levin.mixin.display;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.movement.freelook.CameraOverriddenEntity;
import ru.levin.modules.movement.freelook.FreeLookState;

@Mixin(Camera.class)
public abstract class MixinCamera implements IMinecraft {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Unique
    private boolean initialized = false;
    @Shadow
    private boolean detached;
    @Inject(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", shift = At.Shift.AFTER))
    private void onUpdate(CallbackInfo ci) {
        if (!FreeLookState.active || !(mc.player instanceof CameraOverriddenEntity entity))
            return;

        if (!initialized) {
            entity.setCameraPitch(mc.player.getXRot());
            entity.setCameraYaw(mc.player.getYRot());
            initialized = true;
        }

        setRotation(entity.getCameraYaw(), entity.getCameraPitch());
    }

    @Inject(method = "setup", at = @At("TAIL"))
    private void updateHook(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER.freeCamera.state) {
            this.detached = true;
        }
    }
    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void setRotationHook(Args args) {
        if(Manager.FUNCTION_MANAGER.freeCamera.state)
            args.setAll(Manager.FUNCTION_MANAGER.freeCamera.getFakeYaw(), Manager.FUNCTION_MANAGER.freeCamera.getFakePitch());
    }

    @ModifyArgs(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void setPosHook(Args args) {
        if(Manager.FUNCTION_MANAGER.freeCamera.state)
            args.setAll(Manager.FUNCTION_MANAGER.freeCamera.getFakeX(), Manager.FUNCTION_MANAGER.freeCamera.getFakeY(), Manager.FUNCTION_MANAGER.freeCamera.getFakeZ());
    }
}