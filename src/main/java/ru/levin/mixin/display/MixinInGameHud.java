package ru.levin.mixin.display;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.manager.commandManager.impl.GpsCommand;
import ru.levin.manager.commandManager.impl.WayPointCommand;
import ru.levin.modules.misc.AntiScreenshot;
import ru.levin.modules.render.CrossHair;

@Mixin(Gui.class)
public class MixinInGameHud {
    @Inject(at = @At(value = "HEAD"), method = "render")
    public void renderHook(GuiGraphics drawContext, DeltaTracker tickCounter, CallbackInfo ci) {
        RenderSystem.enableDepthTest();
        PoseStack matrices = drawContext.pose();
        if (!AntiScreenshot.hiding) {
            if (!ClientManager.legitMode) {
                GpsCommand.render(matrices);
                WayPointCommand.render(matrices);
            }

            Event.call(new EventRender2D(drawContext,matrices,tickCounter));
            if (Manager.FUNCTION_MANAGER.hud.state && Manager.FUNCTION_MANAGER.hud.setting.get("Notifications")) {
                Manager.NOTIFICATION_MANAGER.draw(drawContext);
            }
        }
        RenderSystem.disableDepthTest();
    }


    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void cancelVignette(GuiGraphics context, Entity entity, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;CROSSHAIR_SPRITE:Lnet/minecraft/resources/ResourceLocation;"), cancellable = true)
    public void renderCrosshairHook(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        CrossHair crossHair = Manager.FUNCTION_MANAGER.crossHair;
        if (crossHair.state) {
            crossHair.render(context);
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderEffects", cancellable = true)
    public void renderStatusEffectOverlay(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
       if (!ClientManager.legitMode) {
           ci.cancel();
       }
    }
}
