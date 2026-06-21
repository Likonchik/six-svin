package ru.levin.mixin.tacz;

import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;

// When OneTap's CrossHair module is on, TACZ must NOT replace the crosshair. TACZ's handler cancels the
// vanilla CROSSHAIR layer while a gun is held (RenderCrosshairEvent.onRenderCrosshair -> setCanceled(true)),
// which also kills OneTap's crosshair (drawn from MixinInGameHud's inject inside Gui.renderCrosshair).
// Cancelling TACZ's handler at HEAD stops that: the vanilla layer proceeds -> Gui.renderCrosshair runs ->
// OneTap draws its own crosshair. TACZ's reticle AND hit-marker (same handler) are suppressed. remap=false.
@Mixin(value = RenderCrosshairEvent.class, remap = false)
public class MixinRenderCrosshairEvent {

    @Inject(method = "onRenderCrosshair", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onapixRenderCrosshair(RenderGuiLayerEvent.Pre event, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER != null
                && Manager.FUNCTION_MANAGER.crossHair != null
                && Manager.FUNCTION_MANAGER.crossHair.state) {
            ci.cancel();
        }
    }
}
