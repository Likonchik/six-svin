package ru.levin.mixin.tacz;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.modules.render.ViewModel;

// ViewModel на TACZ-стволах. Они рисуются через FirstPersonRenderGunEvent.applyFirstPersonGunTransform, минуя
// ванильный ItemInHandRenderer (где наш MixinHeldItemRenderer применяет ViewModel). На HEAD PoseStack (arg 3)
// уже в перевёрнутом model-фрейме ствола (после translate(0,1.5,0)+Z180 в renderFirstPersonInner). Дальше
// позиционирование прицела идёт через mulPose() (КОМПОЗИЦИЯ) -> наш HEAD-трансформ остаётся ВНЕШНИМ фреймом
// и двигает/крутит/масштабирует весь ствол (idle+ADS) без поломки прицела. Полный набор: translate + поворот
// X/Y/Z + масштаб (как ванильный путь). Стволы только main-hand -> right_* слайдеры. remap=false; грейсфул.
// Заменяет старый MixinGunItemRendererWrapper (тот делал только translate в неверном фрейме).
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public class MixinApplyFirstPersonGunTransform {

    @Inject(
            method = "applyFirstPersonGunTransform(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/item/ItemStack;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/tacz/guns/client/model/BedrockGunModel;F)V",
            at = @At("HEAD"),
            remap = false)
    private static void onetap$applyViewModel(net.minecraft.client.player.LocalPlayer player,
                                              net.minecraft.world.item.ItemStack gunItemStack,
                                              PoseStack poseStack,
                                              com.tacz.guns.client.model.BedrockGunModel model,
                                              float partialTicks,
                                              CallbackInfo ci) {
        try {
            ViewModel vm = (Manager.FUNCTION_MANAGER != null) ? Manager.FUNCTION_MANAGER.viewModel : null;
            if (vm == null || !vm.weaponOn()) return;          // отдельная группа «Оружие»
            poseStack.translate(vm.gun_x.get().floatValue(),
                    vm.gun_y.get().floatValue(),
                    vm.gun_z.get().floatValue());
            poseStack.mulPose(Axis.XP.rotationDegrees(vm.gun_rot_x.get().floatValue()));
            poseStack.mulPose(Axis.YP.rotationDegrees(vm.gun_rot_y.get().floatValue()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(vm.gun_rot_z.get().floatValue()));
            float s = vm.gun_scale.get().floatValue();
            poseStack.scale(s, s, s);
        } catch (Throwable ignored) {}
    }
}
