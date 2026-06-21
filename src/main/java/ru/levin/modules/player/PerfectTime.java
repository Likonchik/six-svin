package ru.levin.modules.player;

import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "PerfectTime", desc = "Автоматически отпускает трезубец или арбалет, когда они полностью натянуты", type = Type.Player)
public class PerfectTime extends Function {

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate) || mc.player == null || !mc.player.isUsingItem()) return;
        ItemStack stack = mc.player.getMainHandItem();
        Item item = stack.getItem();
        int useTime = stack.getUseDuration(mc.player) - mc.player.getUseItemRemainingTicks();

        if (item instanceof TridentItem && useTime >= TridentItem.THROW_THRESHOLD_TIME) {
            releaseUse();
        } else if (item instanceof CrossbowItem && useTime >= stack.getUseDuration(mc.player) - 1) {
            releaseUse();
        }
    }

    private void releaseUse() {
        mc.player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
        mc.player.stopUsingItem();
    }
}
