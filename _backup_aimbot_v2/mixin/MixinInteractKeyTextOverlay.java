package ru.levin.mixin.tacz;

import com.tacz.guns.client.gui.overlay.InteractKeyTextOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Defensive guard for a TACZ NPE: InteractKeyTextOverlay.renderBlockText() calls
// player.level().getBlockState(blockHitResult.getBlockPos()) without null-checking the BlockPos,
// which crashes the HUD render thread. Skip the overlay when the pos is null.
@Mixin(value = InteractKeyTextOverlay.class, remap = false)
public class MixinInteractKeyTextOverlay {

    @Inject(method = "renderBlockText", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onapixRenderBlockText(GuiGraphics graphics, int width, int height, BlockHitResult blockHitResult,
                                              LocalPlayer player, Minecraft mc, CallbackInfo ci) {
        if (blockHitResult == null || blockHitResult.getBlockPos() == null) {
            ci.cancel();
        }
    }
}
