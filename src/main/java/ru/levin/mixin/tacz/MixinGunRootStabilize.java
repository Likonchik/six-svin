package ru.levin.mixin.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.modules.combat.NoSway;

// No-Sway Hook 3: перед вызовом applyFirstPersonGunTransform обнуляем сдвиги/поворот корневого узла модели
// ствола -> стираем look-around камера-инерцию и остаточную микро-анимацию. Вызов applyFirstPersonGunTransform
// лежит НЕ в renderFirstPersonInner, а в её лямбде lambda$renderFirstPersonInner$5 (проверено байткодом javap),
// туда и инжектим INVOKE+BEFORE. Не трогает shoot/jump (они добавляются ВНУТРИ applyFirstPersonGunTransform —
// это Hook 2). Безопасно/не накапливается: TACZ зовёт cleanAnimationTransform() каждый кадр. Модель достаём
// через TimelessAPI (без хрупких @Local/ordinal). remap=false.
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public class MixinGunRootStabilize {

    @Inject(
            method = "lambda$renderFirstPersonInner$5(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/LocalPlayer;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;ILcom/tacz/guns/client/resource/GunDisplayInstance;)V",
            at = @At(value = "INVOKE", target = "Lcom/tacz/guns/client/event/FirstPersonRenderGunEvent;applyFirstPersonGunTransform(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/tacz/guns/client/model/BedrockGunModel;F)V", shift = At.Shift.BEFORE),
            remap = false)
    private void onetap$stabilizeRoot(CallbackInfo ci) {
        try {
            NoSway noSway = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.noSway : null;
            if (noSway == null || !noSway.steadyMovement()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            TimelessAPI.getGunDisplay(mc.player.getMainHandItem())
                    .map(com.tacz.guns.client.resource.GunDisplayInstance::getGunModel)
                    .ifPresent(m -> {
                        var r = m.getRootNode();
                        if (r != null) {
                            r.offsetX = 0f;
                            r.offsetY = 0f;
                            r.offsetZ = 0f;
                            r.additionalQuaternion.identity();
                        }
                    });
        } catch (Throwable ignored) {}
    }

    // No-Sway Hook 4: камера-анимация TACZ двигает реальные углы камеры при выстреле/перезарядке
    // ("экран колбасит"). applyLevelCameraAnimation -> ViewportEvent.ComputeCameraAngles. Гасим по флагу.
    @Inject(method = "applyLevelCameraAnimation(Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/LocalPlayer;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onetap$noLevelCameraShake(CallbackInfo ci) {
        try {
            NoSway noSway = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.noSway : null;
            if (noSway != null && noSway.noCameraShake()) ci.cancel();
        } catch (Throwable ignored) {}
    }

    // applyItemInHandCameraAnimation — камера-анимация для рендера руки/ствола; гасим заодно.
    @Inject(method = "applyItemInHandCameraAnimation(Lcom/tacz/guns/api/client/event/BeforeRenderHandEvent;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/player/LocalPlayer;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void onetap$noHandCameraShake(CallbackInfo ci) {
        try {
            NoSway noSway = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.noSway : null;
            if (noSway != null && noSway.noCameraShake()) ci.cancel();
        } catch (Throwable ignored) {}
    }
}
