package ru.levin.mixin.player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;

import ru.levin.manager.Manager;

@SuppressWarnings("All")
@Mixin(PlayerRenderer.class)
public class MixinPlayerEntityRenderer {
    @Inject(method = "renderNameTag", at = @At("HEAD"), cancellable = true)
    private void renderNameTag(AbstractClientPlayer player, Component component, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick, CallbackInfo callbackInfo) {
        if (Manager.FUNCTION_MANAGER.nameTags.state && Manager.FUNCTION_MANAGER.nameTags.tags.get("Игроки")) {
            callbackInfo.cancel();
        }
    }
}
