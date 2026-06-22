package ru.levin.modules.render;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import ru.levin.events.Event;
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
import ru.levin.util.vector.VectorUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

// Предикт падения взрывчатки SuperbWarfare (физика выверена по декомпилу 0.8.8 — см. SbwBallistics).
// Цели: (1) летящие снаряды (граната M67 — отскоки+фитиль; РГО — взрыв об удар/фитиль; ракеты — дуга с
// разгоном); (2) В РУКЕ: граната считается всегда при удержании (без зажатия — превью на макс. силе, при
// зажатии — по реальной натяжке), РПГ — по скорости пуска. Таймер фитиля в руке и в полёте. Метка попадания
// и маркер видны СКВОЗЬ стены. Read-only, рефлексия — без мода безвреден.
@FunctionAnnotation(name = "SbwProjectilePredict", keywords = {"Предикт", "SBW", "Граната", "РГО", "РПГ"}, desc = "Предикт падения взрывчатки SBW (полёт и в руке) + зона взрыва + таймер", type = Type.Render)
public class SbwProjectilePredict extends Function {

    private final BooleanSetting flying = new BooleanSetting("Летящие снаряды", true, "Предикт уже брошенной взрывчатки");
    private final BooleanSetting inHand = new BooleanSetting("В руке (граната/РПГ)", true, "Считать предикт при удержании");

    private final BooleanSetting arc = new BooleanSetting("Дуга траектории", true, "Линия пути снаряда");
    private final BooleanSetting marker = new BooleanSetting("Маркер падения", true, "Метка в точке падения (сквозь стены)");
    private final BooleanSetting circle = new BooleanSetting("Круг радиуса", true, "Контур радиуса взрыва");
    private final BooleanSetting filled = new BooleanSetting("Заполненная область", true, "Заливка зоны поражения");
    private final SliderSetting fillAlpha = new SliderSetting("Прозрачность области", 55f, 10f, 160f, 5f, () -> this.filled.get()).withDesc("Альфа заливки зоны");
    private final BooleanSetting impactLabel = new BooleanSetting("Метка попадания (текст)", true, "Текст с дистанцией/таймером в точке падения");

    private final SliderSetting maxSteps = new SliderSetting("Лимит тиков", 200f, 20f, 600f, 10f).withDesc("Макс. длина симуляции");
    private final SliderSetting launchSpeed = new SliderSetting("Скорость пуска РПГ", 8f, 1f, 30f, 0.5f, () -> this.inHand.get()).withDesc("Начальная скорость ракеты РПГ (подгон под игру)");
    private final SliderSetting handRadius = new SliderSetting("Радиус гранаты (в руке)", 5f, 1f, 30f, 0.5f, () -> this.inHand.get()).withDesc("Радиус взрыва для предикта гранаты в руке");

    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true, "Рисовать сквозь блоки");
    private final SliderSetting width = new SliderSetting("Толщина", 2f, 0.5f, 5f, 0.1f).withDesc("Толщина линий");

    // точные константы по декомпилу
    private static final float G_GRENADE = 0.05f;
    private static final float G_RPG = 0.015f;
    private static final String GRENADE_PKG = "com.atsuishio.superbwarfare.item.";
    private static final String RPG_CLASS = "com.atsuishio.superbwarfare.item.gun.launcher.RpgItem";

    private static final class Impact {
        final Vec3 at; final float radius; final String label; final List<Vec3> path;
        Impact(Vec3 at, float radius, String label, List<Vec3> path) { this.at = at; this.radius = radius; this.label = label; this.path = path; }
    }

    public SbwProjectilePredict() {
        addSettings(flying, inHand, arc, marker, circle, filled, fillAlpha, impactLabel,
                maxSteps, launchSpeed, handRadius, throughWalls, width);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;

        if (event instanceof EventRender3D) {
            boolean depth = !throughWalls.get();
            float w = width.get().floatValue();
            for (Impact im : compute()) drawGeometry(im, w, depth);
        }

        if (event instanceof EventRender2D e && impactLabel.get()) {
            for (Impact im : compute()) {
                if (im.label.isEmpty()) continue;
                Vector3d v = VectorUtil.toScreen(im.at.x, im.at.y + 0.3, im.at.z);
                if (v.z < 0 || v.z >= 1) continue;
                FontUtils.durman[13].centeredDraw(e.getMatrixStack(), im.label, (float) v.x, (float) v.y, Color.WHITE.getRGB());
            }
        }
    }

    private List<Impact> compute() {
        List<Impact> out = new ArrayList<>();
        int steps = maxSteps.get().intValue();

        if (flying.get()) {
            for (Entity en : mc.level.entitiesForRendering()) {
                if (!SbwAccess.isExplosive(en)) continue;
                float g = SbwAccess.gravity(en);                 // реальная гравитация снаряда (0.05 / 0.015)
                int fuse = SbwAccess.fuse(en);                    // >=0 у гранат
                boolean rocket = SbwAccess.isRpgRocket(en);
                SbwBallistics.Mode mode = rocket ? SbwBallistics.Mode.PROJECTILE_IMPACT
                        : (fuse >= 0 ? (SbwAccess.isRgo(en) ? SbwBallistics.Mode.GRENADE_IMPACT : SbwBallistics.Mode.GRENADE_BOUNCE)
                        : SbwBallistics.Mode.PROJECTILE_IMPACT);
                int cap = rocket ? Math.min(steps, 100) : steps;
                SbwBallistics.Result r = SbwBallistics.simulate(en, g, cap, fuse, mode, rocket);
                if (r == null) continue;
                String tag = SbwAccess.displayName(en);
                if (fuse >= 0) tag += String.format(" ⏱%.1fс", fuse / 20f); // таймер фитиля в полёте
                out.add(new Impact(r.landing, SbwAccess.explosionRadius(en), label(tag, r), r.path));
            }
        }
        if (inHand.get()) {
            Impact a = handImpact(mc.player.getMainHandItem(), steps);
            if (a != null) out.add(a);
            Impact b = handImpact(mc.player.getOffhandItem(), steps);
            if (b != null) out.add(b);
        }
        return out;
    }

    private Impact handImpact(ItemStack stack, int steps) {
        if (stack == null || stack.isEmpty()) return null;
        String cls = stack.getItem().getClass().getName();
        boolean rgo = cls.startsWith(GRENADE_PKG) && cls.contains("Rgo");
        boolean grenade = cls.startsWith(GRENADE_PKG) && cls.contains("Grenade");
        boolean rpg = cls.equals(RPG_CLASS);
        if (!grenade && !rpg) return null;

        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getViewVector(1f).normalize();

        if (grenade) {
            // параметры по типу гранаты (по декомпилу releaseUsing)
            float k = rgo ? 8f : 10f, cap = rgo ? 1.8f : 1.5f;
            int fullFuse = rgo ? 80 : 100;
            SbwBallistics.Mode mode = rgo ? SbwBallistics.Mode.GRENADE_IMPACT : SbwBallistics.Mode.GRENADE_BOUNCE;
            boolean using = mc.player.isUsingItem();
            int t = using ? mc.player.getTicksUsingItem() : -1;
            float power = (t < 0) ? cap : Math.min(t / k, cap);          // не зажато → превью на макс. силе
            int fuse = (t < 0) ? fullFuse : Math.max(0, fullFuse - t);
            Vec3 spawn = eye.subtract(0, 0.1, 0);                          // спавн как в моде (eyeY-0.1)
            Vec3 vel = look.scale(power);                                  // БЕЗ motion (velocityScale=0)
            SbwBallistics.Result r = SbwBallistics.simulate(spawn, vel, G_GRENADE, steps, fuse, mode, false, mc.player);
            if (r == null) return null;
            String tag = String.format("%s ⏱%.1fс%s", rgo ? "РГО" : "Граната", fuse / 20f, using ? "" : " (превью)");
            return new Impact(r.landing, handRadius.get().floatValue(), label(tag, r), r.path);
        } else {
            Vec3 vel = look.scale(launchSpeed.get().floatValue());
            int cap = Math.min(steps, 100);
            SbwBallistics.Result r = SbwBallistics.simulate(eye, vel, G_RPG, cap, -1, SbwBallistics.Mode.PROJECTILE_IMPACT, true, mc.player);
            if (r == null) return null;
            return new Impact(r.landing, handRadius.get().floatValue(), label("РПГ", r), r.path);
        }
    }

    private String label(String name, SbwBallistics.Result r) {
        if (!impactLabel.get()) return "";
        float dist = (float) mc.player.position().distanceTo(r.landing);
        return String.format("%s  %.0fм", name, dist);
    }

    private void drawGeometry(Impact im, float w, boolean depth) {
        int themed = 0xFF000000 | (ColorUtil.getColorStyle(90) & 0x00FFFFFF); // alpha=FF (иначе линия невидима)
        if (arc.get() && im.path != null && im.path.size() > 1) {
            Vec3 prev = null;
            for (Vec3 p : im.path) {
                if (prev != null && prev.distanceToSqr(p) > 1.0e-9) Render3DUtil.drawLine(prev, p, themed, w, depth);
                prev = p;
            }
        }
        Vec3 hit = im.at;
        if (marker.get()) {
            Render3DUtil.drawLine(hit, hit.add(0, 2.5, 0), themed, Math.max(w, 2.5f), false); // столб — всегда сквозь стены
            double s = 0.5;
            Render3DUtil.drawLine(hit.add(-s, 0.02, 0), hit.add(s, 0.02, 0), themed, w, false);
            Render3DUtil.drawLine(hit.add(0, 0.02, -s), hit.add(0, 0.02, s), themed, w, false);
        }
        if (im.radius > 0f) {
            int rgb = new Color(255, 60, 40).getRGB() & 0x00FFFFFF;
            if (filled.get()) drawDisc(hit, im.radius, (clampAlpha((int) fillAlpha.get().floatValue()) << 24) | rgb, depth);
            if (circle.get()) drawRing(hit, im.radius, w, (0xC0 << 24) | rgb, depth);
        }
    }

    private void drawRing(Vec3 c, float radius, float w, int color, boolean depth) {
        final int seg = Math.max(16, (int) (radius * 6));
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = (Math.PI * 2 * i) / seg;
            Vec3 p = c.add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
            if (prev != null) Render3DUtil.drawLine(prev, p, color, w, depth);
            prev = p;
        }
    }

    private void drawDisc(Vec3 c, float radius, int color, boolean depth) {
        final int seg = Math.max(16, (int) (radius * 6));
        Vec3 center = c.add(0, 0.04, 0);
        Vec3 prev = null;
        for (int i = 0; i <= seg; i++) {
            double a = (Math.PI * 2 * i) / seg;
            Vec3 p = center.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            if (prev != null) Render3DUtil.drawQuad(center, prev, p, center, color, depth);
            prev = p;
        }
    }

    private static int clampAlpha(int a) { return Math.max(0, Math.min(255, a)); }
}
