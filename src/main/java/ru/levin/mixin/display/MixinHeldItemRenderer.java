package ru.levin.mixin.display;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.modules.render.ViewModel;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinHeldItemRenderer {
    @Inject(method = "renderArmWithItem", at = @At(value = "HEAD"), cancellable = true)
    private void onRenderItemHook(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (!(item.isEmpty()) && !(item.getItem() instanceof MapItem)) {
            ci.cancel();
            Manager.FUNCTION_MANAGER.swingAnimations.renderFirstPersonItem(player, tickDelta, pitch, hand, swingProgress, item, equipProgress, matrices, vertexConsumers, light);
        }
    }

    // ViewModel offset for the EMPTY hand (no item): the cancel path above skips empty hands, so the bare
    // arm renders via vanilla renderArmWithItem -> renderPlayerArm. Inject AFTER vanilla's own pushPose so
    // our translate lives inside its push/pop scope (auto-reverted) — a HEAD inject would leak the matrix.
    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER))
    private void onEmptyHandViewModel(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        if (!item.isEmpty()) return;
        ViewModel vm = Manager.FUNCTION_MANAGER.viewModel;
        if (vm == null || !vm.state) return;
        boolean right = (hand == InteractionHand.MAIN_HAND)
                ? player.getMainArm() == HumanoidArm.RIGHT
                : player.getMainArm().getOpposite() == HumanoidArm.RIGHT;
        if (right) {
            matrices.translate(vm.right_x.get().floatValue(), vm.right_y.get().floatValue(), vm.right_z.get().floatValue());
            matrices.mulPose(Axis.XP.rotationDegrees(vm.right_rot_x.get().floatValue()));
            matrices.mulPose(Axis.YP.rotationDegrees(vm.right_rot_y.get().floatValue()));
            matrices.mulPose(Axis.ZP.rotationDegrees(vm.right_rot_z.get().floatValue()));
            float s = vm.right_scale.get().floatValue();
            matrices.scale(s, s, s);
        } else {
            matrices.translate(-vm.left_x.get().floatValue(), vm.left_y.get().floatValue(), vm.left_z.get().floatValue());
            matrices.mulPose(Axis.XP.rotationDegrees(vm.left_rot_x.get().floatValue()));
            matrices.mulPose(Axis.YP.rotationDegrees(vm.left_rot_y.get().floatValue()));
            matrices.mulPose(Axis.ZP.rotationDegrees(vm.left_rot_z.get().floatValue()));
            float s = vm.left_scale.get().floatValue();
            matrices.scale(s, s, s);
        }
    }
}