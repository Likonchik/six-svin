package ru.levin.modules.render;

import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.Render3DUtil;

import java.awt.*;
import java.util.Arrays;

@SuppressWarnings("All")
@FunctionAnnotation(name = "ESP", desc = "Хитбокс + обводка по модельке", type = Type.Render)
public class ESP extends Function {

    private static ESP INSTANCE;

    private final MultiSetting targets = new MultiSetting(
            "Отображать",
            Arrays.asList("Игроков", "Друзей", "Меня"),
            new String[]{"Игроков", "Друзей", "Меня", "Предметы"}
    );
    private final ModeSetting boxMode = new ModeSetting("Тип бокса", "3D", "Нет", "3D", "Точный хитбокс").withDesc("Отображение позиции игрока");
    private final BooleanSetting fill = new BooleanSetting("Заливка", true, () -> !boxMode.get().equals("Нет"));
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true, () -> !boxMode.get().equals("Нет"));
    private final SliderSetting width = new SliderSetting("Толщина линий", 1.5f, 0.5f, 4f, 0.1f, () -> !boxMode.get().equals("Нет"));
    private final BooleanSetting outlineModel = new BooleanSetting("Обводка модели", true);
    private final BooleanSetting modelFill = new BooleanSetting("Заливка модели", false, "Заливка силуэта модели (chams), а не хитбокса");
    // красить ESP игрока в цвет его scoreboard-команды (если у команды задан цвет), иначе цвет темы
    private final BooleanSetting teamColor = new BooleanSetting("Цвет команды", true);
    // вообще не рендерить игроков своей scoreboard-команды (союзников)
    private final BooleanSetting hideTeam = new BooleanSetting("Скрывать свою команду", true);

    private static final int FRIEND_COLOR = new Color(85, 255, 85).getRGB();

    public ESP() {
        INSTANCE = this;
        addSettings(targets, boxMode, fill, throughWalls, width, outlineModel, modelFill, teamColor, hideTeam);
    }

    // ===== Цвет темы (общий для хитбокса и обводки) =====
    private static int themeColor(Entity e) {
        if (e instanceof Player p && Manager.FRIEND_MANAGER.isFriend(p.getName().getString())) return FRIEND_COLOR;
        // цвет scoreboard-команды игрока (если включено и у команды задан цвет) — иначе цвет темы
        ESP esp = INSTANCE;
        if (esp != null && esp.teamColor.get() && e instanceof Player) {
            net.minecraft.world.scores.Team team = e.getTeam();
            if (team != null) {
                net.minecraft.ChatFormatting cf = team.getColor();
                if (cf != null && cf.isColor() && cf.getColor() != null) {
                    return 0xFF000000 | cf.getColor();
                }
            }
        }
        return ColorUtil.getColorStyle(90);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventRender3D e)) return;
        if (mc.options.hideGui || boxMode.get().equals("Нет")) return;

        final float partial = e.getDeltatick().getGameTimeDeltaPartialTick(true);
        final boolean depth = !throughWalls.get();

        // Боксы копятся в батч-списки Render3DUtil и выливаются одним проходом в onWorldRender.
        for (Player player : Manager.SYNC_MANAGER.getPlayers()) {
            if (!shouldRender(player)) continue;
            renderBox(player, partial, themeColor(player), depth);
        }

        if (targets.get("Предметы")) {
            for (Entity entity : Manager.SYNC_MANAGER.getEntities()) {
                if (entity instanceof ItemEntity) {
                    renderBox(entity, partial, themeColor(entity), depth);
                }
            }
        }
    }

    private void renderBox(Entity ent, float partial, int color, boolean depth) {
        double x = Mth.lerp(partial, ent.xo, ent.getX());
        double y = Mth.lerp(partial, ent.yo, ent.getY());
        double z = Mth.lerp(partial, ent.zo, ent.getZ());

        AABB local = ent.getBoundingBox();
        AABB box = new AABB(
                x + (local.minX - ent.getX()), y + (local.minY - ent.getY()), z + (local.minZ - ent.getZ()),
                x + (local.maxX - ent.getX()), y + (local.maxY - ent.getY()), z + (local.maxZ - ent.getZ())
        );
        
        if (boxMode.get().equals("3D")) {
            box = box.inflate(0.06);
        }

        // Фрустум-кулинг: невидимое не буферим.
        if (!Render3DUtil.canSee(box)) return;

        Render3DUtil.drawBox(box, color, width.get().floatValue(), true, fill.get(), depth);
    }

    private boolean shouldRender(Player entity) {
        if (entity == mc.player) {
            if (mc.options.getCameraType() == CameraType.FIRST_PERSON) return false;
            return targets.get("Меня");
        }
        // своя команда: вообще не трекать (скрывать) союзников по scoreboard-команде
        if (hideTeam.get() && mc.player != null && mc.player.isAlliedTo(entity)) return false;
        if (targets.get("Друзей") && Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) {
            return true;
        }
        return targets.get("Игроков");
    }

    // ===== Хуки для ванильной обводки модели (вызываются из MixinMinecraftClient / MixinEntity) =====

    /** Должна ли сущность светиться (рендериться в outline-буфер) под нашу обводку. */
    public static boolean isOutlineTarget(Entity e) {
        // техника SBW/VVP — контур модели независимо от состояния ESP (управляется SbwVehicleESP)
        if (SbwVehicleESP.outlineModelEnabled() && ru.levin.util.sbw.SbwAccess.isVehicle(e)) return true;
        ESP esp = INSTANCE;
        if (esp == null || !esp.state || !esp.outlineModel.get()) return false;
        if (e instanceof Player p) return esp.shouldRender(p);
        if (e instanceof ItemEntity) return esp.targets.get("Предметы");
        return false;
    }

    /** Цвет заливки модели (chams) или 0, если сущность не цель заливки. Читается MixinEntityRenderDispatcher. */
    public static int getFillColor(Entity e) {
        // техника SBW/VVP — заливка силуэта модели (управляется SbwVehicleESP)
        if (SbwVehicleESP.modelFillEnabled() && ru.levin.util.sbw.SbwAccess.isVehicle(e)) {
            return SbwVehicleESP.fillColor(e);
        }
        // игроки — заливка модели по настройке ESP
        ESP esp = INSTANCE;
        if (esp != null && esp.state && esp.modelFill.get() && e instanceof Player p && esp.shouldRender(p)) {
            return 0xB0000000 | (themeColor(e) & 0x00FFFFFF); // полупрозрачный тинт силуэта
        }
        return 0;
    }

    /** Цвет обводки модели (из темы клиента / зелёный для друзей). */
    public static int getOutlineColor(Entity e) {
        if (ru.levin.util.sbw.SbwAccess.isVehicle(e)) {
            int c = SbwVehicleESP.outlineColor(e);
            if (c != 0) return c;
        }
        return themeColor(e);
    }
}
