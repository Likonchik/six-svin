package ru.levin.modules.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.NonNullList;
import net.minecraft.core.HolderLookup;
import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "ShulkerPreview", desc = "Просмотр содержимого шалкеров", type = Type.Render)
public class ShulkerPreview extends Function {

    private final Minecraft mc = Minecraft.getInstance();

    @Override
    public void onEvent(Event event) {
    }

    private boolean isShulkerBox(ItemStack stack) {
        return stack.is(Items.SHULKER_BOX)
                || stack.is(Items.WHITE_SHULKER_BOX)
                || stack.is(Items.ORANGE_SHULKER_BOX)
                || stack.is(Items.MAGENTA_SHULKER_BOX)
                || stack.is(Items.LIGHT_BLUE_SHULKER_BOX)
                || stack.is(Items.YELLOW_SHULKER_BOX)
                || stack.is(Items.LIME_SHULKER_BOX)
                || stack.is(Items.PINK_SHULKER_BOX)
                || stack.is(Items.GRAY_SHULKER_BOX)
                || stack.is(Items.LIGHT_GRAY_SHULKER_BOX)
                || stack.is(Items.CYAN_SHULKER_BOX)
                || stack.is(Items.PURPLE_SHULKER_BOX)
                || stack.is(Items.BLUE_SHULKER_BOX)
                || stack.is(Items.BROWN_SHULKER_BOX)
                || stack.is(Items.GREEN_SHULKER_BOX)
                || stack.is(Items.RED_SHULKER_BOX)
                || stack.is(Items.BLACK_SHULKER_BOX);
    }

    private NonNullList<ItemStack> getItems(ItemStack stack) {
        NonNullList<ItemStack> list = NonNullList.withSize(27, ItemStack.EMPTY);

        HolderLookup.Provider registries = Minecraft.getInstance()
                .getConnection()
                .registryAccess();

        Tag nbtElement = stack.saveOptional(registries);

        if (!(nbtElement instanceof CompoundTag compound)) return list;

        if (!compound.contains("BlockEntityTag", 10)) return list;

        CompoundTag blockEntityTag = compound.getCompound("BlockEntityTag");

        if (!blockEntityTag.contains("Items", 9)) return list;

        ListTag nbtList = blockEntityTag.getList("Items", 10);

        for (int i = 0; i < nbtList.size(); i++) {
            CompoundTag nbt = nbtList.getCompound(i);
            int slot = nbt.getByte("Slot") & 255;
            if (slot < list.size()) {
                list.set(slot, ItemStack.parseOptional(registries, nbt));
            }
        }

        return list;
    }


    private void drawPreview(GuiGraphics context, NonNullList<ItemStack> items, int x, int y) {
        ItemRenderer itemRenderer = mc.getItemRenderer();
        RenderSystem.enableBlend();

        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            if (s.isEmpty()) continue;

            int row = i / 9;
            int col = i % 9;
            int px = x + col * 18;
            int py = y + row * 18;

            context.renderItem(s, px, py);
        }

        RenderSystem.disableBlend();
    }
}
