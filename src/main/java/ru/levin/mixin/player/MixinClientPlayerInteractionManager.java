package ru.levin.mixin.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import ru.levin.manager.Manager;
import ru.levin.util.move.MoveUtil;

@SuppressWarnings("All")
@Mixin(MultiPlayerGameMode.class)
public class MixinClientPlayerInteractionManager {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Redirect(method = "handleInventoryMouseClick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V"))
    private void redirectSendPacket(ClientPacketListener networkHandler, Packet<?> packet) {
        if (packet instanceof ServerboundContainerClickPacket clickPacket) {
            if (Manager.FUNCTION_MANAGER.guiWalk.state && Manager.FUNCTION_MANAGER.guiWalk.bypass.is("FunTime")  && clickPacket.getContainerId() == 0 && MoveUtil.isMoving() && minecraft.screen instanceof AbstractContainerScreen<?>) {
                Manager.FUNCTION_MANAGER.guiWalk.queuePacket(clickPacket);
                return;
            }
        }
        networkHandler.send(packet);
    }
}