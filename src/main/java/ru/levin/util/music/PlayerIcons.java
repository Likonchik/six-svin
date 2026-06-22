package ru.levin.util.music;

import com.mojang.blaze3d.vertex.PoseStack;
import ru.levin.util.render.RenderUtil;

// Иконки транспорта, нарисованные нашим рендером (RenderUtil): треугольники растеризуем построчно
// (нет примитива треугольника), паузу/полоски — скруглёнными прямоугольниками. Все принимают левый-верхний
// угол квадратной зоны (x,y) и сторону s.
public final class PlayerIcons {
    private PlayerIcons() {}

    public static void play(PoseStack p, float x, float y, float s, int c) {
        triRight(p, x + s * 0.12f, y, s * 0.82f, c);
    }

    public static void pause(PoseStack p, float x, float y, float s, int c) {
        float bw = s * 0.30f;
        RenderUtil.drawRoundedRect(p, x + s * 0.12f, y, bw, s, 1f, c);
        RenderUtil.drawRoundedRect(p, x + s - bw - s * 0.12f, y, bw, s, 1f, c);
    }

    public static void next(PoseStack p, float x, float y, float s, int c) {
        triRight(p, x, y + s * 0.06f, s * 0.72f, c);
        float bw = s * 0.16f;
        RenderUtil.drawRoundedRect(p, x + s - bw, y, bw, s, 1f, c);
    }

    public static void prev(PoseStack p, float x, float y, float s, int c) {
        float bw = s * 0.16f;
        RenderUtil.drawRoundedRect(p, x, y, bw, s, 1f, c);
        triLeft(p, x + s * 0.28f, y + s * 0.06f, s * 0.72f, c);
    }

    // вершина справа (основание слева)
    private static void triRight(PoseStack p, float x, float y, float s, int c) {
        int rows = Math.max(1, Math.round(s));
        for (int r = 0; r < rows; r++) {
            float d = Math.abs((r + 0.5f) - s / 2f) / (s / 2f);
            float w = s * (1f - d);
            if (w < 0.5f) continue;
            RenderUtil.drawRoundedRect(p, x, y + r, w, 1.2f, 0, c);
        }
    }

    // вершина слева (основание справа)
    private static void triLeft(PoseStack p, float x, float y, float s, int c) {
        int rows = Math.max(1, Math.round(s));
        for (int r = 0; r < rows; r++) {
            float d = Math.abs((r + 0.5f) - s / 2f) / (s / 2f);
            float w = s * (1f - d);
            if (w < 0.5f) continue;
            RenderUtil.drawRoundedRect(p, x + s - w, y + r, w, 1.2f, 0, c);
        }
    }
}
