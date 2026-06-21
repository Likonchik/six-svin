package ru.levin.manager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.User;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Mth;
import ru.levin.ExosWare;
import ru.levin.manager.themeManager.StyleManager;
import ru.levin.mixin.iface.BossBarHudAccessor;
import ru.levin.mixin.iface.MinecraftClientAccessor;
import ru.levin.util.KeyMappings;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.math.MathUtil;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings("All")
public class ClientManager implements IMinecraft {
    public static boolean legitMode = false;
    private static float fps = 0;
    public static float TICK_TIMER = 1f;

    public static float getTPS() {
        return Math.round(Manager.SYNC_MANAGER.tps * 10) / 10F;
    }

    public static int getFps() {
        final Minecraft client = mc;
        final int currentFps = (client != null) ? client.getFps() : 0;
        fps = MathUtil.fast(fps, currentFps, 6);
        return Math.round(fps);
    }
    public static String getBps(Entity entity) {
        if (mc == null || mc.player == null) return "0.00";
        double dx = entity.getX() - entity.xo;
        double dz = entity.getZ() - entity.zo;
        return String.format(Locale.ROOT, "%.2f", Math.hypot(dx, dz) * 20.0D);
    }

    public static String getPing() {
        final Minecraft client = mc;
        if (client == null || client.player == null || client.getConnection() == null) {
            return "N/A";
        }
        var entry = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (entry == null) {
            return "N/A";
        }
        return Integer.toString(entry.getLatency());
    }

    public static float[] getHealthFromScoreboard(LivingEntity target) {
        float currentHealth = target.getHealth();
        float maxHealth = target.getMaxHealth();

        if (target instanceof Player player) {
            Scoreboard scoreboard = player.getScoreboard();
            Objective found = null;

            for (Objective objective : scoreboard.getObjectives()) {
                if (objective.getDisplayName().getString().contains("Здоровья")) {
                    found = objective;
                    break;
                }
            }

            if (found != null) {
                currentHealth = scoreboard.getOrCreatePlayerScore(player, found).get();
                maxHealth = 20;
            }
        }
        return new float[]{currentHealth, maxHealth};
    }

    public static String getKey(int keyCode) {
        return KeyMappings.keyMappings(keyCode);
    }

    public static boolean playerIsPVP() {
        if (mc == null || mc.gui == null) return false;

        BossHealthOverlay bossOverlayGui = mc.gui.getBossOverlay();
        Map<UUID, LerpingBossEvent> bossBars = ((BossBarHudAccessor) bossOverlayGui).getBossBars();

        for (LerpingBossEvent bossInfo : bossBars.values()) {
            String nameStrLower = bossInfo.getName().getString().toLowerCase(Locale.ROOT);
            if (nameStrLower.contains("pvp") || nameStrLower.contains("пвп")) {
                return true;
            }
        }
        return false;
    }

    public static void loginAccount(String name) {
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        User session = new User(name, uuid, "invalid_token", Optional.empty(), Optional.empty(), User.Type.MOJANG);
        Minecraft client = Minecraft.getInstance();
        ((MinecraftClientAccessor) client).setSession(session);
    }

    public static void message(String string) {
        if (mc == null || mc.player == null || mc.level == null || mc.gui == null) return;

        StyleManager theme = Manager.STYLE_MANAGER;
        int start = theme.getFirstColor();
        int end = theme.getSecondColor();

        mc.gui.getChat().addMessage(applyGradient(string, start, end));
    }

    public static String gradient(String message, int first, int end) {
        if (message == null || message.isEmpty()) return "";

        final int length = message.length();
        final StringBuilder result = new StringBuilder(length * 9);

        final float inv = (length <= 1) ? 0f : 1f / (length - 1);
        for (int i = 0; i < length; i++) {
            float progress = (length == 1) ? 0.5f : (i * inv);
            int color = ColorUtil.interpolateColor(first, end, progress) & 0xFFFFFF;

            String hex = Integer.toHexString(color);
            if (hex.length() < 6) {
                hex = "000000".substring(hex.length()) + hex;
            }

            result.append('§').append('#').append(hex).append(message.charAt(i));
        }
        return result.toString();
    }

    private static Component applyGradient(String string, int startColor, int endColor) {
        MutableComponent component = Component.empty();
        final String name = ExosWare.getInstance().name;
        final int length = name.length();
        final float inv = (length <= 1) ? 0f : 1f / (length - 1);

        for (int i = 0; i < length; i++) {
            int rgb = ColorUtil.blendColors(startColor, endColor, (length == 1) ? 0.5f : (i * inv)) & 0xFFFFFF;
            component.append(Component.literal(String.valueOf(name.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true)));
        }

        int gray = (java.awt.Color.GRAY.getRGB()) & 0xFFFFFF;
        component.append(Component.literal(" ➭ ").setStyle(Style.EMPTY.withColor(TextColor.fromRgb(gray)).withBold(true)));
        component.append(Component.literal(string).setStyle(Style.EMPTY.applyFormat(ChatFormatting.RESET)));

        return component;
    }
}