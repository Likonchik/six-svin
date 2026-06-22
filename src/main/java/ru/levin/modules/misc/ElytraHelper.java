package ru.levin.modules.misc;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.ChatFormatting;
import ru.levin.modules.setting.BindSetting;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.input.EventKey;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.ClientManager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.player.InventoryUtil;

@FunctionAnnotation(name = "ElytraHelper", desc = "Быстрое взаимодействие с элитрой", type = Type.Misc)
public class ElytraHelper extends Function {

    private final BindSetting elytraKey = new BindSetting("Кнопка элитры", 0).withDesc("Клавиша свапа элитры");
    private final BindSetting fireworkKey = new BindSetting("Кнопка фейерверка", 0).withDesc("Клавиша использования фейерверка");
    private final BooleanSetting autoTakeoff = new BooleanSetting("Авто-взлёт", true, "Автоматический взлёт на элитре");

    private int takeoffTicks = 0;
    private boolean waitingToGlide = false;

    public ElytraHelper() {
        addSettings(elytraKey, fireworkKey, autoTakeoff);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate && mc.player != null) {
            if (autoTakeoff.get()) handleAutoTakeoff();
        }

        if (event instanceof EventKey e && mc.player != null) {
            ItemStack equipped = mc.player.getItemBySlot(EquipmentSlot.CHEST);
            if (e.key == elytraKey.getKey()) {
                int chestPlateSlot = findChestplate();
                int elytraSlot = findItemSlot(Items.ELYTRA);

                if (equipped.getItem() == Items.ELYTRA) {
                    if (chestPlateSlot != -1) {
                        swapSlots(chestPlateSlot, 6);
                        ClientManager.message("Свапнул на " + ChatFormatting.AQUA + "нагрудник");
                    } else {
                        swapSlots(elytraSlot == -1 ? 6 : elytraSlot, 6);
                        ClientManager.message(ChatFormatting.YELLOW + "Элитра снята (нагрудников нет)");
                    }
                } else {
                    if (elytraSlot != -1) {
                        swapSlots(elytraSlot, 6);
                        ClientManager.message("Свапнул на " + ChatFormatting.RED + "элитру");
                    } else {
                        ClientManager.message(ChatFormatting.RED + "Элитра не найдена!");
                    }
                }
            }

            if (e.key == fireworkKey.getKey() && equipped.getItem() == Items.ELYTRA) {
                InventoryUtil.inventorySwapClick2(Items.FIREWORK_ROCKET, true, false);
            }
        }
    }

    private void handleAutoTakeoff() {
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() != Items.ELYTRA) {
            waitingToGlide = false;
            takeoffTicks = 0;
            return;
        }

        if (mc.player.isFallFlying()) {
            waitingToGlide = false;
            takeoffTicks = 0;
            return;
        }
        if (mc.player.onGround() && !waitingToGlide) {
            mc.player.jumpFromGround();
            waitingToGlide = true;
            takeoffTicks = 0;
            return;
        }

        if (waitingToGlide) {
            takeoffTicks++;

            if (takeoffTicks >= 2 && mc.player.getDeltaMovement().y < -0.08 && !mc.player.isFallFlying()) {
                InventoryUtil.startFly();
                waitingToGlide = false;
                takeoffTicks = 0;
            }

            if (takeoffTicks > 10) {
                waitingToGlide = false;
                takeoffTicks = 0;
            }
        }
    }

    private int findItemSlot(Item item) {
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) return i;
        }
        return -1;
    }

    private int findChestplate() {
        Item[] chestplates = {
                Items.NETHERITE_CHESTPLATE,
                Items.DIAMOND_CHESTPLATE,
                Items.IRON_CHESTPLATE,
                Items.GOLDEN_CHESTPLATE,
                Items.CHAINMAIL_CHESTPLATE,
                Items.LEATHER_CHESTPLATE
        };
        for (Item item : chestplates) {
            int slot = findItemSlot(item);
            if (slot != -1) return slot;
        }
        return -1;
    }

    private void swapSlots(int from, int armorSlot) {
        int slot = from < 9 ? from + 36 : from;
        mc.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.SWAP, mc.player);
        mc.gameMode.handleInventoryMouseClick(0, armorSlot, 0, ClickType.SWAP, mc.player);
        mc.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.SWAP, mc.player);
    }

    @Override
    protected void onEnable() {
        ClientManager.message("Пожалуйста поставьте версию 1.17 в ViaFabricPlus, чтобы свапы работали корректно");
        super.onEnable();
    }
}
