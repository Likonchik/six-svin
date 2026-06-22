package ru.levin.mixin.tacz;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.Manager;
import ru.levin.modules.combat.NoSway;

// No-Sway Hook 1 (главный): анимация бега/ходьбы выбирается тут — TickAnimationEvent.tickAnimation(ClientTickEvent.Pre)
// зовёт animationStateMachine.trigger(INPUT_RUN/WALK/IDLE) по isSprinting/getMoveVector. Форсим IDLE (как сам
// TACZ делает в покое) и отменяем родной обработчик -> ствол не качается при беге/ходьбе. Прицел НЕ ломается:
// позиционирование прицела — независимый путь (applyFirstPersonPositioningTransform/getClientAimingProgress).
// Метод ПЕРЕГРУЖЕН (есть ещё tickAnimation(RenderFrameEvent.Post)) -> ОБЯЗАТЕЛЕН полный дескриптор, иначе
// миксин молча промахнётся (defaultRequire=0). remap=false (классы TACZ — опц. зависимость).
@Mixin(value = com.tacz.guns.client.event.TickAnimationEvent.class, remap = false)
public class MixinTickAnimationEvent {

    @Inject(method = "tickAnimation(Lnet/neoforged/neoforge/client/event/ClientTickEvent$Pre;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onetap$forceIdle(CallbackInfo ci) {
        try {
            NoSway noSway = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.noSway : null;
            if (noSway == null || !noSway.steadyMovement()) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).ifPresent(d -> {
                var sm = d.getAnimationStateMachine();
                if (sm != null) sm.trigger(GunAnimationConstant.INPUT_IDLE);
            });
            ci.cancel();
        } catch (Throwable ignored) {}
    }
}
