package ru.levin.mixin.display;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.Optionull;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.Manager;

import java.util.Comparator;
import java.util.List;

import static ru.levin.manager.IMinecraft.mc;

@Mixin(PlayerTabOverlay.class)
public class MixinPlayerListHud {
    private static final Comparator<PlayerInfo> ENTRY_ORDERING = Comparator.<PlayerInfo>comparingInt(entry -> entry.getGameMode() == GameType.SPECTATOR ? 1 : 0).thenComparing(entry -> Optionull.mapOrDefault(entry.getTeam(), PlayerTeam::getName, "")).thenComparing(entry -> entry.getProfile().getName(), String::compareToIgnoreCase);

    @Inject(method = "getPlayerInfos", at = @At("HEAD"), cancellable = true)
    private void collectPlayerEntriesHook(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        if (mc.player == null || mc.player.connection == null) return;
        int limit = Manager.FUNCTION_MANAGER.extraTab.state ? 200 : 80;
        List<PlayerInfo> list = new java.util.ArrayList<>(mc.player.connection.getListedOnlinePlayers());
        list.sort(ENTRY_ORDERING);
        if (list.size() > limit) {
            list = list.subList(0, limit);
        }
        cir.setReturnValue(list);
    }
}
