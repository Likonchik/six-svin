package ru.levin.mixin.attack;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.events.Event;
import ru.levin.events.impl.player.EventAttack;
import ru.levin.manager.Manager;
import ru.levin.util.math.RayTraceUtil;

@Mixin(MultiPlayerGameMode.class)
public class MixinAttackPlayer {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    public void attackEntity(Player player, Entity target, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER.noFriendDamage.state) {
            if (Manager.FRIEND_MANAGER.isFriend(target.getName().getString())) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", shift = At.Shift.AFTER, ordinal = 0))
    private void afterSendPacket(Player player, Entity target, CallbackInfo ci) {
        Event.call(new EventAttack(player,target));
        RayTraceUtil.markHit(target);
    }
}