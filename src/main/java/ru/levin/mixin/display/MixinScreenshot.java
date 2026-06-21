package ru.levin.mixin.display;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.modules.misc.AntiScreenshot;

import java.io.File;
import java.util.function.Consumer;

// Антискринилка: перехватываем оба публичных Screenshot.grab. Если модуль активен — отменяем немедленный
// захват (он снял бы грязный кадр со читом), а AntiScreenshot гасит модули и переснимает через пару чистых
// кадров (см. AntiScreenshot.onRenderEnd, вызываемый из GameRenderer.render TAIL). Наш собственный
// повторный grab помечен флагом capturing и проходит без перехвата.
@Mixin(Screenshot.class)
public class MixinScreenshot {

    @Inject(method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void onetap$grab(File gameDirectory, RenderTarget framebuffer, Consumer<Component> messageConsumer, CallbackInfo ci) {
        if (AntiScreenshot.interceptGrab(gameDirectory, null, framebuffer, messageConsumer)) ci.cancel();
    }

    @Inject(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V", at = @At("HEAD"), cancellable = true)
    private static void onetap$grabNamed(File gameDirectory, String name, RenderTarget framebuffer, Consumer<Component> messageConsumer, CallbackInfo ci) {
        if (AntiScreenshot.interceptGrab(gameDirectory, name, framebuffer, messageConsumer)) ci.cancel();
    }
}
