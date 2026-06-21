package ru.levin.mixin.display;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.events.Event;
import ru.levin.events.impl.world.EventFog;
import ru.levin.manager.Manager;


@SuppressWarnings("All")
@Mixin(FogRenderer.class)
public class MixinBackgroundRenderer {

    // 1.21.4 getFogModifier(...)->StatusEffectFogModifier became 1.21.1 getPriorityFogFunction(...)->MobEffectFogFunction.
    // Returning null disables the blindness/darkness effect fog (caller null-checks the result).
    @Inject(method = "getPriorityFogFunction(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/FogRenderer$MobEffectFogFunction;", at = @At("HEAD"), cancellable = true)
    private static void onGetFogModifier(Entity entity, float tickDelta, CallbackInfoReturnable<Object> info) {
        if (Manager.FUNCTION_MANAGER.noRender.state && Manager.FUNCTION_MANAGER.noRender.mods.get("Плохие эффекты"))
            info.setReturnValue(null);
    }

    // 1.21.1: setupFog(...) returns void and mutates RenderSystem fog state at the TAIL (no Fog record).
    // Re-author the 1.21.4 applyFog @ModifyReturnValue by overriding the shader fog state after vanilla sets it.
    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/FogRenderer$FogMode;FZF)V", at = @At("TAIL"))
    private static void modifyFog(Camera camera, FogRenderer.FogMode fogMode, float viewDistance, boolean thickenFog, float tickDelta, CallbackInfo ci) {
        EventFog fogEvent = new EventFog();
        Event.call(fogEvent);
        if (fogEvent.modified) {
            RenderSystem.setShaderFogStart(fogEvent.start);
            RenderSystem.setShaderFogEnd(fogEvent.end);
            RenderSystem.setShaderFogShape(fogEvent.shape);
            RenderSystem.setShaderFogColor(fogEvent.r, fogEvent.g, fogEvent.b, fogEvent.alpha);
        }
    }
}
