package ru.levin.modules.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.Render3DUtil;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// Трассеры пуль TACZ: показывают траекторию полёта пули. Каждый тик накапливаем позиции всех летящих
// пуль (EntityKineticBullet) и в EventRender3D рисуем по ним ломаную — видно дугу с учётом дропа.
// Пулю детектим по имени класса (без жёсткой ссылки на TACZ), поэтому модуль безвреден без мода.
@SuppressWarnings("All")
@FunctionAnnotation(name = "BulletTracers", keywords = {"Трассеры", "Tracers", "Пули"}, desc = "Показывает траекторию полёта пуль TACZ", type = Type.Render)
public class BulletTracers extends Function {

    private final SliderSetting width = new SliderSetting("Толщина", 2f, 0.5f, 5f, 0.1f);
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true);
    private final BooleanSetting fade = new BooleanSetting("Затухание хвоста", true);
    private final SliderSetting linger = new SliderSetting("Держать после попадания, мс", 1500f, 0f, 5000f, 100f);

    private static final int MAX_POINTS = 80;

    private final Map<Integer, Trail> trails = new HashMap<>();

    public BulletTracers() {
        addSettings(width, throughWalls, fade, linger);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;

        if (event instanceof EventUpdate) {
            Set<Integer> alive = new HashSet<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!isBullet(e)) continue;
                alive.add(e.getId());
                Vec3 cur = new Vec3(e.getX(), e.getY(), e.getZ());
                Trail tr = trails.get(e.getId());
                if (tr == null) {
                    // первая встреча: засеваем позицией прошлого тика (xOld), чтобы даже пуля, прожившая
                    // 1 тик, дала видимый отрезок, а трасса начиналась у дула, а не на тик позже.
                    tr = new Trail();
                    trails.put(e.getId(), tr);
                    Vec3 prev = new Vec3(e.xOld, e.yOld, e.zOld);
                    if (prev.distanceToSqr(cur) > 1.0e-9) tr.points.addLast(prev);
                }
                // не дублируем совпадающие точки — иначе нулевой сегмент даёт NaN-нормаль в drawLine
                if (tr.points.isEmpty() || tr.points.getLast().distanceToSqr(cur) > 1.0e-9) {
                    tr.points.addLast(cur);
                }
                while (tr.points.size() > MAX_POINTS) tr.points.removeFirst();
                tr.last = System.currentTimeMillis();
            }
            // подчищаем хвосты пуль, которых уже нет, спустя linger
            long now = System.currentTimeMillis();
            long keep = (long) linger.get().floatValue();
            Iterator<Map.Entry<Integer, Trail>> it = trails.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Trail> en = it.next();
                if (!alive.contains(en.getKey()) && now - en.getValue().last > keep) it.remove();
            }
        }

        if (event instanceof EventRender3D) {
            boolean depth = !throughWalls.get();
            float w = width.get().floatValue();
            for (Trail tr : trails.values()) {
                if (tr.points.size() < 2) continue;
                Vec3 prev = null;
                int i = 0, n = tr.points.size();
                for (Vec3 p : tr.points) {
                    if (prev != null && prev.distanceToSqr(p) > 1.0e-9)
                        Render3DUtil.drawLine(prev, p, colorAt(i, n), w, depth);
                    prev = p;
                    i++;
                }
            }
        }
    }

    // голова (новые точки) ярче, хвост прозрачнее
    private int colorAt(int i, int n) {
        int rgb = ColorUtil.getColorStyle(90) & 0x00FFFFFF;
        int a = fade.get() ? (int) (255f * ((float) i / Math.max(1, n - 1))) : 255;
        return (a << 24) | rgb;
    }

    // детект пули TACZ по имени класса — без жёсткой ссылки (graceful без мода)
    private boolean isBullet(Entity e) {
        return e != null && e.getClass().getName().contains("KineticBullet");
    }

    @Override
    protected void onDisable() {
        trails.clear();
        super.onDisable();
    }

    private static final class Trail {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        long last;
    }
}
