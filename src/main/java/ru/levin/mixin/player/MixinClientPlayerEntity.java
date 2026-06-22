package ru.levin.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.move.EventNoSlow;
import ru.levin.events.impl.player.EventSprint;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;

@Mixin(LocalPlayer.class)
public abstract class MixinClientPlayerEntity implements IMinecraft {

    @Shadow
    public abstract void move(MoverType type, Vec3 movement);

    @Unique
    private float preYaw;
    @Unique
    private float prePitch;
    @Unique
    private float packetYaw;
    @Unique
    private float packetPitch;
    @Unique
    private boolean preOnGround;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        Event.call(new EventUpdate());
        preYaw = mc.player.getYRot();
        prePitch = mc.player.getXRot();

    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        EventMotion event = new EventMotion( mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround());
        preOnGround = mc.player.onGround();
        Event.call(event);

        if (event.isCancel()) {
            ci.cancel();
            return;
        }
        mc.player.setYRot(event.getYaw());
        mc.player.setXRot(event.getPitch());
        mc.player.setOnGround(event.isOnGround());
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;sendPosition()V", shift = At.Shift.AFTER))
    private void afterSendMovementPackets(CallbackInfo ci) {
        packetYaw = mc.player.getYRot();
        packetPitch = mc.player.getXRot();
        mc.player.setYRot(preYaw);
        mc.player.setXRot(prePitch);
        mc.player.setOnGround(preOnGround);

        // GunAimbot стреляет ИМЕННО здесь: move-пакет с наведённой (silent) ротацией уже ушёл на сервер
        // этим же тиком, поэтому сервер разрешит выстрел по наведённому углу, а не по ротации прошлого тика.
        if (Manager.FUNCTION_MANAGER != null && Manager.FUNCTION_MANAGER.gunAimbot != null) {
            Manager.FUNCTION_MANAGER.gunAimbot.flushPendingShot();
        }
    }

    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void onPushOutOfBlocksHook(double x, double d, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER.noPush.state && Manager.FUNCTION_MANAGER.noPush.mods.get("Блоки")) {
            ci.cancel();
        }
    }

    private boolean checkNoSlowCancel() {
        EventNoSlow eventNoSlow = new EventNoSlow();
        Event.call(eventNoSlow);
        return eventNoSlow.isCancel();
    }

    @Redirect(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/Input;leftImpulse:F", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void redirectMovementSideways(Input input, float value) {
        if (!checkNoSlowCancel()) {
            input.leftImpulse = value;
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "FIELD", target = "Lnet/minecraft/client/player/Input;forwardImpulse:F", opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void redirectMovementForward(Input input, float value) {
        if (!checkNoSlowCancel()) {
            input.forwardImpulse = value;
        }
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;setSprinting(Z)V", ordinal = 0))
    private void redirectSetSprinting(LocalPlayer player, boolean sprinting) {
        if (!checkNoSlowCancel()) {
            player.setSprinting(sprinting);
        }
    }
    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/KeyMapping;isDown()Z"))
    private boolean hookSprintStart(boolean original) {
        var event = new EventSprint(original);
        return event.isSprinting();
    }

    @ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;canStartSprinting()Z"))
    private boolean hookSprintStop(boolean original) {
        var event = new EventSprint(original);
        Event.call(event);
        return event.isSprinting();
    }
}
