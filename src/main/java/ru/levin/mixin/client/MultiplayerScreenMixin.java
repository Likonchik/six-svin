package ru.levin.mixin.client;

import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.manager.proxyManager.GuiProxy;
import ru.levin.manager.proxyManager.Proxy;
import ru.levin.manager.proxyManager.ProxyManager;
import ru.levin.mixin.iface.ScreenAccessor;

@Mixin(JoinMultiplayerScreen.class)
public class MultiplayerScreenMixin {
    @Inject(method = "init()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/multiplayer/JoinMultiplayerScreen;onSelectedChange()V"))
    public void multiplayerGuiOpen(CallbackInfo ci) {
        ProxyManager pm = Manager.PROXY_MANAGER;
        String playerName = pm.mc.getUser().getName();

        if (!playerName.equals(pm.lastPlayerName)) {
            pm.lastPlayerName = playerName;
            pm.proxy = pm.accounts.getOrDefault(playerName, pm.accounts.getOrDefault("", new Proxy()));
        }

        JoinMultiplayerScreen screen = (JoinMultiplayerScreen) (Object) this;
        pm.proxyMenuButton = Button.builder(Component.literal("Прокси: " + pm.getLastUsedProxyIp()), b -> pm.mc.setScreen(new GuiProxy(screen)))
                .bounds(screen.width - 320, 479, 100, 20).build();

        ScreenAccessor sa = (ScreenAccessor) screen;
        sa.getDrawables().add(pm.proxyMenuButton);
        sa.getSelectables().add(pm.proxyMenuButton);
        sa.getChildren().add(pm.proxyMenuButton);
    }
}