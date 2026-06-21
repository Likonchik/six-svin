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
    private final BooleanSetting hitbox = new BooleanSetting("Хитбокс", true);
    private final BooleanSetting fill = new BooleanSetting("Заливка", true, () -> hitbox.get());
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true, () -> hitbox.get());
    private final SliderSetting width = new SliderSetting("Толщина линий", 1.5f, 0.5f, 4f, 0.1f);
    private final BooleanSetting outlineModel = new BooleanSetting("Обводка модели", true);

    private static final int FRIEND_COLOR = new Color(85, 255, 85).getRGB();

    public ESP() {
        INSTANCE = this;
        addSettings(targets, hitbox, fill, throughWalls, width, outlineModel);
    }

    // ===== Цвет темы (общий для хитбокса и обводки) =====
    private static int themeColor(Entity e) {
        if (e instanceof Player p && Manager.FRIEND_MANAGER.isFriend(p.getName().getString())) return FRIEND_COLOR;
        return ColorUtil.getColorStyle(90);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventRender3D e)) return;
        if (mc.options.hideGui || !hitbox.get()) return;

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
        ).inflate(0.06);

        // Фрустум-кулинг: невидимое не буферим.
        if (!Render3DUtil.canSee(box)) return;

        Render3DUtil.drawBox(box, color, width.get().floatValue(), true, fill.get(), depth);
    }

    private boolean shouldRender(Player entity) {
        if (entity == mc.player) {
            if (mc.options.getCameraType() == CameraType.FIRST_PERSON) return false;
            return targets.get("Меня");
        }
        if (targets.get("Друзей") && Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) {
            return true;
        }
        return targets.get("Игроков");
    }

    // ===== Хуки для ванильной обводки модели (вызываются из MixinMinecraftClient / MixinEntity) =====

    /** Должна ли сущность светиться (рендериться в outline-буфер) под нашу обводку. */
    public static boolean isOutlineTarget(Entity e) {
        ESP esp = INSTANCE;
        if (esp == null || !esp.state || !esp.outlineModel.get()) return false;
        if (e instanceof Player p) return esp.shouldRender(p);
        if (e instanceof ItemEntity) return esp.targets.get("Предметы");
        return false;
    }

    /** Цвет обводки модели (из темы клиента / зелёный для друзей). */
    public static int getOutlineColor(Entity e) {
        return themeColor(e);
    }
}
