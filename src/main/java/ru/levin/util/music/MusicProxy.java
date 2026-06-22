package ru.levin.util.music;

import ru.levin.manager.proxyManager.ProxyManager;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

// Сетевые запросы плеера для МЕТАДАННЫХ (Spotify oEmbed, обложки) с опциональной маршрутизацией через
// клиентский SOCKS-прокси.
//
// ВАЖНО (ограничение v1): здесь проксируются только METADATA-запросы (URLConnection). Сам аудио-стрим
// lavaplayer (Apache HttpClient 4) в v1 НЕ проксируется — для этого нужен кастомный hc4 SOCKS
// ConnectionSocketFactory (+SSL), это вынесено в отдельную фазу. Поэтому при включённом прокси
// IP всё ещё виден аудио-серверам (YouTube/SoundCloud). См. docs/musicplayer-design.md (риск №1).
//
// Используем URLConnection.openConnection(Proxy), а НЕ java.net.http.HttpClient — последний молча
// игнорирует SOCKS-прокси (JDK-8214516) и пошёл бы напрямую.
public final class MusicProxy {

    // выставляется модулем из настройки "Метаданные через прокси"
    public static volatile boolean routeMusic = false;

    private MusicProxy() {}

    private static Proxy socksProxy() {
        try {
            if (!routeMusic || !ProxyManager.proxyEnabled) return Proxy.NO_PROXY;
            ru.levin.manager.proxyManager.Proxy p = ProxyManager.proxy;
            if (p == null || p.ipPort == null || p.ipPort.isEmpty() || !p.ipPort.contains(":")) return Proxy.NO_PROXY;
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(p.getIp(), p.getPort()));
        } catch (Throwable t) {
            return Proxy.NO_PROXY;
        }
    }

    // простой GET, возвращает тело как строку или null при любой ошибке. Через SOCKS, если включено.
    public static String httpGet(String urlStr) {
        byte[] b = httpBytes(urlStr);
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    // GET сырых байт (для обложек). Через тот же SOCKS, что и метаданные. null при ошибке.
    public static byte[] httpBytes(String urlStr) {
        try {
            URL url = URI.create(urlStr).toURL();
            Proxy proxy = socksProxy();
            URLConnection con = (proxy == Proxy.NO_PROXY) ? url.openConnection() : url.openConnection(proxy);
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (OneTap MediaPlayer)");
            con.setRequestProperty("Accept", "*/*");
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            try (InputStream in = con.getInputStream()) {
                return in.readAllBytes();
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
