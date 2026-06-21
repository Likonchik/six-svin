package ru.levin.events.impl.render;

import lombok.Getter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import ru.levin.events.Event;

@Getter
public class EventHeldItemRenderer extends Event {
    private final InteractionHand hand;
    private final ItemStack item;
    private final float ep;
    private final PoseStack stack;

    public EventHeldItemRenderer(InteractionHand hand, ItemStack item, float equipProgress, PoseStack stack) {
        this.hand = hand;
        this.item = item;
        this.ep = equipProgress;
        this.stack = stack;
    }

}