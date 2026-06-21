package ru.levin.mixin.player;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventPlayerRender;
import ru.levin.manager.IMinecraft;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity> implements IMinecraft {
    @Unique
    private float originalPrevHeadYaw, originalHeadYaw, originalPrevHeadPitch, originalHeadPitch, originalBodyYaw, originalPrevBodyYaw;
    @Unique
    private boolean replaced;

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"))
    private void onRenderPre(T livingEntity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (mc == null || mc.player == null || livingEntity != mc.player) return;

        if (mc.screen instanceof InventoryScreen) return;

        EventPlayerRender playerRender = new EventPlayerRender(livingEntity);
        Event.call(playerRender);

        originalPrevHeadYaw = livingEntity.yHeadRotO;
        originalHeadYaw = livingEntity.yHeadRot;
        originalPrevHeadPitch = livingEntity.xRotO;
        originalHeadPitch = livingEntity.getXRot();
        originalBodyYaw = livingEntity.yBodyRot;
        originalPrevBodyYaw = livingEntity.yBodyRotO;

        livingEntity.yHeadRotO = playerRender.getPrevYaw();
        livingEntity.yHeadRot = playerRender.getYaw();
        livingEntity.xRotO = playerRender.getPrevPitch();
        livingEntity.setXRot(playerRender.getPitch());
        livingEntity.yBodyRotO = playerRender.getPrevBodyYaw();
        livingEntity.yBodyRot = playerRender.getBodyYaw();

        replaced = true;
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("TAIL"))
    private void onRenderPost(T livingEntity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        if (!replaced || mc == null || mc.player == null || livingEntity != mc.player) return;

        livingEntity.yHeadRotO = originalPrevHeadYaw;
        livingEntity.yHeadRot = originalHeadYaw;
        livingEntity.xRotO = originalPrevHeadPitch;
        livingEntity.setXRot(originalHeadPitch);
        livingEntity.yBodyRotO = originalPrevBodyYaw;
        livingEntity.yBodyRot = originalBodyYaw;

        replaced = false;
    }
}
