package ru.levin.mixin.util;

import net.minecraft.world.level.block.*;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.modules.player.CustomCoolDown;

import static ru.levin.manager.IMinecraft.mc;

@Mixin(MultiPlayerGameMode.class)
public class MixinClientPlayerInteractionManager {

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void cancelUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = player.getItemInHand(hand);
        CustomCoolDown coolDown = Manager.FUNCTION_MANAGER.customCoolDown;

        if (!coolDown.state) return;
        if (coolDown.PVPonly.get() && !ClientManager.playerIsPVP()) return;

        if (coolDown.isItemEnabled(stack.getItem()) && coolDown.lastUseMap.containsKey(stack.getItem())) {
            cir.setReturnValue(InteractionResult.FAIL);
            cir.cancel();
        }
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void interactBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Block bs = mc.level.getBlockState(hitResult.getBlockPos()).getBlock();
        if (Manager.FUNCTION_MANAGER.noInteract.state && (
                bs == Blocks.CHEST ||
                        bs == Blocks.TRAPPED_CHEST ||
                        bs == Blocks.FURNACE ||
                        bs == Blocks.ANVIL ||
                        bs == Blocks.CRAFTING_TABLE ||
                        bs == Blocks.HOPPER ||
                        bs == Blocks.JUKEBOX ||
                        bs == Blocks.NOTE_BLOCK ||
                        bs == Blocks.ENDER_CHEST ||
                        bs == Blocks.DISPENSER ||
                        bs == Blocks.DROPPER ||
                        bs == Blocks.LOOM ||
                        bs == Blocks.BEACON ||
                        bs == Blocks.SMITHING_TABLE ||
                        bs instanceof ShulkerBoxBlock ||
                        bs instanceof FenceBlock ||
                        bs instanceof FenceGateBlock ||
                        bs instanceof TrapDoorBlock)
                && (Manager.FUNCTION_MANAGER.attackAura.state || !Manager.FUNCTION_MANAGER.noInteract.onlyAura.get())) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}