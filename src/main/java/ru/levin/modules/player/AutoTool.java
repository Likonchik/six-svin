package ru.levin.modules.player;

import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("All")
@FunctionAnnotation(name = "AutoTool", desc = "Берёт в руку лучший инструмент для ломания", type = Type.Player)
public class AutoTool extends Function {

    public static int itemIndex;
    private boolean swap;
    private long swapDelay;
    private final List<Integer> lastItem = new ArrayList<>();

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (!(mc.hitResult instanceof BlockHitResult)) return;

        BlockHitResult result = (BlockHitResult) mc.hitResult;
        if (result == null) return;

        BlockPos pos = result.getBlockPos();
        if (pos == null || mc.level.getBlockState(pos).isAir()) return;

        int toolSlot = getBest(pos);

        if (toolSlot != -1 && mc.options.keyAttack.isDown()) {
            lastItem.add(mc.player.getInventory().selected);
            mc.player.getInventory().selected = toolSlot;
            itemIndex = toolSlot;
            swap = true;
            swapDelay = System.currentTimeMillis();
        } else if (swap && !lastItem.isEmpty() && System.currentTimeMillis() >= swapDelay + 200) {
            mc.player.getInventory().selected = lastItem.get(0);
            itemIndex = lastItem.get(0);
            lastItem.clear();
            swap = false;
        }
    }

    public static int getBest(final BlockPos pos) {
        if (pos == null) return -1;

        int index = -1;
        float currentFastest = 1.0f;

        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == ItemStack.EMPTY) continue;
            if (stack.getMaxDamage() - stack.getDamageValue() <= 10) continue;

            float digSpeed = EnchantmentHelper.getItemEnchantmentLevel(
                    mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY),
                    stack);
            float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(pos));

            if (mc.level.getBlockState(pos).getBlock() instanceof AirBlock) continue;

            if (digSpeed + destroySpeed > currentFastest) {
                currentFastest = digSpeed + destroySpeed;
                index = i;
            }
        }

        return index;
    }
}
