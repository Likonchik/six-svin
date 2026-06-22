package ru.levin.mixin.display;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.modules.misc.AntiScreenshot;

import java.io.File;
import java.util.function.Consumer;

// Антискринилка. Перехватываем оба публичных Screenshot.grab (F2/моды) и прямой Screenshot.takeScreenshot.
// grab: отменяем немедленный (грязный) захват, AntiScreenshot гасит модули и переснимает чистый кадр через
// пару кадров (AntiScreenshot.onRenderEnd из GameRenderer.render TAIL). takeScreenshot: серверные «анти-читы»
// (напр. superbwarfare) тянут фреймбуфер напрямую в обход grab — отложить синхронный вызов нельзя, поэтому
// гасим модули и возвращаем null. Наш собственный повторный (чистый) grab помечен флагом capturing и проходит.
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

    // прямой захват фреймбуфера в обход grab (серверные анти-читы, напр. superbwarfare TargetSignalMessage).
    // Наш собственный (чистый) повторный grab помечен capturing -> пропускаем. Иначе гасим модули и глушим
    // захват, вернув null (вызывающая сторона трактует null как «снимок не получился»).
    @Inject(method = "takeScreenshot(Lcom/mojang/blaze3d/pipeline/RenderTarget;)Lcom/mojang/blaze3d/platform/NativeImage;", at = @At("HEAD"), cancellable = true)
    private static void onetap$takeScreenshot(RenderTarget framebuffer, CallbackInfoReturnable<NativeImage> cir) {
        if (!AntiScreenshot.capturing && AntiScreenshot.isActive()) {
            AntiScreenshot.panic();
            cir.setReturnValue(null);
        }
    }
}
