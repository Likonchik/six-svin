package ru.levin.mixin.player;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.levin.manager.Manager;

// GunNoSpread "Легит" forces the server-synced aiming progress to 1.0 (via ClientMessagePlayerAim) so
// the server applies AIM (low) spread. TACZ's own MouseHandlerMixin reduces mouse sensitivity from that
// SAME synced progress, so our forced aim would also shrink the player's sensitivity. We wrap the same
// LocalPlayer.turn(DD) call OUTSIDE TACZ's wrapper (higher priority) and, while we're force-aiming,
// perform the raw turn directly — bypassing TACZ's ADS sensitivity scaling. The server-side spread
// decision (which reads the synced progress) is untouched. require=0 so a mismatch fails inert.
@Mixin(value = MouseHandler.class, priority = 1500)
public class MixinMouseHandlerNoSens {

    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"),
            require = 0)
    private void onapixTurnPlayer(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
        try {
            if (Manager.FUNCTION_MANAGER != null
                    && Manager.FUNCTION_MANAGER.gunNoSpread != null
                    && Manager.FUNCTION_MANAGER.gunNoSpread.suppressAimSens()) {
                player.turn(yaw, pitch); // raw turn — skip TACZ's ADS sensitivity reduction
                return;
            }
        } catch (Throwable ignored) {}
        original.call(player, yaw, pitch);
    }
}
