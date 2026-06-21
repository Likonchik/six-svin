package ru.levin.mixin.player;

import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Scoreboard.class)
public class MixinFixRw {
    @Inject(method = "removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V", at = @At("HEAD"),cancellable = true)
    public void removeScoreHolderFromTeam(String scoreHolderName, PlayerTeam team, CallbackInfo ci) {
        ci.cancel();
    }
}