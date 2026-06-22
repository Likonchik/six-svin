package ru.levin.modules.misc;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.core.BlockPos;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.TextSetting;
import ru.levin.util.player.TimerUtil;

@FunctionAnnotation(name = "TPLoot", desc = "Телепортирует на место, где лежат предметы", type = Type.Misc)
public class TPLoot extends Function {
    private final ModeSetting lootEnd = new ModeSetting("После лута", "Ничего", "Ничего", "/hub", "/spawn", "home", "Своя").withDesc("Действие после сбора лута");
    private final TextSetting text = new TextSetting("Команда", "/home home", () -> lootEnd.is("home")).withDesc("Команда телепорта домой");
    private final TextSetting custom = new TextSetting("Команда", "/test", () -> lootEnd.is("Своя")).withDesc("Своя команда телепорта");

    private final TimerUtil timerUtil = new TimerUtil();
    private boolean check;

    public TPLoot() {
        addSettings(lootEnd, text, custom);
    }

    private boolean isValuable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof SwordItem ||
                item instanceof PlayerHeadItem ||
                item instanceof ArmorItem ||
                item == Items.TOTEM_OF_UNDYING ||
                item == Items.ENDER_PEARL ||
                item == Items.END_CRYSTAL ||
                item == Items.FIREWORK_ROCKET ||
                item == Items.ELYTRA ||
                item == Items.GOLDEN_APPLE ||
                item == Items.ENCHANTED_GOLDEN_APPLE ||
                item == Items.CLAY_BALL ||
                item == Items.MAGMA_CREAM ||
                item == Items.CRYING_OBSIDIAN;
    }

    private boolean isHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValuable(stack)) return true;
        }
        return false;
    }

    private boolean isInv() {
        for (int i = 9; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValuable(stack)) return true;
        }
        return false;
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;

        for (Entity entity : Manager.SYNC_MANAGER.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            ItemStack stack = itemEntity.getItem();
            if (!isValuable(stack)) continue;

            double itemY = itemEntity.getY();
            BlockPos blockBelow = itemEntity.blockPosition().below();

            if (!mc.level.getBlockState(blockBelow).isAir() && itemY - blockBelow.getY() <= 1.0) {
                double x = itemEntity.getX() + 0.5;
                double y = itemEntity.getY();
                double z = itemEntity.getZ() + 0.5;
                mc.player.connection.send(
                        new ServerboundMovePlayerPacket.PosRot(x, y, z, mc.player.getYRot(), mc.player.getXRot(), false)
                );

                check = true;
            }
        }

        if (check && timerUtil.hasTimeElapsed(100)) {
            if (isHotbar() || isInv()) {
                switch (lootEnd.get()) {
                    case "/hub" -> mc.player.connection.sendChat("/hub");
                    case "/spawn" -> mc.player.connection.sendChat("/spawn");
                    case "home" -> mc.player.connection.sendChat(text.getValue());
                    case "Своя" -> mc.player.connection.sendChat(custom.getValue());
                }
                check = false;
                timerUtil.reset();
            }
        }
    }
}
