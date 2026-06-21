package ru.levin.mixin.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.render.ItemPhysic;

import static ru.levin.manager.IMinecraft.mc;

@Mixin(ItemEntityRenderer.class)
public abstract class MixinItemEntityRenderer implements IMinecraft {

    @Inject(
            method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/ItemEntityRenderer;renderMultipleFromCount(Lnet/minecraft/client/renderer/entity/ItemRenderer;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/resources/model/BakedModel;ZLnet/minecraft/util/RandomSource;)V"
            )
    )
    private void onRender(ItemEntity entity, float entityYaw, float partialTick, PoseStack matrices, MultiBufferSource vertexConsumers, int light, CallbackInfo ci) {
        ItemPhysic itemPhysic = Manager.FUNCTION_MANAGER.itemPhysic;
        if (!itemPhysic.state) return;

        if (itemPhysic.mode.is("Обычная")) {
            matrices.popPose();
            matrices.pushPose();
            float spin = entity.getSpin(partialTick);
            if (entity.onGround()) {
                matrices.mulPose(Axis.XP.rotationDegrees(90));
            } else {
                matrices.mulPose(Axis.XP.rotationDegrees(spin * 300));
            }
        } else if (itemPhysic.mode.is("2D")) {
            matrices.popPose();
            matrices.pushPose();
            matrices.translate(0.0F, 0.10F, 0.0F);

            matrices.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            matrices.scale(1.1F, 1.1F, 0.0F);
        }
    }
}
