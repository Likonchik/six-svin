package ru.levin.mixin.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.modules.render.ViewModel;

// TACZ guns render first-person via the library RenderHandEvent path and BYPASS vanilla
// ItemInHandRenderer.renderArmWithItem, so OneTap's MixinHeldItemRenderer (where the ViewModel translate
// lives) never runs for guns. Apply the ViewModel offset here at HEAD (view-space, before TACZ's own
// pushPose/flip), mirroring how SwingAnimations applies it before the equip/swing offsets. Guns are
// main-hand/right only -> right_* sliders. remap=false; graceful so it stays inert without TACZ.
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public class MixinGunItemRendererWrapper {

    @Inject(method = "renderFirstPersonInner", at = @At("HEAD"), remap = false)
    private void onapixApplyViewModel(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx,
                                      PoseStack poseStack, MultiBufferSource bufferSource,
                                      int light, float partialTick, CallbackInfo ci) {
        try {
            ViewModel vm = (Manager.FUNCTION_MANAGER != null) ? Manager.FUNCTION_MANAGER.viewModel : null;
            if (vm != null && vm.state) {
                poseStack.translate(vm.right_x.get().floatValue(),
                        vm.right_y.get().floatValue(),
                        vm.right_z.get().floatValue());
            }
        } catch (Throwable ignored) {}
    }
}
