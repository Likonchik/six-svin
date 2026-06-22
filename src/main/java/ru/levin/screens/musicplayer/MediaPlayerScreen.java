package ru.levin.screens.musicplayer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.modules.render.MediaPlayer;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.music.MusicState;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.Scissor;

import java.awt.Color;
import java.util.List;

// Отдельное удобное меню плеера: обложка, перемотка/громкость (клик+драг), prev/play/next, поле ввода
// ссылки + кнопки Играть/В очередь/Следующим, прокручиваемый список очереди (клик по треку = переход),
// Стоп/Повтор/Восстановить. Открывается биндом "меню плеера" или .music gui.
public class MediaPlayerScreen extends Screen implements IMinecraft {

    private static final int PW = 360, PH = 300, ROW = 11, LIST_ROWS = 4;

    private String input = "";
    private boolean inputFocused = false;
    private int firstRow = 0;            // прокрутка списка очереди
    private boolean dragSeek = false, dragVol = false;
    private long blink = 0;
    private boolean caret = false;

    public MediaPlayerScreen() { super(Component.literal("MediaPlayer")); }

    private MediaPlayer mp() { try { return Manager.FUNCTION_MANAGER.mediaPlayer; } catch (Throwable t) { return null; } }

    @Override public boolean isPauseScreen() { return false; }

    private float px() { return (this.width - PW) / 2f; }
    private float py() { return (this.height - PH) / 2f; }

    private float[] rCover()   { return new float[]{px() + 12, py() + 26, 76, 76}; }
    private float[] rSeek()    { return new float[]{px() + 12, py() + 108, PW - 24, 10}; }
    private float[] rVol()     { return new float[]{px() + 54, py() + 148, 150, 10}; }
    private float[] rPrev()    { return new float[]{px() + PW / 2f - 46, py() + 126, 28, 20}; }
    private float[] rPlay()    { return new float[]{px() + PW / 2f - 14, py() + 126, 28, 20}; }
    private float[] rNext()    { return new float[]{px() + PW / 2f + 18, py() + 126, 28, 20}; }
    private float[] rField()   { return new float[]{px() + 12, py() + 174, PW - 24, 16}; }
    private float[] rPlayBtn() { return new float[]{px() + 12, py() + 196, 100, 18}; }
    private float[] rQueueBtn(){ return new float[]{px() + 116, py() + 196, 110, 18}; }
    private float[] rNextBtn() { return new float[]{px() + 230, py() + 196, 118, 18}; }
    private float[] rList()    { return new float[]{px() + 12, py() + 220, PW - 24, ROW * LIST_ROWS + 4}; }
    private float[] rStop()    { return new float[]{px() + 12, py() + 280, 78, 16}; }
    private float[] rLoop()    { return new float[]{px() + 96, py() + 280, 78, 16}; }
    private float[] rRestore() { return new float[]{px() + 180, py() + 280, 96, 16}; }
    private float[] rClose()   { return new float[]{px() + 282, py() + 280, 66, 16}; }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // затемнённый фон
        MediaPlayer mp = mp();
        if (mp == null) return;
        try { mp.tickAlways(); } catch (Throwable ignored) {} // обновляет обложку/состояние, даже если HUD-тогл off

        PoseStack pose = ctx.pose();
        float x = px(), y = py();
        int bg = new Color(18, 18, 18, 235).getRGB();
        int panel2 = new Color(40, 40, 40, 255).getRGB();
        int gray = new Color(165, 165, 165, 255).getRGB();
        int track = new Color(60, 60, 60, 255).getRGB();
        int accent = ColorUtil.getColorStyle(90, 255);

        RenderUtil.drawRoundedRect(pose, x, y, PW, PH, 5f, bg);
        FontUtils.durman[15].drawLeftAligned(pose, "Media Player", x + 12, y + 8, -1);

        var engine = mp.getEngine();
        MusicState st = engine.getState();

        // обложка
        ResourceLocation cover = mp.cover();
        float[] cv = rCover();
        if (cover != null) RenderUtil.drawTexture(pose, cover, cv[0], cv[1], cv[2], cv[3], 4f, -1);
        else {
            RenderUtil.drawRoundedRect(pose, cv[0], cv[1], cv[2], cv[3], 4f, panel2);
            FontUtils.durman[16].centeredDraw(pose, "MP", cv[0] + cv[2] / 2f, cv[1] + cv[3] / 2f - 6, gray);
        }

        // метаданные
        float tx = x + 100;
        String title = engine.getTitle();
        if (title.isBlank()) title = st == MusicState.IDLE ? "Ничего не играет" : "—";
        FontUtils.durman[15].drawLeftAligned(pose, clip(title, 42), tx, y + 30, -1);
        String artist = engine.getArtist();
        if (!artist.isBlank()) FontUtils.durman[13].drawLeftAligned(pose, clip(artist, 46), tx, y + 46, gray);
        String prov = engine.getProviderLabel();
        if (!prov.isBlank()) FontUtils.durman[12].drawLeftAligned(pose, prov, tx, y + 62, accent);
        int qs = engine.queueSize();
        if (qs > 1) FontUtils.durman[12].drawRightAligned(pose, (engine.queuePosition() + 1) + "/" + qs, x + PW - 12, y + 62, gray);
        if (st == MusicState.ERROR) FontUtils.durman[12].drawLeftAligned(pose, clip(engine.getErrorMessage(), 46), tx, y + 78, new Color(235, 80, 80).getRGB());

        // перемотка
        long pos = engine.getPositionMs(), dur = engine.getDurationMs();
        float[] sk = rSeek();
        float sby = sk[1] + sk[3] / 2f - 2f;
        RenderUtil.drawRoundedRect(pose, sk[0], sby, sk[2], 4f, 2f, track);
        float frac = dur > 0 ? Math.max(0f, Math.min(1f, pos / (float) dur)) : 0f;
        if (frac > 0f) RenderUtil.drawRoundedRect(pose, sk[0], sby, sk[2] * frac, 4f, 2f, accent);
        FontUtils.durman[11].drawLeftAligned(pose, fmt(pos), sk[0], y + 119, gray);
        FontUtils.durman[11].drawRightAligned(pose, dur > 0 ? fmt(dur) : "--:--", sk[0] + sk[2], y + 119, gray);

        // транспорт (иконки нашим рендером)
        boolean playing = st == MusicState.PLAYING;
        transport(pose, rPrev(), "prev", playing, mouseX, mouseY, panel2, accent);
        transport(pose, rPlay(), "play", playing, mouseX, mouseY, accent, accent);
        transport(pose, rNext(), "next", playing, mouseX, mouseY, panel2, accent);

        // громкость
        float[] vb = rVol();
        FontUtils.durman[12].drawLeftAligned(pose, "Громк.", x + 12, vb[1] + 1, gray);
        float vby = vb[1] + vb[3] / 2f - 2f;
        RenderUtil.drawRoundedRect(pose, vb[0], vby, vb[2], 4f, 2f, track);
        RenderUtil.drawRoundedRect(pose, vb[0], vby, vb[2] * (mp.getVolume() / 100f), 4f, 2f, accent);
        FontUtils.durman[12].drawLeftAligned(pose, mp.getVolume() + "%", vb[0] + vb[2] + 8, vb[1] + 1, -1);

        // поле ввода
        float[] f = rField();
        RenderUtil.drawRoundedRect(pose, f[0], f[1], f[2], f[3], 3f, inputFocused ? new Color(55, 55, 55, 255).getRGB() : panel2);
        long now = System.currentTimeMillis();
        if (now - blink > 500) { caret = !caret; blink = now; }
        String shown = input.isEmpty() && !inputFocused ? "Вставь ссылку / запрос (Ctrl+V), Enter — играть" : input + (inputFocused && caret ? "_" : "");
        Scissor.push();
        Scissor.setFromComponentCoordinates(f[0] + 4, f[1], f[2] - 8, f[3]);
        FontUtils.durman[12].drawLeftAligned(pose, shown, f[0] + 5, f[1] + 4, input.isEmpty() && !inputFocused ? gray : -1);
        Scissor.unset(); Scissor.pop();

        button(pose, rPlayBtn(), "▶ Играть", mouseX, mouseY, panel2, accent);
        button(pose, rQueueBtn(), "+ В очередь", mouseX, mouseY, panel2, accent);
        button(pose, rNextBtn(), "Следующим", mouseX, mouseY, panel2, accent);

        // список очереди
        float[] ls = rList();
        RenderUtil.drawRoundedRect(pose, ls[0], ls[1], ls[2], ls[3], 3f, new Color(26, 26, 26, 255).getRGB());
        List<String> titles = engine.getQueueTitles();
        int cur = engine.queuePosition();
        firstRow = Math.max(0, Math.min(firstRow, Math.max(0, titles.size() - LIST_ROWS)));
        Scissor.push();
        Scissor.setFromComponentCoordinates(ls[0], ls[1] + 2, ls[2], ls[3] - 4);
        for (int r = 0; r < LIST_ROWS; r++) {
            int idx = firstRow + r;
            if (idx >= titles.size()) break;
            float ry = ls[1] + 3 + r * ROW;
            boolean isCur = idx == cur;
            boolean hov = RenderUtil.isInRegion(mouseX, mouseY, ls[0] + 1, ry - 1, ls[2] - 2, ROW);
            if (isCur) RenderUtil.drawRoundedRect(pose, ls[0] + 1, ry - 1, ls[2] - 2, ROW, 1f, accent);
            else if (hov) RenderUtil.drawRoundedRect(pose, ls[0] + 1, ry - 1, ls[2] - 2, ROW, 1f, panel2);
            String row = (idx + 1) + ". " + clip(titles.get(idx).isBlank() ? "—" : titles.get(idx), 46);
            FontUtils.durman[11].drawLeftAligned(pose, row, ls[0] + 4, ry, isCur ? -1 : gray);
            // крестик удаления справа
            float dx = ls[0] + ls[2] - 12;
            boolean delHov = RenderUtil.isInRegion(mouseX, mouseY, dx, ry - 1, 12, ROW);
            int xc = delHov ? new Color(235, 80, 80).getRGB() : (isCur ? -1 : gray);
            RenderUtil.drawLine(dx + 3, ry + 1, dx + 8, ry + 7, xc);
            RenderUtil.drawLine(dx + 8, ry + 1, dx + 3, ry + 7, xc);
        }
        Scissor.unset(); Scissor.pop();
        if (titles.size() > LIST_ROWS)
            FontUtils.durman[11].drawRightAligned(pose, (firstRow + 1) + "-" + Math.min(firstRow + LIST_ROWS, titles.size()) + " из " + titles.size(), ls[0] + ls[2], ls[1] + ls[3] + 2, gray);

        // нижние кнопки
        button(pose, rStop(), "Стоп", mouseX, mouseY, panel2, accent);
        button(pose, rLoop(), mp.isLoop() ? "Повтор: вкл" : "Повтор", mouseX, mouseY, mp.isLoop() ? accent : panel2, accent);
        button(pose, rRestore(), "Восстановить", mouseX, mouseY, panel2, accent);
        button(pose, rClose(), "Закрыть", mouseX, mouseY, panel2, accent);
    }

    private void button(PoseStack pose, float[] r, String label, int mx, int my, int bgc, int hoverColor) {
        boolean hov = RenderUtil.isInRegion(mx, my, r[0], r[1], r[2], r[3]);
        RenderUtil.drawRoundedRect(pose, r[0], r[1], r[2], r[3], 2f, hov ? hoverColor : bgc);
        FontUtils.durman[12].centeredDraw(pose, label, r[0] + r[2] / 2f, r[1] + r[3] / 2f - 4f, -1);
    }

    // кнопка транспорта: фон + иконка (PlayerIcons), центрированная в зоне
    private void transport(PoseStack pose, float[] r, String type, boolean playing, int mx, int my, int bgc, int accent) {
        boolean hov = RenderUtil.isInRegion(mx, my, r[0], r[1], r[2], r[3]);
        RenderUtil.drawRoundedRect(pose, r[0], r[1], r[2], r[3], 3f, hov ? accent : bgc);
        float s = Math.min(r[2], r[3]) - 9f;
        float ix = r[0] + (r[2] - s) / 2f, iy = r[1] + (r[3] - s) / 2f;
        switch (type) {
            case "prev" -> ru.levin.util.music.PlayerIcons.prev(pose, ix, iy, s, -1);
            case "next" -> ru.levin.util.music.PlayerIcons.next(pose, ix, iy, s, -1);
            default -> { if (playing) ru.levin.util.music.PlayerIcons.pause(pose, ix, iy, s, -1); else ru.levin.util.music.PlayerIcons.play(pose, ix, iy, s, -1); }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        MediaPlayer mp = mp();
        if (mp != null && button == 0) {
            var engine = mp.getEngine();
            if (in(mx, my, rPrev())) { engine.prev(); return true; }
            if (in(mx, my, rPlay())) { engine.playPause(); return true; }
            if (in(mx, my, rNext())) { engine.next(); return true; }
            if (in(mx, my, rStop())) { engine.stop(); return true; }
            if (in(mx, my, rLoop())) { mp.toggleLoop(); return true; }
            if (in(mx, my, rRestore())) { mp.restoreQueue(); return true; }
            if (in(mx, my, rClose())) { onClose(); return true; }
            if (in(mx, my, rPlayBtn())) { commit(mp::play); return true; }
            if (in(mx, my, rQueueBtn())) { commit(mp::queue); return true; }
            if (in(mx, my, rNextBtn())) { commit(mp::playNext); return true; }
            if (in(mx, my, rField())) { inputFocused = true; return true; }
            float[] sk = rSeek();
            if (in(mx, my, sk)) { dragSeek = true; seekAt(mx, sk); inputFocused = false; return true; }
            float[] vb = rVol();
            if (in(mx, my, vb)) { dragVol = true; volAt(mx, vb); inputFocused = false; return true; }
            float[] ls = rList();
            if (in(mx, my, ls)) {
                int r = (int) ((my - (ls[1] + 3)) / ROW);
                int idx = firstRow + r;
                if (idx >= 0 && idx < engine.getQueueTitles().size()) {
                    float dx = ls[0] + ls[2] - 12;
                    if (mx >= dx) engine.removeAt(idx); else engine.jumpTo(idx); // крестик = удалить, иначе перейти
                }
                inputFocused = false;
                return true;
            }
            inputFocused = false; // клик вне поля
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragSeek) { seekAt(mx, rSeek()); return true; }
        if (dragVol) { volAt(mx, rVol()); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragSeek = false; dragVol = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (in(mx, my, rList())) { firstRow = Math.max(0, firstRow - (int) Math.signum(sy)); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) { MediaPlayer mp = mp(); if (mp != null) commit(mp::play); return true; }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { inputFocused = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                try { String c = mc.keyboardHandler.getClipboard(); if (c != null) input = c.replace("\n", "").replace("\r", ""); } catch (Throwable ignored) {}
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) { input = ""; return true; }
            return true; // глотаем клавиши, пока печатаем
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (inputFocused && !Character.isISOControl(c)) { input += c; return true; }
        return super.charTyped(c, modifiers);
    }

    // ===== helpers =====

    private void commit(java.util.function.Consumer<String> action) {
        String v = input.trim();
        if (!v.isEmpty()) { action.accept(v); input = ""; }
        inputFocused = false;
    }

    private void seekAt(double mx, float[] sk) {
        MediaPlayer mp = mp(); if (mp == null) return;
        var engine = mp.getEngine();
        long dur = engine.getDurationMs();
        if (engine.isSeekable() && dur > 0) {
            float f = (float) Math.max(0, Math.min(1, (mx - sk[0]) / sk[2]));
            engine.seek((long) (f * dur));
        }
    }

    private void volAt(double mx, float[] vb) {
        MediaPlayer mp = mp(); if (mp == null) return;
        float f = (float) Math.max(0, Math.min(1, (mx - vb[0]) / vb[2]));
        mp.setVolume(Math.round(f * 100));
    }

    private static boolean in(double mx, double my, float[] r) {
        return RenderUtil.isInRegion((int) mx, (int) my, r[0], r[1], r[2], r[3]);
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static String fmt(long ms) {
        long sec = ms / 1000;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
}
