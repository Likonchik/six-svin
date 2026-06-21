package ru.levin.util.player;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import ru.levin.manager.IMinecraft;

import java.util.Locale;

public class ServerUtil implements IMinecraft {
    public static void selectCompass() {
        int slot = InventoryUtil.getHotBarSlot(Items.COMPASS);
        if (slot == -1) {
            return;
        }

        mc.player.getInventory().selected = slot;
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
    public static float getHealth(LivingEntity target) {
        if (mc.getCurrentServer() == null) {
            return target.getHealth() / target.getMaxHealth();
        }

        String serverAddress = mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
        boolean isLocal = mc.isLocalServer();

        if (isLocal || serverAddress.isEmpty()) {
            return target.getHealth() / target.getMaxHealth();
        }

        if (target instanceof Mob) {
            return target.getHealth() / target.getMaxHealth();
        }

        if (serverAddress.contains("reallyworld") || serverAddress.contains("playrw") || serverAddress.contains("saturn-x") || serverAddress.contains("skytime") || serverAddress.contains("space-times")) {
            Scoreboard scoreboard = target.level().getScoreboard();
            Objective scoreObjective = scoreboard.getDisplayObjective(DisplaySlot.BELOW_NAME);

            if (scoreObjective != null) {
                try {
                    int hp = scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(target.getScoreboardName()), scoreObjective).get();
                    if (hp >= 0 && hp <= target.getMaxHealth()) {
                        return (float) hp / target.getMaxHealth();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return target.getHealth() / target.getMaxHealth();
    }
    public static boolean isConnected(String ip) {
        if (mc.getCurrentServer() == null) return false;
        String serverAddress = mc.getCurrentServer().ip;
        return serverAddress != null && serverAddress.contains(ip);
    }

}
