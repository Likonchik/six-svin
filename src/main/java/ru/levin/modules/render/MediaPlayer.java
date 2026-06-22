package ru.levin.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;
import ru.levin.ExosWare;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.input.EventKey;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.dragManager.Dragging;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BindSetting;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.modules.setting.TextSetting;
import ru.levin.util.color.ColorUtil;
import net.minecraft.resources.ResourceLocation;
import ru.levin.util.music.AlbumArtCache;
import ru.levin.util.music.MusicEngine;
import ru.levin.util.music.MusicProxy;
import ru.levin.util.music.MusicState;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.Scissor;

import java.awt.Color;

// Внутриигровой музыкальный плеер. Стрим YT/YT Music/SoundCloud/HTTP/локалка по ссылке (lavaplayer),
// Spotify — зеркалом по метаданным. Управление: хоткеи (в мире) + команда .music. Плавающая HUD-панель
// (draggable, как остальные HUD-элементы) показывает трек/прогресс/состояние; гейтится тоглом
// HUD->MediaPlayer. Звук идёт мимо OpenAL MC через javax.sound (см. util.music.AudioOutput).
@SuppressWarnings("All")
@FunctionAnnotation(name = "MediaPlayer", keywords = {"Music", "Музыка", "Плеер", "Media"},
        desc = "Музыкальный плеер: YouTube/SoundCloud/Spotify по ссылке", type = Type.Render)
public class MediaPlayer extends Function {

    private final TextSetting url = new TextSetting("Ссылка / запрос", "")
            .withDesc("Вставь ссылку (трек/плейлист) или текст запроса; применяется при снятии фокуса. Проще: .music play <url>");
    private final ModeSetting source = new ModeSetting("Источник", "YT Music", "YT Music", "YouTube", "SoundCloud", "Авто")
            .withDesc("Откуда искать по тексту + куда зеркалить Spotify");
    private final SliderSetting volume = new SliderSetting("Громкость", 50, 0, 100, 1).withDesc("Громкость музыки");
    private final BooleanSetting respectMaster = new BooleanSetting("Учитывать громкость MC", true, "Складывать с мастер-громкостью игры");
    private final BooleanSetting autoplay = new BooleanSetting("Автоплей", true, "Авто-переход к следующему треку очереди");
    private final BooleanSetting loop = new BooleanSetting("Повтор", false, "Повторять текущий трек");
    private final BooleanSetting pauseOnGamePause = new BooleanSetting("Пауза при сворачивании", true, "Пауза при паузе игры / потере фокуса окна");
    private final BooleanSetting pauseOnLegit = new BooleanSetting("Пауза в legitMode", true, "Глушить музыку в режиме паники");
    private final BooleanSetting routeMeta = new BooleanSetting("Метаданные через прокси", true,
            "Spotify/обложки через SOCKS-прокси. ВНИМАНИЕ: сам аудио-стрим в v1 НЕ проксируется (IP виден аудио-серверам)");

    private final BindSetting kPlayPause = new BindSetting("Бинд: пауза/плей", 0);
    private final BindSetting kNext = new BindSetting("Бинд: следующий", 0);
    private final BindSetting kPrev = new BindSetting("Бинд: предыдущий", 0);
    private final BindSetting kVolUp = new BindSetting("Бинд: громче", 0);
    private final BindSetting kVolDown = new BindSetting("Бинд: тише", 0);
    private final BindSetting menuKey = new BindSetting("Бинд: меню плеера", 0);

    public final MusicEngine engine = new MusicEngine();
    public final Dragging musicDrag = ExosWare.getInstance().createDrag(this, "MediaPlayer", 700, 210);
    private final AlbumArtCache albumArt = new AlbumArtCache();

    private String lastLoaded = "";
    // состояние окна снимается на render-потоке (GLFW нельзя дёргать из аудио-потока) и читается гейтом
    private volatile boolean windowFocused = true;
    private volatile boolean windowIconified = false;

    public MediaPlayer() {
        addSettings(url, source, volume, respectMaster, autoplay, loop, pauseOnGamePause, pauseOnLegit, routeMeta,
                kPlayPause, kNext, kPrev, kVolUp, kVolDown, menuKey);
        engine.setSuppliers(this::computeHardMute, this::computeGain);
    }

    public MusicEngine getEngine() { return engine; }

    // открыть отдельное меню плеера (полное управление)
    public void openMenu() {
        try { mc.execute(() -> mc.setScreen(new ru.levin.screens.musicplayer.MediaPlayerScreen())); } catch (Throwable ignored) {}
    }

    public ResourceLocation cover() { return albumArt.get(); }
    public int getVolume() { return volume.get().intValue(); }
    public boolean isLoop() { return loop.get(); }
    public void toggleLoop() { loop.set(!loop.get()); }
    public String getSource() { return source.get(); }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventKey ek) {
            handleKey(ek.key);
        } else if (event instanceof EventUpdate) {
            tickAlways();
        } else if (event instanceof EventRender2D r) {
            // только когда экран НЕ открыт; при открытом экране (чат/ClickGUI/инвентарь) рисует MixinScreenMusic
            if (mc.screen == null && hudVisible()) renderPanel(r.getDrawContext());
        }
    }

    // вызывается также из MixinKeyBoard вне мира (когда открыт экран / нет игрока)
    public void onTransportKey(int key) {
        handleKey(key);
    }

    private void handleKey(int key) {
        if (kPlayPause.getKey() != 0 && key == kPlayPause.getKey()) engine.playPause();
        else if (kNext.getKey() != 0 && key == kNext.getKey()) engine.next();
        else if (kPrev.getKey() != 0 && key == kPrev.getKey()) engine.prev();
        else if (kVolUp.getKey() != 0 && key == kVolUp.getKey()) bumpVolume(5);
        else if (kVolDown.getKey() != 0 && key == kVolDown.getKey()) bumpVolume(-5);
        else if (menuKey.getKey() != 0 && key == menuKey.getKey()) openMenu();
    }

    private void bumpVolume(int delta) {
        volume.set(Math.max(0, Math.min(100, volume.get().intValue() + delta)));
    }

    // вызывается из .music vol <n>
    public void setVolume(int v) {
        volume.set(Math.max(0, Math.min(100, v)));
    }

    // для TextSettingRenderer: коммит ссылки по Enter (надёжнее, чем ожидание тика на расфокус)
    public TextSetting urlSetting() { return url; }

    // выполняется как из EventUpdate (в мире), так и из MixinScreenMusic.tickAlways (вне мира)
    public void tickAlways() {
        // снимок состояния окна на render-потоке (tickAlways зовётся из EventUpdate и MixinScreenMusic — оба render)
        try {
            long h = mc.getWindow().getWindow();
            windowFocused = GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_FOCUSED) == 1;
            windowIconified = GLFW.glfwGetWindowAttrib(h, GLFW.GLFW_ICONIFIED) == 1;
        } catch (Throwable ignored) {}

        MusicProxy.routeMusic = routeMeta.get();
        engine.setAutoplay(autoplay.get());
        engine.setLoop(loop.get());
        // Spotify (librespot) живёт отдельным потоком — гейт по фронту + проброс громкости
        engine.applySpotifyGate(computeHardMute());
        engine.setVolumePercent(volume.get().intValue());
        albumArt.ensure(engine.getArtworkUrl()); // подгрузка обложки текущего трека (off-thread + mc.execute)

        // коммит ссылки: ввели/вставили в поле и сняли фокус -> загрузить один раз
        String v = url.getValue();
        if (v != null && !v.isBlank() && !url.isFocused() && !v.equals(lastLoaded)) {
            lastLoaded = v;
            engine.load(v, source.get());
        }
    }

    // вызывается из .music команды / поля. НЕ трогаем url.setValue (чтобы clear-on-commit не воевал);
    // lastLoaded ставим, чтобы коммит по расфокусу в tickAlways не выстрелил повторно.
    public void play(String input) {
        if (input == null || input.isBlank()) return;
        lastLoaded = input.trim();
        engine.load(input.trim(), source.get());
    }

    // добавить в конец очереди (играть после текущего и остальных)
    public void queue(String input) {
        if (input == null || input.isBlank()) return;
        if (!state) setState(true);
        engine.enqueue(input.trim(), source.get());
    }

    // вставить следующим (сразу после текущего)
    public void playNext(String input) {
        if (input == null || input.isBlank()) return;
        if (!state) setState(true);
        engine.playNext(input.trim(), source.get());
    }

    // восстановить сохранённую очередь (без автоплея)
    public void restoreQueue() {
        if (!state) setState(true);
        engine.restoreQueue();
    }

    public boolean hudVisible() {
        try {
            return state && Manager.FUNCTION_MANAGER.hud != null
                    && Manager.FUNCTION_MANAGER.hud.setting.get("MediaPlayer");
        } catch (Throwable t) { return false; }
    }

    @Override
    protected void onDisable() {
        try { engine.saveQueue(); } catch (Throwable ignored) {} // сохраняем ДО stop() (stop чистит очередь)
        try { engine.stop(); } catch (Throwable ignored) {}
        try { albumArt.dispose(); } catch (Throwable ignored) {}
        super.onDisable();
    }

    // ===== гейтинг/громкость (читаются из потока вывода независимо от Event.call) =====

    private boolean computeHardMute() {
        try {
            if (pauseOnLegit.get() && ClientManager.legitMode) return true;
            if (pauseOnGamePause.get()) {
                // НЕ используем mc.isPaused(): в одиночной игре он true при любом открытом экране (чат/ClickGUI)
                // -> музыка глохла всегда. Глушим только при реальном сворачивании / потере фокуса (кэш с render-потока).
                if (windowIconified) return true;
                if (!windowFocused) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    private double computeGain() {
        try {
            double v = volume.get().doubleValue() / 100.0;
            double g = v * v; // перцептивная кривая (квадрат)
            if (respectMaster.get()) {
                double master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
                g *= master;
            }
            return Math.max(0.0, Math.min(1.0, g));
        } catch (Throwable t) {
            return 0.5;
        }
    }

    // ===== HUD =====

    private static final float PANEL_W = 212f, PANEL_H = 64f;

    // регионы кликабельных контролов {rx,ry,rw,rh} — одни и те же для отрисовки и хит-теста
    private float[] regPrev(float x, float y) { return new float[]{x + 8, y + 48, 16, 12}; }
    private float[] regPlay(float x, float y) { return new float[]{x + 28, y + 48, 16, 12}; }
    private float[] regNext(float x, float y) { return new float[]{x + 48, y + 48, 16, 12}; }
    private float[] regSeek(float x, float y) { return new float[]{x + 52, y + 28, PANEL_W - 58, 8}; }  // зона перемотки (шире полосы для удобства)
    private float[] regVol(float x, float y)  { return new float[]{x + 76, y + 49, PANEL_W - 110, 10}; } // зона громкости

    public void renderPanel(GuiGraphics ctx) {
        try {
            PoseStack pose = ctx.pose();
            float x = musicDrag.getX();
            float y = musicDrag.getY();
            float w = PANEL_W, h = PANEL_H;

            int bg = new Color(20, 20, 20, 222).getRGB();
            int panel2 = new Color(40, 40, 40, 255).getRGB();
            int gray = new Color(165, 165, 165, 255).getRGB();
            int track = new Color(60, 60, 60, 255).getRGB();
            int accent = ColorUtil.getColorStyle(90, 255);

            RenderUtil.drawRoundedRect(pose, x, y, w, h, 4f, bg);

            // обложка трека либо плейсхолдер "MP"
            ResourceLocation cover = albumArt.get();
            if (cover != null) RenderUtil.drawTexture(pose, cover, x + 6, y + 6, 40, 40, 3f, -1);
            else {
                RenderUtil.drawRoundedRect(pose, x + 6, y + 6, 40, 40, 3f, panel2);
                FontUtils.durman[16].centeredDraw(pose, "MP", x + 26, y + 20, gray);
            }

            float tx = x + 52f;
            float tw = w - 58f;

            MusicState st = engine.getState();
            String title = engine.getTitle();
            String artist = engine.getArtist();

            String headLine; int headColor = -1;
            switch (st) {
                case ERROR -> { headLine = engine.getErrorMessage(); headColor = new Color(235, 80, 80).getRGB(); }
                case LOADING -> headLine = "Загрузка...";
                case IDLE -> { headLine = "Вставь ссылку (.music play <url>)"; headColor = gray; }
                case ENDED -> headLine = title.isBlank() ? "Очередь пуста" : title;
                default -> headLine = title.isBlank() ? "—" : title;
            }

            // счётчик N/M (текущий / всего в очереди)
            String counter = "";
            int qsize = engine.queueSize();
            if (qsize > 1) counter = (engine.queuePosition() + 1) + "/" + qsize;

            Scissor.push();
            Scissor.setFromComponentCoordinates(tx, y + 5, tw, 24);
            FontUtils.durman[14].drawLeftAligned(pose, headLine, tx, y + 6f, headColor);
            if (st != MusicState.ERROR && st != MusicState.IDLE && !artist.isBlank())
                FontUtils.durman[12].drawLeftAligned(pose, artist, tx, y + 18f, gray);
            Scissor.unset();
            Scissor.pop();

            // верх-право: счётчик N/M + бейдж провайдера
            String prov = engine.getProviderLabel();
            float rightX = x + w - 6f;
            if (!counter.isBlank()) {
                FontUtils.durman[11].drawRightAligned(pose, counter, rightX, y + 6f, gray);
                rightX -= FontUtils.durman[11].getWidth(counter) + 6f;
            }
            if (!prov.isBlank()) {
                float pw = FontUtils.durman[11].getWidth(prov) + 8f;
                RenderUtil.drawRoundedRect(pose, rightX - pw, y + 5.5f, pw, 11f, 2f, accent);
                FontUtils.durman[11].drawLeftAligned(pose, prov, rightX - pw + 4f, y + 7f, -1);
            }

            // полоса перемотки + время
            long pos = engine.getPositionMs(), dur = engine.getDurationMs();
            float by = y + 31f;
            RenderUtil.drawRoundedRect(pose, tx, by, tw, 3f, 1f, track);
            float frac = dur > 0 ? Math.max(0f, Math.min(1f, pos / (float) dur)) : 0f;
            if (frac > 0f) RenderUtil.drawRoundedRect(pose, tx, by, tw * frac, 3f, 1f, accent);
            FontUtils.durman[11].drawLeftAligned(pose, fmtTime(pos) + " / " + (dur > 0 ? fmtTime(dur) : "--:--"), tx, y + 35f, gray);

            // транспорт-кнопки (кликабельные)
            boolean playing = st == MusicState.PLAYING;
            panelBtn(pose, regPrev(x, y), "prev", playing, panel2);
            panelBtn(pose, regPlay(x, y), "play", playing, accent);
            panelBtn(pose, regNext(x, y), "next", playing, panel2);

            // громкость (кликабельная полоса) + %
            float[] v = regVol(x, y);
            float vbY = v[1] + v[3] / 2f - 2f;
            RenderUtil.drawRoundedRect(pose, v[0], vbY, v[2], 4f, 1f, track);
            RenderUtil.drawRoundedRect(pose, v[0], vbY, v[2] * (volume.get().intValue() / 100f), 4f, 1f, accent);
            FontUtils.durman[11].drawRightAligned(pose, volume.get().intValue() + "%", x + w - 6f, y + 49f, gray);

            musicDrag.setWidth(w);
            musicDrag.setHeight(h);
        } catch (Throwable ignored) {
            // рендер не должен ронять игру
        }
    }

    // транспорт-кнопка панели: фон + иконка нашим рендером
    private void panelBtn(PoseStack pose, float[] r, String type, boolean playing, int bgc) {
        RenderUtil.drawRoundedRect(pose, r[0], r[1], r[2], r[3], 2f, bgc);
        float s = Math.min(r[2], r[3]) - 6f;
        float ix = r[0] + (r[2] - s) / 2f, iy = r[1] + (r[3] - s) / 2f;
        switch (type) {
            case "prev" -> ru.levin.util.music.PlayerIcons.prev(pose, ix, iy, s, -1);
            case "next" -> ru.levin.util.music.PlayerIcons.next(pose, ix, iy, s, -1);
            default -> { if (playing) ru.levin.util.music.PlayerIcons.pause(pose, ix, iy, s, -1); else ru.levin.util.music.PlayerIcons.play(pose, ix, iy, s, -1); }
        }
    }

    // клик по панели (из MixinScreenMusic, когда открыт экран и курсор свободен — ClickGUI/чат). true = обработали.
    public boolean onMouseClicked(double mx, double my, int button) {
        if (button != 0 || !hudVisible()) return false;
        float x = musicDrag.getX(), y = musicDrag.getY();
        int ix = (int) mx, iy = (int) my;
        if (hit(ix, iy, regPrev(x, y))) { engine.prev(); return true; }
        if (hit(ix, iy, regPlay(x, y))) { engine.playPause(); return true; }
        if (hit(ix, iy, regNext(x, y))) { engine.next(); return true; }
        float[] s = regSeek(x, y);
        if (hit(ix, iy, s)) {
            long dur = engine.getDurationMs();
            if (engine.isSeekable() && dur > 0) {
                float f = Math.max(0f, Math.min(1f, (float) (mx - s[0]) / s[2]));
                engine.seek((long) (f * dur));
            }
            return true;
        }
        float[] v = regVol(x, y);
        if (hit(ix, iy, v)) {
            float f = Math.max(0f, Math.min(1f, (float) (mx - v[0]) / v[2]));
            setVolume(Math.round(f * 100));
            return true;
        }
        return false;
    }

    private static boolean hit(int mx, int my, float[] r) {
        return RenderUtil.isInRegion(mx, my, r[0], r[1], r[2], r[3]);
    }

    private static String fmtTime(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
