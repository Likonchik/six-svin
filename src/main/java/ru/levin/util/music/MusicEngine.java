package ru.levin.util.music;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;

// Провайдер-агностичный оркестратор + владелец lavaplayer. Весь доступ к lavaplayer в try/catch(Throwable).
// Очередь+история — в одном Playlist (index идёт вперёд next / назад prev); play-now добавляет с прыжком
// (история сохраняется), queue добавляет в конец, play-next вставляет после текущего. Авто-переход — по
// TrackEndEvent. HUD-геттеры держат снапшот последнего трека (на случай, если getPlayingTrack() == null).
public final class MusicEngine {

    public enum LoadMode { PLAY_NOW, QUEUE, PLAY_NEXT }

    private AudioPlayerManager manager;
    private AudioPlayer player;
    private AudioOutput output;
    private final Playlist playlist = new Playlist();

    private volatile boolean initialized = false;
    private volatile boolean userPaused = false;
    private volatile boolean spotifyMirror = false;

    public enum ActiveSource { NONE, LAVA, SPOTIFY }
    private volatile ActiveSource active = ActiveSource.NONE;
    private volatile SpotifyPlayer spotify;
    private volatile boolean spotifyGated = false;

    private volatile MusicState state = MusicState.IDLE;
    private volatile String errorMessage = "";

    // снапшот текущего трека — HUD читает его, когда getPlayingTrack()==null (пауза/после остановки)
    private volatile String lastTitle = "";
    private volatile String lastArtist = "";
    private volatile String lastArtworkUrl = "";
    private volatile String lastProvider = "";
    private volatile long lastDurationMs = 0;

    private volatile BooleanSupplier hardMute;
    private volatile DoubleSupplier gainSupplier;
    private volatile boolean autoplay = true;
    private volatile boolean loop = false;

    public void setSuppliers(BooleanSupplier hardMute, DoubleSupplier gain) {
        this.hardMute = hardMute;
        this.gainSupplier = gain;
    }

    public void setAutoplay(boolean v) { this.autoplay = v; }
    public void setLoop(boolean v) { this.loop = v; }

    public synchronized void ensureInit() {
        if (initialized) return;
        try {
            LavaNatives.setup();
            manager = new DefaultAudioPlayerManager();
            manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_PCM_S16_LE);
            // ВАЖНО: иначе lavaplayer через ~60с глушит ПОСТАВЛЕННЫЙ НА ПАУЗУ трек (cleanup по lastRequestTime),
            // getPlayingTrack() становится null и HUD «пропадает». Отключаем уборщик.
            manager.setPlayerCleanupThreshold(Long.MAX_VALUE);

            try {
                manager.registerSourceManager(new dev.lavalink.youtube.YoutubeAudioSourceManager());
            } catch (Throwable t) {
                System.err.println("[MediaPlayer] YouTube source unavailable: " + t);
            }
            AudioSourceManagers.registerRemoteSources(manager);
            AudioSourceManagers.registerLocalSource(manager);

            player = manager.createPlayer();
            player.addListener(new AudioEventAdapter() {
                @Override
                public void onTrackEnd(AudioPlayer p, AudioTrack track, AudioTrackEndReason endReason) {
                    if (endReason != null && endReason.mayStartNext) advanceAuto();
                }
            });

            output = new AudioOutput(
                    player,
                    () -> userPaused || (hardMute != null && hardMute.getAsBoolean()),
                    () -> gainSupplier != null ? gainSupplier.getAsDouble() : 1.0
            );
            output.start();
            initialized = true;
        } catch (Throwable t) {
            setError("Не удалось запустить плеер");
            System.err.println("[MediaPlayer] engine init failed: " + t);
        }
    }

    // ===== загрузка =====

    public void load(String raw, String sourceMode) { load(raw, sourceMode, LoadMode.PLAY_NOW); }
    public void enqueue(String raw, String src) { load(raw, src, LoadMode.QUEUE); }
    public void playNext(String raw, String src) { load(raw, src, LoadMode.PLAY_NEXT); }

    public void load(String raw, String sourceMode, LoadMode mode) {
        if (raw == null || raw.isBlank()) return;
        final String input = raw.trim();
        setState(MusicState.LOADING);
        errorMessage = "";
        Thread w = new Thread(() -> {
            try {
                if (SpotifyResolver.isSpotify(input)) {
                    // Spotify: реального аудио пока нет (librespot не грузится в NeoForge). Зеркалим на YT Music.
                    ensureInit();
                    if (player == null) { setError("Плеер не инициализирован"); return; }
                    pauseSpotify();
                    active = ActiveSource.LAVA;
                    String q = SpotifyResolver.resolveQuery(input);
                    if (q == null) { setError("Spotify: не удалось получить трек"); return; }
                    spotifyMirror = true;
                    doLoad("ytmsearch:" + q, mode);
                    return;
                }
                ensureInit();
                if (player == null) { setError("Плеер не инициализирован"); return; }
                pauseSpotify();
                active = ActiveSource.LAVA;
                spotifyMirror = false;
                String identifier;
                if (input.startsWith("http://") || input.startsWith("https://")) identifier = input;
                else identifier = searchPrefix(sourceMode) + input;
                doLoad(identifier, mode);
            } catch (Throwable t) {
                setError("Ошибка загрузки");
            }
        }, "OneTap-MusicLoad");
        w.setDaemon(true);
        w.start();
    }

    private void pauseSpotify() { if (spotify != null) spotify.pause(); }

    public void spotifyLogin() {
        if (spotify == null) spotify = new SpotifyPlayer();
        final SpotifyPlayer sp = spotify;
        Thread t = new Thread(() -> { try { sp.ensureSession(); sp.ensurePlayer(); } catch (Throwable ignored) {} }, "OneTap-SpotifyLogin");
        t.setDaemon(true);
        t.start();
    }

    public void applySpotifyGate(boolean mute) {
        if (active != ActiveSource.SPOTIFY || spotify == null) return;
        if (mute != spotifyGated) { spotifyGated = mute; if (mute) spotify.pause(); else spotify.play(); }
    }

    public void setVolumePercent(int pct) {
        if (active == ActiveSource.SPOTIFY && spotify != null) spotify.setVolume(pct);
    }

    public boolean isSpotifyLoggedIn() { return spotify != null && spotify.isLoggedIn(); }

    private void doLoad(String identifier, LoadMode mode) {
        try {
            manager.loadItem(identifier, new AudioLoadResultHandler() {
                @Override public void trackLoaded(AudioTrack track) { applyLoad(track, mode); }
                @Override public void playlistLoaded(AudioPlaylist pl) {
                    if (pl.isSearchResult()) {
                        AudioTrack sel = pl.getSelectedTrack();
                        if (sel == null && !pl.getTracks().isEmpty()) sel = pl.getTracks().get(0);
                        if (sel != null) applyLoad(sel, mode);
                        else setError("Ничего не найдено");
                    } else {
                        if (pl.getTracks().isEmpty()) { setError("Пустой плейлист"); return; }
                        if (mode == LoadMode.PLAY_NOW) { playlist.setAll(pl.getTracks()); playCurrent(); }
                        else {
                            playlist.addAll(pl.getTracks());
                            if (player.getPlayingTrack() == null) playCurrent();
                            else setState(userPaused ? MusicState.PAUSED : MusicState.PLAYING);
                        }
                    }
                }
                @Override public void noMatches() { setError("Ничего не найдено"); }
                @Override public void loadFailed(FriendlyException e) { setError("Источник недоступен"); }
            });
        } catch (Throwable t) {
            setError("Источник недоступен");
        }
    }

    private void applyLoad(AudioTrack t, LoadMode mode) {
        switch (mode) {
            case QUEUE -> {
                playlist.add(t);
                if (player.getPlayingTrack() == null) playCurrent();
                else setState(userPaused ? MusicState.PAUSED : MusicState.PLAYING);
            }
            case PLAY_NEXT -> {
                playlist.insertNext(t);
                if (player.getPlayingTrack() == null) playCurrent();
                else setState(userPaused ? MusicState.PAUSED : MusicState.PLAYING);
            }
            default -> { // PLAY_NOW: append + jump (история сохраняется -> prev вернётся к прошлым)
                playlist.addJump(t);
                playCurrent();
            }
        }
    }

    private void playCurrent() {
        AudioTrack t = playlist.current();
        if (t == null) { setState(MusicState.ENDED); return; }
        try {
            userPaused = false;
            startTrack(t);
            errorMessage = "";
        } catch (Throwable e) {
            setError("Не удалось воспроизвести");
        }
    }

    private void advanceAuto() {
        try {
            if (!autoplay) { setState(MusicState.ENDED); return; }
            if (loop) {
                AudioTrack t = playlist.current();
                if (t != null) { startTrack(t); return; }
            }
            AudioTrack nx = playlist.next(false);
            if (nx != null) startTrack(nx);
            else setState(MusicState.ENDED);
        } catch (Throwable e) {
            setError("Ошибка перехода");
        }
    }

    // единая точка старта трека: играем клон + снимаем снапшот метаданных для HUD
    private void startTrack(AudioTrack t) {
        player.playTrack(t.makeClone());
        snapshot(t);
        setState(MusicState.PLAYING);
    }

    private void snapshot(AudioTrack t) {
        try {
            var info = t.getInfo();
            lastTitle = info.title == null ? "" : info.title;
            lastArtist = info.author == null ? "" : info.author;
            lastArtworkUrl = artworkOf(t);
            lastDurationMs = (info.length == Long.MAX_VALUE) ? 0L : info.length;
            lastProvider = providerLabelOf(t);
        } catch (Throwable ignored) {}
    }

    // ===== транспорт =====

    public void playPause() {
        if (active == ActiveSource.SPOTIFY && spotify != null) {
            spotify.playPause();
            setState(state == MusicState.PLAYING ? MusicState.PAUSED : MusicState.PLAYING);
            return;
        }
        if (player == null) return;
        try {
            if (player.getPlayingTrack() == null) {
                AudioTrack t = playlist.current();
                if (t != null) playCurrent();
                return;
            }
            userPaused = !userPaused;
            setState(userPaused ? MusicState.PAUSED : MusicState.PLAYING);
        } catch (Throwable ignored) {}
    }

    public void next() {
        if (active == ActiveSource.SPOTIFY && spotify != null) { spotify.next(); return; }
        if (player == null) return;
        try {
            AudioTrack t = playlist.next(loop);
            if (t != null) { userPaused = false; startTrack(t); }
        } catch (Throwable ignored) {}
    }

    public void prev() {
        if (active == ActiveSource.SPOTIFY && spotify != null) { spotify.prev(); return; }
        if (player == null) return;
        try {
            AudioTrack t = playlist.prev();
            if (t != null) { userPaused = false; startTrack(t); }
        } catch (Throwable ignored) {}
    }

    public void stop() {
        if (spotify != null) spotify.stop();
        active = ActiveSource.NONE;
        if (player == null) return;
        try { player.stopTrack(); playlist.clear(); userPaused = false; setState(MusicState.IDLE); } catch (Throwable ignored) {}
    }

    public void seek(long ms) {
        if (active == ActiveSource.SPOTIFY && spotify != null) { spotify.seek(ms); return; }
        try {
            AudioTrack t = player != null ? player.getPlayingTrack() : null;
            if (t != null && t.isSeekable()) t.setPosition(Math.max(0, ms));
        } catch (Throwable ignored) {}
    }

    public boolean isSeekable() {
        if (active == ActiveSource.SPOTIFY) return true;
        try {
            AudioTrack t = player != null ? player.getPlayingTrack() : null;
            return t != null && t.isSeekable();
        } catch (Throwable t) { return false; }
    }

    // ===== геттеры для HUD (с фолбэком на снапшот) =====

    public MusicState getState() { return state; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isUserPaused() { return userPaused; }

    private AudioTrack playing() {
        try { return player != null ? player.getPlayingTrack() : null; } catch (Throwable t) { return null; }
    }

    public String getTitle() {
        if (active == ActiveSource.SPOTIFY && spotify != null) return spotify.getTitle();
        AudioTrack t = playing();
        if (t == null) return lastTitle;
        try { String s = t.getInfo().title; return s == null ? lastTitle : s; } catch (Throwable e) { return lastTitle; }
    }

    public String getArtist() {
        if (active == ActiveSource.SPOTIFY && spotify != null) return spotify.getArtist();
        AudioTrack t = playing();
        if (t == null) return lastArtist;
        try { String s = t.getInfo().author; return s == null ? lastArtist : s; } catch (Throwable e) { return lastArtist; }
    }

    public String getArtworkUrl() {
        if (active == ActiveSource.SPOTIFY) return lastArtworkUrl;
        AudioTrack t = playing();
        if (t == null) return lastArtworkUrl;
        String s = artworkOf(t);
        return s.isBlank() ? lastArtworkUrl : s;
    }

    // обложка: artworkUrl трека, а для YouTube (где дефолтные клиенты его не ставят) — строим из id видео
    private static String artworkOf(AudioTrack t) {
        try {
            var info = t.getInfo();
            if (info.artworkUrl != null && !info.artworkUrl.isBlank()) return info.artworkUrl;
            String src = t.getSourceManager() != null ? t.getSourceManager().getSourceName() : "";
            if ("youtube".equals(src) && info.identifier != null && !info.identifier.isBlank())
                return "https://i.ytimg.com/vi/" + info.identifier + "/hqdefault.jpg";
        } catch (Throwable ignored) {}
        return "";
    }

    // список названий очереди (для меню) и переход к треку по индексу
    public List<String> getQueueTitles() {
        List<String> out = new ArrayList<>();
        try {
            for (AudioTrack t : playlist.snapshot()) {
                String s = "";
                try { s = t.getInfo().title; } catch (Throwable ignored) {}
                out.add(s == null ? "" : s);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    public void jumpTo(int index) {
        try {
            playlist.setIndex(index);
            AudioTrack t = playlist.current();
            if (t != null) { userPaused = false; startTrack(t); }
        } catch (Throwable ignored) {}
    }

    // удалить трек из очереди по индексу (текущий играющий не прерывается)
    public void removeAt(int index) {
        try { playlist.remove(index); } catch (Throwable ignored) {}
    }

    public long getPositionMs() {
        if (active == ActiveSource.SPOTIFY && spotify != null) return spotify.getPositionMs();
        AudioTrack t = playing();
        try { return t != null ? t.getPosition() : 0L; } catch (Throwable e) { return 0L; }
    }

    public long getDurationMs() {
        if (active == ActiveSource.SPOTIFY && spotify != null) return spotify.getDurationMs();
        AudioTrack t = playing();
        if (t == null) return lastDurationMs;
        try { long d = t.getDuration(); return (d == Long.MAX_VALUE) ? 0L : d; } catch (Throwable e) { return lastDurationMs; }
    }

    public String getProviderLabel() {
        if (spotifyMirror) return "SP→YTM";
        AudioTrack t = playing();
        if (t == null) return lastProvider;
        return providerLabelOf(t);
    }

    private static String providerLabelOf(AudioTrack t) {
        try {
            if (t == null || t.getSourceManager() == null) return "";
            String src = t.getSourceManager().getSourceName();
            if (src == null) return "";
            switch (src) {
                case "youtube": return "YT";
                case "soundcloud": return "SC";
                case "bandcamp": return "BC";
                case "http": return "HTTP";
                case "local": return "LOCAL";
                default: return src.toUpperCase();
            }
        } catch (Throwable e) { return ""; }
    }

    public int queueSize() { return playlist.size(); }
    public int queuePosition() { return playlist.position(); }

    // ===== персистентность (URI-based; пере-резолв на загрузке; без автоплея) =====

    private static File queueFile() {
        return new File(Minecraft.getInstance().gameDirectory, "files/music_queue.json");
    }

    public void saveQueue() {
        try {
            List<AudioTrack> snap = playlist.snapshot();
            if (snap.isEmpty()) return;
            QueueStore.Model m = new QueueStore.Model();
            m.index = playlist.position();
            for (AudioTrack t : snap) {
                try {
                    var info = t.getInfo();
                    if (info.uri == null || info.uri.isBlank()) continue;
                    QueueStore.Entry e = new QueueStore.Entry();
                    e.uri = info.uri; e.identifier = info.identifier; e.title = info.title;
                    e.author = info.author; e.artworkUrl = info.artworkUrl; e.lengthMs = info.length;
                    m.tracks.add(e);
                } catch (Throwable ignored) {}
            }
            if (m.tracks.isEmpty()) return;
            QueueStore.save(queueFile(), m);
        } catch (Throwable ignored) {}
    }

    // восстановить сохранённую очередь (без автоплея — встаёт на паузу на сохранённой позиции)
    public void restoreQueue() {
        Thread t = new Thread(() -> {
            try {
                QueueStore.Model m = QueueStore.load(queueFile());
                if (m == null || m.tracks == null || m.tracks.isEmpty()) return;
                ensureInit();
                if (player == null) return;
                List<AudioTrack> resolved = new ArrayList<>();
                for (QueueStore.Entry e : m.tracks) {
                    if (e.uri == null || e.uri.isBlank()) continue;
                    final AudioTrack[] got = new AudioTrack[1];
                    try {
                        manager.loadItem(e.uri, new AudioLoadResultHandler() {
                            @Override public void trackLoaded(AudioTrack tr) { got[0] = tr; }
                            @Override public void playlistLoaded(AudioPlaylist pl) { if (!pl.getTracks().isEmpty()) got[0] = pl.getTracks().get(0); }
                            @Override public void noMatches() {}
                            @Override public void loadFailed(FriendlyException ex) {}
                        }).get();
                    } catch (Throwable ignored) {}
                    if (got[0] != null) resolved.add(got[0]);
                }
                if (resolved.isEmpty()) return;
                playlist.setAll(resolved);
                playlist.setIndex(Math.min(m.index, resolved.size() - 1));
                active = ActiveSource.LAVA;
                userPaused = true;
                AudioTrack cur = playlist.current();
                if (cur != null) snapshot(cur);
                setState(MusicState.PAUSED);
            } catch (Throwable ignored) {}
        }, "OneTap-QueueRestore");
        t.setDaemon(true);
        t.start();
    }

    // ===== жизненный цикл =====

    public void shutdown() {
        try { if (spotify != null) spotify.shutdown(); } catch (Throwable ignored) {}
        try { if (output != null) output.stop(); } catch (Throwable ignored) {}
        try { if (manager != null) manager.shutdown(); } catch (Throwable ignored) {}
        initialized = false;
    }

    private void setState(MusicState s) { this.state = s; }

    private void setError(String msg) {
        this.errorMessage = msg;
        this.state = MusicState.ERROR;
    }

    private static String searchPrefix(String sourceMode) {
        if (sourceMode == null) return "ytmsearch:";
        if (sourceMode.contains("SoundCloud")) return "scsearch:";
        if (sourceMode.equals("YouTube")) return "ytsearch:";
        return "ytmsearch:";
    }
}
