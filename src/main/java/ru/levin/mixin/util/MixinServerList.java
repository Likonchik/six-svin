package ru.levin.mixin.util;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.ClientManager;

import java.util.List;
@Mixin(ServerList.class)
public class MixinServerList {
    @Unique
    private final List<ServerData> serverTop = List.of(
            new ServerData("Лучший HvH сервер!", "mc.furryhvh.ru", ServerData.Type.OTHER)
    );

    @Shadow @Final private List<ServerData> serverList;
    @Shadow @Final private List<ServerData> hiddenServerList;

    @Inject(method = "load", at = @At(value = "FIELD", target = "Lnet/minecraft/client/multiplayer/ServerList;hiddenServerList:Ljava/util/List;", ordinal = 0))
    private void loadFileHook(CallbackInfo ci) {
        if (ClientManager.legitMode) {
            serverList.removeIf(si -> serverTop.stream().anyMatch(top -> top.ip.equals(si.ip)));
        } else {
            for (ServerData top : serverTop) {
                boolean exists = serverList.stream().anyMatch(si -> si.ip.equals(top.ip));
                if (!exists) serverList.add(top);
            }
        }
        removeDuplicates();
    }

    @Unique
    private void removeDuplicates() {
        removeDuplicatesFromList(serverList);
        removeDuplicatesFromList(hiddenServerList);
    }
    @Unique
    private void removeDuplicatesFromList(List<ServerData> list) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        list.removeIf(si -> !seen.add(si.ip));
    }

    @Redirect(method = "save", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/ListTag;add(Ljava/lang/Object;)Z", ordinal = 0))
    private boolean saveFileHook(ListTag instance, Object o, @Local(ordinal = 0) ServerData info) {
        if (!ClientManager.legitMode && serverTop.stream().anyMatch(top -> top.ip.equals(info.ip))) {
            return true;
        }
        return instance.add((Tag) o);
    }
}