package ru.levin.mixin.player;

import io.netty.channel.Channel;
import io.netty.handler.proxy.Socks4ProxyHandler;
import io.netty.handler.proxy.Socks5ProxyHandler;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.manager.proxyManager.Proxy;
import ru.levin.manager.proxyManager.ProxyManager;

import java.net.InetSocketAddress;

@Mixin(targets = "net.minecraft.network.Connection$1")
public class MixinClientConnectionInitMixin {

    @Inject(method = "initChannel(Lio/netty/channel/Channel;)V", at = @At("HEAD"))
    private void connect(Channel channel, CallbackInfo ci) {
        ProxyManager proxyManager = Manager.PROXY_MANAGER;
        Proxy proxy = proxyManager.proxy;

        if (proxyManager.proxyEnabled && proxy != null) {
            proxyManager.lastUsedProxy = proxy;

            if (proxy.type == Proxy.ProxyType.SOCKS5) {
                channel.pipeline().addFirst(new Socks5ProxyHandler(
                        new InetSocketAddress(proxy.getIp(), proxy.getPort()),
                        proxy.username.isEmpty() ? null : proxy.username,
                        proxy.password.isEmpty() ? null : proxy.password
                ));
            } else if (proxy.type == Proxy.ProxyType.SOCKS4) {
                channel.pipeline().addFirst(new Socks4ProxyHandler(
                        new InetSocketAddress(proxy.getIp(), proxy.getPort()),
                        proxy.username.isEmpty() ? null : proxy.username
                ));
            }
        } else {
            proxyManager.lastUsedProxy = new Proxy();
        }

        // proxyMenuButton создаётся только при открытии ванильного экрана Multiplayer
        // (MultiplayerScreenMixin). Если на сервер заходят в обход этого экрана —
        // например через очередь krutqueue (auto-connect с кастомного тайтл-скрина) —
        // кнопка остаётся null. Раньше это бросало NPE прямо в Connection.initChannel,
        // Netty закрывал канал, а ConnectScreen падал на getMessage()==null → вечное
        // «Connecting to the world…». Обновление подписи кнопки чисто косметическое,
        // поэтому просто гейтим его по null.
        if (proxyManager.proxyMenuButton != null) {
            proxyManager.proxyMenuButton.setMessage(Component.literal("Proxy: " + proxyManager.getLastUsedProxyIp()));
        }
    }
}
