package ru.levin.mixin.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.events.Event;
import ru.levin.events.impl.world.EventObsidianPlace;

@Mixin(Block.class)
public class MixinBlock {

    @Inject(method = "setPlacedBy", at = @At("HEAD"))
    private void onPlaced(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack, CallbackInfo ci) {
        if (state.getBlock() == Blocks.OBSIDIAN)
            Event.call(new EventObsidianPlace(state.getBlock(),pos));

    }
}