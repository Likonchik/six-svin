package ru.levin.util.music;

import com.spotify.Authentication;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import ru.levin.manager.ClientManager;
import xyz.gianlu.librespot.audio.MetadataWrapper;
import xyz.gianlu.librespot.audio.decoders.AudioQuality;
import xyz.gianlu.librespot.core.OAuth;
import xyz.gianlu.librespot.core.Session;
import xyz.gianlu.librespot.mercury.MercuryRequests;
import xyz.gianlu.librespot.metadata.PlayableId;
import xyz.gianlu.librespot.player.Player;
import xyz.gianlu.librespot.player.PlayerConfiguration;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// РЕАЛЬНЫЙ Spotify через librespot-java (Premium). Вход — OAuth один раз (браузер), потом тихо из
// сохранённых credentials. Аудио — встроенный MIXER-синк librespot (свой javax.sound SourceDataLine @44.1кГц),
// работает параллельно lavaplayer-выводу (@48кГц), без общего пайплайна. Всё в try/catch(Throwable):
// без librespot/без логина модуль остаётся жив. credentials.json — это секрет уровня пароля (files/spotify/).
public final class SpotifyPlayer {

    public enum LoginState { LOGGED_OUT, AWAITING_BROWSER, LOGGED_IN, ERROR }

    // redirect, зарегистрированный у keymaster-клиента (его же использует librespot внутри)
    private static final String REDIRECT = "http://127.0.0.1:5588/login";
    private static final Pattern URI_RE = Pattern.compile(
            "open\\.spotify\\.com/(?:intl-[a-z]+/)?(track|playlist|album|episode|show|artist)/([A-Za-z0-9]+)");

    private final File dir;
    private final File credsFile;
    private final File cacheDir;

    private volatile Session session;
    private volatile Player player;
    private volatile LoginState loginState = LoginState.LOGGED_OUT;
    private volatile String error = "";

    // кэш метаданных (пишется в EventsListener, читается HUD-потоком)
    private volatile String title = "";
    private volatile String artist = "";
    private volatile String album = "";
    private volatile long durationMs = 0;
    private volatile boolean trackLoaded = false;

    public SpotifyPlayer() {
        dir = new File(Minecraft.getInstance().gameDirectory, "files/spotify");
        try { dir.mkdirs(); } catch (Throwable ignored) {}
        credsFile = new File(dir, "credentials.json");
        cacheDir = new File(dir, "cache");
        try { cacheDir.mkdirs(); } catch (Throwable ignored) {}
    }

    public LoginState getLoginState() { return loginState; }
    public String getError() { return error; }
    public boolean isLoggedIn() { try { return session != null && session.isValid(); } catch (Throwable t) { return false; } }

    // ВЫЗЫВАТЬ С DAEMON-ПОТОКА: при первом входе oauth блокирует поток до логина в браузере.
    public synchronized void ensureSession() {
        if (session != null) {
            try { if (session.isValid()) return; } catch (Throwable ignored) {}
        }
        try {
            Session.Configuration conf = new Session.Configuration.Builder()
                    .setStoreCredentials(true)
                    .setStoredCredentialsFile(credsFile)
                    .setCacheEnabled(true)
                    .setCacheDir(cacheDir)
                    .build();

            Session.Builder b = new Session.Builder(conf).setDeviceName("OneTap").setPreferredLocale("en");

            if (credsFile.exists()) {
                b.stored();                      // тихий вход из сохранённых credentials, без браузера
            } else {
                loginState = LoginState.AWAITING_BROWSER;
                OAuth oauth = new OAuth(MercuryRequests.KEYMASTER_CLIENT_ID, REDIRECT);
                String url = oauth.getAuthUrl();
                surfaceUrl(url);                 // показать ссылку ДО блокирующего flow()
                Authentication.LoginCredentials creds = oauth.flow(); // блок до логина в браузере
                try { oauth.close(); } catch (Throwable ignored) {}
                b.credentials(creds);
            }

            session = b.create();                // storeCredentials=true -> запишет credentials.json
            loginState = LoginState.LOGGED_IN;
            error = "";
        } catch (Throwable t) {
            loginState = LoginState.ERROR;
            error = "Spotify: вход не удался";
            System.err.println("[MediaPlayer] spotify login failed: " + t);
        }
    }

    public synchronized void ensurePlayer() {
        if (player != null) return;
        if (session == null) return;
        try {
            PlayerConfiguration pconf = new PlayerConfiguration.Builder()
                    .setOutput(PlayerConfiguration.AudioOutput.MIXER)   // встроенный javax.sound синк
                    .setPreferredQuality(AudioQuality.VERY_HIGH)        // Premium 320k
                    .setInitialVolume(Player.VOLUME_MAX)
                    .setEnableNormalisation(true)
                    .setAutoplayEnabled(true)
                    .build();
            player = new Player(pconf, session);
            player.addEventsListener(new Listener());
        } catch (Throwable t) {
            error = "Spotify: плеер не запущен";
            System.err.println("[MediaPlayer] spotify player init failed: " + t);
        }
    }

    public void load(String input, boolean play) {
        ensureSession();
        if (session == null) return;
        ensurePlayer();
        if (player == null) return;
        try {
            player.load(normalize(input), play, false);
            trackLoaded = true;
        } catch (Throwable t) {
            error = "Spotify: не удалось загрузить";
            System.err.println("[MediaPlayer] spotify load failed: " + t);
        }
    }

    public void play() { if (player != null) try { player.play(); } catch (Throwable ignored) {} }
    public void pause() { if (player != null) try { player.pause(); } catch (Throwable ignored) {} }
    public void playPause() { if (player != null) try { player.playPause(); } catch (Throwable ignored) {} }
    public void next() { if (player != null) try { player.next(); } catch (Throwable ignored) {} }
    public void prev() { if (player != null) try { player.previous(); } catch (Throwable ignored) {} }
    public void seek(long ms) { if (player != null) try { player.seek((int) Math.max(0, ms)); } catch (Throwable ignored) {} }

    public void setVolume(int pct) {
        if (player == null) return;
        try { player.setVolume(Math.round(Math.max(0, Math.min(100, pct)) / 100f * Player.VOLUME_MAX)); } catch (Throwable ignored) {}
    }

    public void stop() { pause(); trackLoaded = false; }

    public boolean isActive() { try { return player != null && player.isActive(); } catch (Throwable t) { return false; } }
    public boolean hasTrack() { return trackLoaded; }

    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getAlbum() { return album; }
    public long getDurationMs() { return durationMs; }
    public long getPositionMs() { if (player != null) try { return player.time(); } catch (Throwable ignored) {} return 0; }

    public void shutdown() {
        try { if (player != null) player.close(); } catch (Throwable ignored) {}
        try { if (session != null) session.close(); } catch (Throwable ignored) {}
        player = null;
        session = null;
        loginState = LoginState.LOGGED_OUT;
    }

    // ===== helpers =====

    private void surfaceUrl(String url) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try { mc.keyboardHandler.setClipboard(url); } catch (Throwable ignored) {}
            try { ClientManager.message("§aSpotify: открой ссылку и войди в аккаунт (она скопирована в буфер обмена)"); } catch (Throwable ignored) {}
            try { Util.getPlatform().openUri(url); } catch (Throwable ignored) {}
        });
    }

    private void cache(MetadataWrapper m) {
        if (m == null) return;
        try {
            String n = m.getName(); title = n == null ? "" : n;
            String a = m.getArtist(); artist = a == null ? "" : a;
            String al = m.getAlbumName(); album = al == null ? "" : al;
            durationMs = m.duration();
        } catch (Throwable ignored) {}
    }

    private static String normalize(String s) {
        s = s.trim();
        if (s.startsWith("spotify:")) return s;
        Matcher m = URI_RE.matcher(s);
        if (m.find()) return "spotify:" + m.group(1) + ":" + m.group(2);
        return s;
    }

    // EventsListener: кэшируем метаданные; остальное — пустышки.
    private final class Listener implements Player.EventsListener {
        @Override public void onContextChanged(Player p, String newUri) {}
        @Override public void onTrackChanged(Player p, PlayableId id, MetadataWrapper metadata, boolean userInitiated) { cache(metadata); trackLoaded = true; }
        @Override public void onPlaybackEnded(Player p) {}
        @Override public void onPlaybackPaused(Player p, long trackTime) {}
        @Override public void onPlaybackResumed(Player p, long trackTime) {}
        @Override public void onPlaybackFailed(Player p, Exception e) { error = "Spotify: ошибка воспроизведения"; }
        @Override public void onTrackSeeked(Player p, long trackTime) {}
        @Override public void onMetadataAvailable(Player p, MetadataWrapper metadata) { cache(metadata); }
        @Override public void onPlaybackHaltStateChanged(Player p, boolean halted, long trackTime) {}
        @Override public void onInactiveSession(Player p, boolean timeout) {}
        @Override public void onVolumeChanged(Player p, float volume) {}
        @Override public void onPanicState(Player p) {}
        @Override public void onStartedLoading(Player p) {}
        @Override public void onFinishedLoading(Player p) {}
    }
}
