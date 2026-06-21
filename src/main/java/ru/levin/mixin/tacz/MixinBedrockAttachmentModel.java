package ru.levin.mixin.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;

// NoVisualADS (overlay): renderOcularAndDivision draws the scope's black ocular mask + reticle through a
// stencil, with the lens radius scaled by aiming progress. Both the normal renderScope/renderSight/
// renderBoth and the AR-accelerated variants call it, so one HEAD-cancel removes the lens/vignette/reticle
// in all paths. The FOV zoom (CameraSetupEvent) is unaffected. remap=false; try/catch -> inert.
@Mixin(value = BedrockAttachmentModel.class, remap = false)
public class MixinBedrockAttachmentModel {

    @Inject(
            method = "renderOcularAndDivision(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/client/renderer/RenderType;IIZ)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void onapixSuppressScopeOverlay(PoseStack matrixStack, ItemDisplayContext transformType,
                                            RenderType renderType, int light, int overlay, boolean selective,
                                            CallbackInfo ci) {
        try {
            if (Manager.FUNCTION_MANAGER != null
                    && Manager.FUNCTION_MANAGER.noVisualAds != null
                    && Manager.FUNCTION_MANAGER.noVisualAds.suppressOverlay()) {
                ci.cancel();
            }
        } catch (Throwable ignored) {}
    }
}
