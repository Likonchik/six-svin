package ru.levin.modules.render;

import net.minecraft.world.entity.Entity;
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
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.Render3DUtil;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.sbw.SbwAccess;
import ru.levin.util.vector.EntityPosition;
import ru.levin.util.vector.VectorUtil;

import java.awt.Color;
import java.util.List;

// ESP техники SuperbWarfare / VVP (танки/БТР/вертолёты/арта): обводка ПО МОДЕЛИ через OBB-части (башня/корпус/
// гусеницы/двигатель) — у техники не один AABB, а набор ориентированных боксов (getOBBs()→OBB.getVertices()).
// Рисуем рёбра каждой части (дёшево, только линии), цвет по части либо по HP; опц. заливка граней. Плюс подпись
// имя/HP/энергия. Детект техники по наличию getOBBs() → ловит и SBW, и VVP (общий базовый VehicleEntity).
@FunctionAnnotation(name = "SbwVehicleESP", keywords = {"Техника", "SBW", "VVP", "ESP", "Танк"}, desc = "ESP техники SBW/VVP: обводка по OBB-модели, HP, энергия", type = Type.Render)
public class SbwVehicleESP extends Function {

    // настоящий контур МОДЕЛИ (силуэт меша) через ванильный outline-буфер свечения — не хитбокс
    private final BooleanSetting contour = new BooleanSetting("Контур модели (силуэт)", true, "Обводка по силуэту модели техники (ванильный outline)");
    private final ModeSetting mode = new ModeSetting("Хитбокс-обводка", "Выкл", "Выкл", "OBB (по частям)", "AABB (рамка)", "2D рамка").withDesc("Доп. обводка по хитбоксу: OBB — по частям; AABB/2D — рамки");

    private static SbwVehicleESP INSTANCE;

    // читается ESP.isOutlineTarget — роутит технику в ванильный outline-буфер (контур модели)
    public static boolean outlineModelEnabled() {
        return INSTANCE != null && INSTANCE.state && INSTANCE.contour.get();
    }
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true, "Рисовать сквозь блоки");
    private final SliderSetting width = new SliderSetting("Толщина", 1.5f, 0.5f, 4f, 0.1f).withDesc("Толщина рёбер");
    // заливка СИЛУЭТА модели (chams) — не хитбокс-коробки; рисуется в MixinEntityRenderDispatcher
    private final BooleanSetting modelFill = new BooleanSetting("Заливка модели", false, "Заливка силуэта модели (chams), а не хитбокса");
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Свой", "По части", "По HP").withDesc("Тема клиента / свой цвет / по части / по HP");
    private final SliderSetting hue = new SliderSetting("Оттенок", 0f, 0f, 360f, 1f, () -> this.colorMode.is("Свой")).withDesc("Свой цвет (HSB-оттенок)");
    private final SliderSetting maxDist = new SliderSetting("Дистанция", 200f, 32f, 512f, 16f).withDesc("Макс. дистанция отрисовки (нагрузка)");

    private final BooleanSetting info = new BooleanSetting("Подпись", true, "Текст над техникой");
    private final BooleanSetting showName = new BooleanSetting("Имя", true, "Название", () -> this.info.get());
    private final BooleanSetting showHp = new BooleanSetting("HP", true, "Текущее/макс. HP", () -> this.info.get());
    private final BooleanSetting showEnergy = new BooleanSetting("Энергия", true, "Заряд", () -> this.info.get());

    // рёбра коробки по порядку вершин OBB.getVertices(): -z грань 0..3, +z грань 4..7
    private static final int[][] EDGES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int[][] FACES = {
            {0, 1, 5, 4}, {3, 2, 6, 7}, {0, 1, 2, 3}, {4, 5, 6, 7}, {0, 3, 7, 4}, {1, 2, 6, 5}
    };

    public SbwVehicleESP() {
        INSTANCE = this;
        addSettings(contour, modelFill, mode, throughWalls, width, colorMode, hue, maxDist, info, showName, showHp, showEnergy);
    }

    // цвет контура модели для ESP.getOutlineColor (тема/HP/часть → берём общий цвет техники)
    public static int outlineColor(Entity e) {
        return INSTANCE == null ? 0 : INSTANCE.colorFor(e, "", 0xFF);
    }

    // заливка силуэта (chams) техники — читается ESP.getFillColor / MixinEntityRenderDispatcher
    public static boolean modelFillEnabled() {
        return INSTANCE != null && INSTANCE.state && INSTANCE.modelFill.get();
    }
    public static int fillColor(Entity e) {
        return INSTANCE == null ? 0 : (0xB0000000 | (INSTANCE.colorFor(e, "", 0xFF) & 0x00FFFFFF)); // полупрозрачный тинт
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;
        double maxSq = maxDist.get().doubleValue() * maxDist.get().doubleValue();

        // ОПЦИОНАЛЬНЫЕ линии хитбокса (OBB по частям / AABB). Заливка модели = chams (отдельно, через миксин).
        boolean obbLines = mode.is("OBB");
        boolean aabbLines = mode.is("AABB");
        if (event instanceof EventRender3D && (obbLines || aabbLines)) {
            boolean depth = !throughWalls.get();
            float w = width.get().floatValue();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!SbwAccess.isVehicle(e)) continue;
                if (e.distanceToSqr(mc.player) > maxSq) continue;

                List<Object> obbs = aabbLines ? java.util.Collections.emptyList() : SbwAccess.obbList(e);
                boolean drewObb = false;
                for (Object obb : obbs) {
                    Vec3[] v = SbwAccess.obbVertices(obb);
                    if (v == null || v.length < 8) continue;
                    drewObb = true;
                    int color = colorFor(e, SbwAccess.obbPart(obb), 0xFF);
                    for (int[] ed : EDGES) Render3DUtil.drawLine(v[ed[0]], v[ed[1]], color, w, depth);
                }
                if (!drewObb) { // нет OBB или режим AABB — рамка по AABB
                    Render3DUtil.drawBox(e.getBoundingBox(), colorFor(e, "", 0xFF), w, true, false, depth);
                }
            }
        }

        if (event instanceof EventRender2D e) {
            float tickDelta = e.getDeltatick().getGameTimeDeltaPartialTick(true);
            boolean mode2d = mode.is("2D");
            for (Entity ent : mc.level.entitiesForRendering()) {
                if (!SbwAccess.isVehicle(ent)) continue;
                if (ent.distanceToSqr(mc.player) > maxSq) continue;

                // 2D-рамка: плотный экранный прямоугольник по проекции OBB-вершин (чистая линия по модели)
                if (mode2d) drawScreenBox(ent);

                if (info.get()) {
                    String text = buildInfo(ent);
                    if (!text.isEmpty()) {
                        Vector3d v = VectorUtil.toScreen(EntityPosition.get(ent, ent.getBbHeight() + 0.4f, tickDelta));
                        if (v.z >= 0 && v.z < 1)
                            FontUtils.durman[13].centeredDraw(e.getMatrixStack(), text, (float) v.x, (float) v.y, Color.WHITE.getRGB());
                    }
                }
            }
        }
    }

    // плотная 2D-рамка: собираем экранные проекции всех углов OBB (или AABB) и берём bounding-прямоугольник
    private void drawScreenBox(Entity ent) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        List<Object> obbs = SbwAccess.obbList(ent);
        if (!obbs.isEmpty()) {
            for (Object obb : obbs) {
                Vec3[] verts = SbwAccess.obbVertices(obb);
                if (verts == null) continue;
                for (Vec3 vv : verts) {
                    Vector3d s = VectorUtil.toScreen(vv.x, vv.y, vv.z);
                    if (s.z < 0 || s.z >= 1) continue;
                    minX = Math.min(minX, s.x); minY = Math.min(minY, s.y);
                    maxX = Math.max(maxX, s.x); maxY = Math.max(maxY, s.y);
                    any = true;
                }
            }
        }
        if (!any) { // фолбэк по AABB
            net.minecraft.world.phys.AABB bb = ent.getBoundingBox();
            double[][] c = {{bb.minX, bb.minY, bb.minZ}, {bb.maxX, bb.minY, bb.minZ}, {bb.maxX, bb.minY, bb.maxZ}, {bb.minX, bb.minY, bb.maxZ},
                    {bb.minX, bb.maxY, bb.minZ}, {bb.maxX, bb.maxY, bb.minZ}, {bb.maxX, bb.maxY, bb.maxZ}, {bb.minX, bb.maxY, bb.maxZ}};
            for (double[] p : c) {
                Vector3d s = VectorUtil.toScreen(p[0], p[1], p[2]);
                if (s.z < 0 || s.z >= 1) continue;
                minX = Math.min(minX, s.x); minY = Math.min(minY, s.y);
                maxX = Math.max(maxX, s.x); maxY = Math.max(maxY, s.y);
                any = true;
            }
        }
        if (!any) return;
        int color = colorFor(ent, "", 0xFF);
        RenderUtil.drawLine((float) minX, (float) minY, (float) maxX, (float) minY, color);
        RenderUtil.drawLine((float) maxX, (float) minY, (float) maxX, (float) maxY, color);
        RenderUtil.drawLine((float) maxX, (float) maxY, (float) minX, (float) maxY, color);
        RenderUtil.drawLine((float) minX, (float) maxY, (float) minX, (float) minY, color);
    }

    private String buildInfo(Entity ent) {
        StringBuilder sb = new StringBuilder();
        if (showName.get()) sb.append(SbwAccess.displayName(ent));
        if (showHp.get()) {
            float hp = SbwAccess.vehicleHealth(ent), max = SbwAccess.vehicleMaxHealth(ent);
            if (!Float.isNaN(hp)) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(Float.isNaN(max) ? String.format("%.0fHP", hp) : String.format("%.0f/%.0f", hp, max));
            }
        }
        if (showEnergy.get()) {
            int en = SbwAccess.vehicleEnergy(ent);
            if (en >= 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(String.format("⚡%d", en));
            }
        }
        return sb.toString();
    }

    private int colorFor(Entity ent, String part, int alpha) {
        if (colorMode.is("Тема")) return (alpha << 24) | (ColorUtil.getColorStyle(90) & 0x00FFFFFF);
        if (colorMode.is("Свой")) return (alpha << 24) | (Color.HSBtoRGB(hue.get().floatValue() / 360f, 1f, 1f) & 0x00FFFFFF);
        if (colorMode.is("HP")) return healthColor(ent, alpha);
        // По части (для силуэта/пустой части — тема клиента)
        String p = part.toUpperCase();
        int rgb;
        if (p.isEmpty()) rgb = ColorUtil.getColorStyle(90) & 0x00FFFFFF;
        else if (p.contains("TURRET")) rgb = 0xFF8C00;  // башня — оранжевый
        else if (p.contains("WHEEL")) rgb = 0x9E9E9E;   // гусеницы/колёса — серый
        else if (p.contains("ENGINE")) rgb = 0xFF3030;  // двигатель — красный
        else if (p.contains("BODY")) rgb = 0x30C0FF;    // корпус — голубой
        else rgb = 0xFFFFFF;
        return (alpha << 24) | rgb;
    }

    private int healthColor(Entity ent, int alpha) {
        float hp = SbwAccess.vehicleHealth(ent), max = SbwAccess.vehicleMaxHealth(ent);
        if (Float.isNaN(hp) || Float.isNaN(max) || max <= 0) return (alpha << 24) | 0x00AAAAAA;
        float f = Math.max(0f, Math.min(1f, hp / max));
        int r = (int) (255 * (1 - f)), g = (int) (255 * f);
        return (alpha << 24) | (r << 16) | (g << 8);
    }

    private static int clampAlpha(int a) {
        return Math.max(0, Math.min(255, a));
    }
}
