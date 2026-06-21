package ru.levin.mixin.display;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;

@Mixin(ScreenEffectRenderer.class)
public class MixinInGameOverlayRenderer {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void renderFireOverlayHook(Minecraft client, PoseStack matrices, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER.noRender.state && Manager.FUNCTION_MANAGER.noRender.mods.get("Огонь на экране")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWater", at = @At("HEAD"), cancellable = true)
    private static void renderUnderwaterOverlayHook(Minecraft client, PoseStack matrices, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER.noRender.state && Manager.FUNCTION_MANAGER.noRender.mods.get("Вода на экране")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderTex", at = @At("HEAD"), cancellable = true)
    private static void renderInWallOverlayHook(TextureAtlasSprite sprite, PoseStack matrices, CallbackInfo ci) {
        if (Manager.FUNCTION_MANAGER.noRender.state && Manager.FUNCTION_MANAGER.noRender.mods.get("Удушье")) {
            ci.cancel();
        }
    }
}