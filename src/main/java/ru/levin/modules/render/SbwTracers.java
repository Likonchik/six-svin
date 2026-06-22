package ru.levin.modules.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.Render3DUtil;
import ru.levin.util.sbw.SbwAccess;
import ru.levin.util.sbw.SbwBallistics;
import ru.levin.util.vector.EntityPosition;
import ru.levin.util.vector.VectorUtil;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

// Трассеры снарядов SuperbWarfare (ракеты/бомбы/гранаты/снаряды автопушки и пр.): хвост по траектории полёта
// + плавающая подпись над энтити (имя / дистанция / время до удара / таймер фитиля гранаты). Всё read-only,
// снаряды детектятся по пакету, поля читаются рефлексией — безвредно без мода. См. SbwAccess/SbwBallistics.
@FunctionAnnotation(name = "SbwTracers", keywords = {"Трассеры", "SBW", "Снаряды", "Ракеты"}, desc = "Трассеры и подписи снарядов SuperbWarfare", type = Type.Render)
public class SbwTracers extends Function {

    // --- хвост-трасса ---
    private final SliderSetting width = new SliderSetting("Толщина", 2f, 0.5f, 5f, 0.1f).withDesc("Толщина линии трассы");
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true, "Рисовать трассу сквозь блоки");
    private final BooleanSetting fade = new BooleanSetting("Затухание хвоста", true, "Хвост прозрачнее к началу");
    private final SliderSetting linger = new SliderSetting("Держать после исчезновения, мс", 1200f, 0f, 5000f, 100f).withDesc("Сколько держать трассу после исчезновения снаряда");

    // --- подпись над энтити ---
    private final BooleanSetting label = new BooleanSetting("Подпись над снарядом", true, "Показывать текст над снарядом");
    private final BooleanSetting showName = new BooleanSetting("Имя снаряда", true, "Тип снаряда", () -> this.label.get());
    private final BooleanSetting showDist = new BooleanSetting("Дистанция", true, "Расстояние до снаряда", () -> this.label.get());
    private final BooleanSetting showTime = new BooleanSetting("Время до удара", true, "Оценка времени до падения", () -> this.label.get());
    private final BooleanSetting showFuse = new BooleanSetting("Таймер гранаты", true, "Обратный отсчёт фитиля гранаты", () -> this.label.get());

    private static final int MAX_POINTS = 100;
    private final Map<Integer, Trail> trails = new HashMap<>();

    public SbwTracers() {
        addSettings(width, throughWalls, fade, linger, label, showName, showDist, showTime, showFuse);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;

        if (event instanceof EventUpdate) {
            Set<Integer> alive = new HashSet<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!SbwAccess.isProjectile(e)) continue;
                alive.add(e.getId());
                Vec3 cur = new Vec3(e.getX(), e.getY(), e.getZ());
                Trail tr = trails.get(e.getId());
                if (tr == null) {
                    tr = new Trail();
                    trails.put(e.getId(), tr);
                    Vec3 prev = new Vec3(e.xOld, e.yOld, e.zOld);
                    if (prev.distanceToSqr(cur) > 1.0e-9) tr.points.addLast(prev);
                }
                if (tr.points.isEmpty() || tr.points.getLast().distanceToSqr(cur) > 1.0e-9) {
                    tr.points.addLast(cur);
                }
                while (tr.points.size() > MAX_POINTS) tr.points.removeFirst();
                tr.last = System.currentTimeMillis();
            }
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

        if (event instanceof EventRender2D e && label.get()) {
            float tickDelta = e.getDeltatick().getGameTimeDeltaPartialTick(true);
            for (Entity ent : mc.level.entitiesForRendering()) {
                if (!SbwAccess.isProjectile(ent)) continue;
                String text = buildLabel(ent);
                if (text.isEmpty()) continue;
                Vector3d v = VectorUtil.toScreen(EntityPosition.get(ent, ent.getBbHeight() + 0.35f, tickDelta));
                if (v.z < 0 || v.z >= 1) continue;
                FontUtils.durman[13].centeredDraw(e.getMatrixStack(), text, (float) v.x, (float) v.y, Color.WHITE.getRGB());
            }
        }
    }

    private String buildLabel(Entity ent) {
        StringBuilder sb = new StringBuilder();
        if (showName.get()) sb.append(SbwAccess.displayName(ent));
        if (showDist.get() && mc.player != null) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.format("%.0fм", mc.player.distanceTo(ent)));
        }
        // фитиль гранаты — точный отсчёт (fuse тиков)
        int fuse = SbwAccess.fuse(ent);
        if (showFuse.get() && fuse >= 0) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(String.format("⏱%.1fс", fuse / 20f));
        } else if (showTime.get() && SbwAccess.isExplosive(ent)) {
            // время до падения/взрыва (физика по типу снаряда)
            boolean rocket = SbwAccess.isRpgRocket(ent);
            int f = SbwAccess.fuse(ent);
            SbwBallistics.Mode mode = rocket ? SbwBallistics.Mode.PROJECTILE_IMPACT
                    : (f >= 0 ? (SbwAccess.isRgo(ent) ? SbwBallistics.Mode.GRENADE_IMPACT : SbwBallistics.Mode.GRENADE_BOUNCE)
                    : SbwBallistics.Mode.PROJECTILE_IMPACT);
            SbwBallistics.Result r = SbwBallistics.simulate(ent, SbwAccess.gravity(ent), rocket ? 100 : 200, f, mode, rocket);
            if (r != null) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(String.format("%.1fс", r.ticks / 20f));
            }
        }
        return sb.toString();
    }

    private int colorAt(int i, int n) {
        int rgb = ColorUtil.getColorStyle(90) & 0x00FFFFFF;
        int a = fade.get() ? (int) (255f * ((float) i / Math.max(1, n - 1))) : 255;
        return (a << 24) | rgb;
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
