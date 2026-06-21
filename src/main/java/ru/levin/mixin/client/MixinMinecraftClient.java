package ru.levin.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.ExosWare;
import ru.levin.manager.*;
import ru.levin.modules.render.ESP;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftClient implements IMinecraft {
    @Inject(method = "allowsMultiplayer", at = @At("HEAD"), cancellable = true)
    private void isMultiplayerEnabled(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void getWindowTitle(CallbackInfoReturnable<String> cir) {
        // createTitle() fires during Minecraft.<init> before the @Mod ctor sets USER_PROFILE — guard it.
        if (!ClientManager.legitMode && Manager.USER_PROFILE != null) {
            cir.setReturnValue("OneTap 1.21.1 NeoForge | " + Manager.USER_PROFILE.getName());
        }
    }
    @Inject(at = @At("HEAD"), method = "stop")
    private void stop(CallbackInfo ci) {
        ExosWare.getInstance().shutDown();
    }
    @Inject(method = "<init>", at = @At("TAIL"))
    private void init(CallbackInfo callbackInfo) {
        ExosWare.getInstance().init();
    }

    // ESP «Обводка модели»: форсим ванильное свечение на наших целях, чтобы они попали в outline-буфер.
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void onShouldEntityGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ESP.isOutlineTarget(entity)) {
            cir.setReturnValue(true);
        }
    }
}
