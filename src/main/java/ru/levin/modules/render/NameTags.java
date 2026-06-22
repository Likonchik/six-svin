package ru.levin.modules.render;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlayerHeadItem;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.joml.Vector3d;
import ru.levin.modules.setting.BooleanSetting;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.render.RenderAddon;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.providers.ResourceProvider;
import ru.levin.util.vector.EntityPosition;
import ru.levin.util.vector.VectorUtil;

import java.awt.*;
import java.util.*;

@FunctionAnnotation(name = "NameTags", desc = "Нейм таги", type = Type.Render)
public class NameTags extends Function {

    public final MultiSetting tags = new MultiSetting("Энтити", Arrays.asList("Игроки"), new String[]{"Игроки", "Предметы на земле"});
    private final BooleanSetting armorRender = new BooleanSetting("Показывать предметы", true, () -> tags.get("Игроки"));
    private final BooleanSetting effectRender = new BooleanSetting("Показывать эффекты", true, () -> tags.get("Игроки"));
    private final BooleanSetting enchantRender = new BooleanSetting("Показывать чары", true, () -> tags.get("Игроки"));
    private final BooleanSetting sphereRender = new BooleanSetting("Показывать Шары/Талисманы", true, () -> tags.get("Игроки"));
    private final BooleanSetting shulkerCheck = new BooleanSetting("Показывать содержимое шалкеров", true, () -> tags.get("Предметы на земле"));
    private final BooleanSetting gunESP = new BooleanSetting("Оружие врагов (TACZ)", true, () -> tags.get("Игроки"));
    // вообще не показывать нейм-таги игроков своей scoreboard-команды (союзников)
    private final BooleanSetting hideTeam = new BooleanSetting("Скрывать свою команду", true, () -> tags.get("Игроки"));
    private static final int BG_COLOR = new Color(30, 30, 30, 150).getRGB();

    public NameTags() {
        addSettings(tags, armorRender, effectRender,enchantRender, sphereRender,shulkerCheck, gunESP, hideTeam);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventRender2D e) {
            if (tags.get("Игроки")) renderPlayers(e);
            if (tags.get("Предметы на земле")) renderItems(e);
        }
    }

    private void renderPlayers(EventRender2D e) {
        final int screenW = mc.getWindow().getGuiScaledWidth();
        final int screenH = mc.getWindow().getGuiScaledHeight();
        final float tickDelta = e.getDeltatick().getGameTimeDeltaPartialTick(true);
        PoseStack matrixStack = e.getMatrixStack();

        for (Player player : Manager.SYNC_MANAGER.getPlayers()) {
            if (player == null || (player instanceof LocalPlayer && mc.options.getCameraType().isFirstPerson()))
                continue;
            // своя команда: вообще не трекать (скрывать) союзников по scoreboard-команде (кроме себя)
            if (hideTeam.get() && player != mc.player && mc.player != null && mc.player.isAlliedTo(player)) continue;

            Vector3d vec = VectorUtil.toScreen(EntityPosition.get(player, 2.0f, tickDelta));
            if (vec.z < 0 || vec.x < 0 || vec.x > screenW || vec.y < 0 || vec.y > screenH) continue;

            boolean isClientUser = false;

            String friendPrefix = Manager.FRIEND_MANAGER.isFriend(player.getName().getString()) ? ChatFormatting.GRAY + "[" + ChatFormatting.GREEN + "F" + ChatFormatting.GRAY + "] " : "";

            float health = player.getHealth() + player.getAbsorptionAmount();
            String hpText = ChatFormatting.GRAY + " [" + (health < 300 ? ChatFormatting.RED.toString() + (int) health : ChatFormatting.RED + "Unknown") + ChatFormatting.GRAY + "]" + ChatFormatting.RESET;

            String name = Manager.FUNCTION_MANAGER.nameProtect.getProtectedName(player.getGameProfile().getName());
            Component prefix = player.getTeam() != null ? player.getTeam().getPlayerPrefix() : Component.literal("");
            Component itemText = null;

            // Цвет ника из таба
            String nameColorCode = getTabNameColor(player);
            String coloredName = nameColorCode + name;


            String gunText = "";
            if (gunESP.get()) {
                ItemStack mainHand = player.getMainHandItem();
                if (!mainHand.isEmpty()) {
                    IGun iGun = null;
                    try {
                        iGun = IGun.getIGunOrNull(mainHand);
                    } catch (Throwable ignored) {}
                    if (iGun != null) {
                        net.minecraft.resources.ResourceLocation gunId = iGun.getGunId(mainHand);
                        java.util.Optional<ClientGunIndex> gunIndexOpt = TimelessAPI.getClientGunIndex(gunId);
                        String gunName = "Unknown";
                        int maxAmmo = 0;
                        if (gunIndexOpt.isPresent()) {
                            ClientGunIndex gunIndex = gunIndexOpt.get();
                            gunName = net.minecraft.client.resources.language.I18n.get(gunIndex.getName());
                            GunData gunData = gunIndex.getGunData();
                            if (gunData != null) {
                                maxAmmo = AttachmentDataUtils.getAmmoCountWithAttachment(mainHand, gunData);
                            }
                        }
                        int currentAmmo = iGun.getCurrentAmmoCount(mainHand);
                        gunText = " " + ChatFormatting.GOLD + "[" + gunName + " - " + currentAmmo + "/" + maxAmmo + "]";
                    } else {
                        // Generic modded gun/weapon check
                        net.minecraft.resources.ResourceLocation registryName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(mainHand.getItem());
                        if (registryName != null && !registryName.getNamespace().equals("minecraft")) {
                            String path = registryName.getPath().toLowerCase(java.util.Locale.ROOT);
                            String className = mainHand.getItem().getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
                            if (path.contains("gun") || path.contains("weapon") || path.contains("rifle") || path.contains("pistol") || path.contains("ammo") || path.contains("sword") ||
                                path.contains("grenade") || path.contains("rpg") || path.contains("launcher") || path.contains("blaster") || path.contains("sniper") || path.contains("carbine") ||
                                path.contains("shotgun") || path.contains("smg") ||
                                className.contains("gun") || className.contains("weapon") || className.contains("rifle") || className.contains("pistol") || className.contains("sword")) {
                                String displayName = mainHand.getHoverName().getString();
                                gunText = " " + ChatFormatting.GOLD + "[" + displayName + "]";
                            }
                        }
                    }
                }
            }

            if (sphereRender.get()) {
                ItemStack offHand = player.getOffhandItem();
                if (!offHand.isEmpty() && (offHand.getItem() == Items.TOTEM_OF_UNDYING || offHand.getItem() instanceof PlayerHeadItem)) {
                    Component customName = offHand.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
                    if (customName != null) {
                        itemText = customName;
                    }
                }
            }

            float iconWidth = isClientUser ? 12f : 0f;
            float friendWidth = mc.font.width(Component.literal(friendPrefix)) * 0.7f;
            float prefixWidth = mc.font.width(prefix) * 0.7f;
            float nameHpWidth = FontUtils.durman[13].getWidth(coloredName + hpText + gunText);
            float itemWidth = itemText != null ? mc.font.width(itemText) * 0.7f + 3f : 0f;

            float totalWidth = iconWidth + friendWidth + prefixWidth + nameHpWidth + itemWidth;

            float x = (float) vec.x - (totalWidth + 8f) / 2f;
            float y = (float) vec.y - 14f - 1f;

            RenderUtil.drawRoundedRect(matrixStack, x, y, totalWidth + 8f, 12f, 1.5f, BG_COLOR);

            PoseStack matrices = e.getDrawContext().pose();
            matrices.pushPose();
            matrices.translate(x + 4f, y + 3.2f, 0);
            matrices.scale(0.7f, 0.7f, 1.0f);

            int dx = 0;

            if (isClientUser) {
                RenderUtil.drawTexture(matrices, "images/hud/tags.png", dx, -2, 12, 12, 0, Color.white.getRGB());
                dx += iconWidth / 0.7f + 2;
            }

            if (!friendPrefix.isEmpty()) {
                mc.font.drawInBatch(Component.literal(friendPrefix), dx, 0, -1, false, matrices.last().pose(), mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
                dx += friendWidth / 0.7f;
            }

            mc.font.drawInBatch(prefix, dx, 0, -1, false, matrices.last().pose(), mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
            dx += prefixWidth / 0.7f;

            matrices.popPose();

            FontUtils.durman[13].drawLeftAligned(matrixStack, coloredName + hpText + gunText, x + 4f + iconWidth + friendWidth + prefixWidth, y + 1.8f, -1);

            if (itemText != null) {
                matrices.pushPose();
                matrices.translate(x + 4f + iconWidth + friendWidth + prefixWidth + nameHpWidth + 3f, y + 3.5f, 0);
                matrices.scale(0.7f, 0.7f, 1.0f);
                mc.font.drawInBatch(itemText, 0, 0, -1, false, matrices.last().pose(), mc.renderBuffers().bufferSource(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
                matrices.popPose();
            }

            if (effectRender.get()) renderEffect(e, player);
            if (armorRender.get()) renderPlayerItems(e, x + 5f, y, player);
        }
    }


    /**
     * Возвращает §-код цвета ника игрока из таб-листа.
     * Приоритет: tabListDisplayName style → цвет команды → белый (§f).
     */
    private String getTabNameColor(Player player) {
        // 1. Из таб-листа
        if (mc.getConnection() != null) {
            net.minecraft.client.multiplayer.PlayerInfo info =
                    mc.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) {
                Component tabName = info.getTabListDisplayName();
                if (tabName != null) {
                    net.minecraft.network.chat.TextColor textColor = tabName.getStyle().getColor();
                    if (textColor != null) {
                        int rgb = textColor.getValue();
                        // Ищем совпадение среди именованных §-кодов
                        for (net.minecraft.ChatFormatting fmt : net.minecraft.ChatFormatting.values()) {
                            if (fmt.isColor() && fmt.getColor() != null && fmt.getColor() == rgb) {
                                return fmt.toString();
                            }
                        }
                        // RGB-цвет без §-кода — берём ближайший
                        return nearestChatFormatting(rgb).toString();
                    }
                }
            }
        }
        // 2. Из команды
        if (player.getTeam() != null) {
            net.minecraft.ChatFormatting teamColor = player.getTeam().getColor();
            if (teamColor != null && teamColor.isColor()) return teamColor.toString();
        }
        // 3. Белый по умолчанию
        return ChatFormatting.WHITE.toString();
    }


    /** Находит ближайший §-цвет Minecraft для произвольного RGB */
    private static net.minecraft.ChatFormatting nearestChatFormatting(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        net.minecraft.ChatFormatting best = net.minecraft.ChatFormatting.WHITE;
        int bestDist = Integer.MAX_VALUE;
        for (net.minecraft.ChatFormatting fmt : net.minecraft.ChatFormatting.values()) {
            if (!fmt.isColor()) continue;
            Integer col = fmt.getColor();
            if (col == null) continue;
            int fr = (col >> 16) & 0xFF;
            int fg = (col >> 8) & 0xFF;
            int fb = col & 0xFF;
            int dist = (r - fr) * (r - fr) + (g - fg) * (g - fg) + (b - fb) * (b - fb);
            if (dist < bestDist) { bestDist = dist; best = fmt; }
        }
        return best;
    }

    private void renderEffect(EventRender2D e, Player player) {
        Vector3d footPos = VectorUtil.toScreen(EntityPosition.get(player, 0.0f, e.getDeltatick().getGameTimeDeltaPartialTick(true)));
        if (footPos.z < 0) return;

        int offsetY = 5;
        for (MobEffectInstance effect : player.getActiveEffects()) {
            String name = I18n.get(effect.getEffect().value().getDisplayName().getString());
            int lvl = effect.getAmplifier() + 1;
            int sec = effect.getDuration() / 20;
            String text = ChatFormatting.WHITE + name + (lvl > 1 ? " " + lvl : "") + ChatFormatting.WHITE + " | " + sec / 60 + ":" + String.format("%02d", sec % 60);

            FontUtils.durman[14].centeredDraw(e.getDrawContext().pose(), text, (float) footPos.x, (float) footPos.y + offsetY, Color.white.getRGB());
            offsetY += 9;
        }
    }

    private static final Set<String> IMPORTANT_ENCHANTS = Set.of(
            "Protection", "Защита",
            "Unbreaking", "Прочность",
            "Looting", "Добыча",
            "Fortune", "Удача",
            "Efficiency", "Эффективность",
            "Power", "Сила",
            "Feather Falling", "Невесомость",
            "Thorns", "Шипы",
            "Silk Touch", "Шёлковое касание",
            "Respiration", "Подводное дыхание",
            "Mending", "Починка",
            "Knockback", "Отдача",
            "Curse of Vanishing", "Проклятие утраты"
    );

    private void renderPlayerItems(EventRender2D e, float x, float y, Player player) {
        List<ItemStack> stacks = new ArrayList<>(6);
        stacks.add(player.getMainHandItem());
        player.getArmorSlots().forEach(stacks::add);
        stacks.add(player.getOffhandItem());
        stacks.removeIf(i -> i.isEmpty() || i.getItem() instanceof AirItem);

        float offset = 0;

        for (ItemStack stack : stacks) {
            RenderAddon.renderItem(e.getDrawContext(), stack, x + offset - 3f, y - 18f, 0.8f, true);

            if (enchantRender.get() && !stack.getEnchantments().isEmpty()) {
                List<Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>>> enchantments = new ArrayList<>(stack.getEnchantments().entrySet());

                enchantments.removeIf(entry -> {
                    Component name = Enchantment.getFullname(entry.getKey(), entry.getIntValue());
                    String full = name.getString();
                    return IMPORTANT_ENCHANTS.stream().noneMatch(full::contains);
                });

                if (!enchantments.isEmpty()) {
                    int totalHeight = enchantments.size() * 8;
                    int startY = (int) (y - 18f - totalHeight);

                    PoseStack matrices = e.getMatrixStack();
                    for (Object2IntMap.Entry<net.minecraft.core.Holder<Enchantment>> entry : enchantments) {
                        net.minecraft.core.Holder<Enchantment> regEntry = entry.getKey();
                        int level = entry.getIntValue();
                        Component enchantText = Enchantment.getFullname(regEntry, level);

                        String display = getShortName(enchantText, level);

                        matrices.pushPose();
                        matrices.translate(x + offset, startY, 0);
                        matrices.scale(0.7f, 0.7f, 1.0f);
                        FontUtils.durman[14].drawLeftAligned(e.getDrawContext().pose(),
                                display, 0, 0, Color.white.getRGB());
                        matrices.popPose();

                        startY += 8;
                    }
                }
            }

            offset += 15f;
        }
    }

    /**
     * Автоматическое сокращение чар
     */
    private String getShortName(Component description, int level) {
        String full = description.getString();
        String[] words = full.split(" ");
        String shortName;

        if (words.length == 1) {
            shortName = words[0].substring(0, Math.min(2, words[0].length())).toUpperCase();
        } else {
            shortName = "";
            for (String w : words) {
                if (!w.isEmpty()) shortName += w.charAt(0);
            }
            shortName = shortName.toUpperCase();
        }

        return shortName + " " + level;
    }

    private void renderItems(EventRender2D e) {
        final float tickDelta = e.getDeltatick().getGameTimeDeltaPartialTick(true);
        final int screenW = mc.getWindow().getGuiScaledWidth();
        final int screenH = mc.getWindow().getGuiScaledHeight();

        for (Entity entity : Manager.SYNC_MANAGER.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)) continue;

            ItemStack stack = itemEntity.getItem();
            Item item = stack.getItem();

            if (item instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock) {
                Vector3d vec = VectorUtil.toScreen(EntityPosition.get(entity, 0.6f, tickDelta));
                if (vec.z < 0 || vec.x < 0 || vec.x > screenW || vec.y < 0 || vec.y > screenH) continue;

                int offsetX = (int) vec.x;
                int offsetY = (int) vec.y;
                renderShulkerToolTip(e.getDrawContext(), offsetX, offsetY, stack);
            }

            Vector3d vec = VectorUtil.toScreen(EntityPosition.get(entity, 0.6f, tickDelta));
            if (vec.z < 0 || vec.x < 0 || vec.x > screenW || vec.y < 0 || vec.y > screenH) continue;

            String name = itemEntity.getName().getString();
            int count = stack.getCount();
            if (count > 1) name += " [x" + ChatFormatting.RED + count + ChatFormatting.WHITE +"]";

            float width = FontUtils.durman[13].getWidth(name);
            float totalWidth = width + 8f;
            float x = (float) vec.x - totalWidth / 2f;
            float y = (float) vec.y - 14f - 1f;

            RenderUtil.drawRoundedRect(e.getMatrixStack(), x, y, totalWidth, 12f, 1.5f, BG_COLOR);
            FontUtils.durman[13].drawLeftAligned(e.getDrawContext().pose(), name, x + 4f, y + 1.8f, -1);
        }
    }

    public boolean renderShulkerToolTip(GuiGraphics context, int offsetX, int offsetY, ItemStack stack) {
        ItemContainerContents compoundTag = stack.get(DataComponents.CONTAINER);
        if (compoundTag == null || compoundTag.copyOne().isEmpty()) return false;
        draw(context, compoundTag.stream().toList(), offsetX, offsetY);
        return true;
    }

    private void draw(GuiGraphics context, java.util.List<ItemStack> itemStacks, int offsetX, int offsetY) {
        final int columns = 9;
        final int rows = 3;
        final float itemSize = 16f;
        final float spacing = 0.1f;
        final int paddingX = 8;
        final int paddingY = 7;

        offsetX += paddingX;
        offsetY -= 82 - paddingY;

        int bgWidth = (int) (columns * itemSize + (columns - 1) * spacing + paddingX * 2);
        int bgHeight = (int) (rows * itemSize + (rows - 1) * spacing + paddingY * 2);
        RenderUtil.drawTexture(context.pose(), ResourceProvider.container, offsetX - paddingX, offsetY - paddingY, bgWidth, bgHeight, 0, -1);
        for (int index = 0; index < itemStacks.size(); index++) {
            int row = index / columns;
            int col = index % columns;

            float x = offsetX + col * (itemSize + spacing);
            float y = offsetY + row * (itemSize + spacing);

            RenderAddon.renderItem(context, itemStacks.get(index), x, y, 0.85f, true);
        }
    }
}
