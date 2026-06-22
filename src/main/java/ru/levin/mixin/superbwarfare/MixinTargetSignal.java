package ru.levin.mixin.superbwarfare;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.modules.misc.AntiScreenshot;

// Точечный перехват серверной «анти-чит» скриналки мода superbwarfare. Класс TargetSignalMessage по команде
// сервера (пакет playToClient) снимает либо фреймбуфер игры, либо ВЕСЬ рабочий стол через java.awt.Robot
// (reason="TargetImaging") и шлёт JPEG обратно на сервер пакетами RadarSignalDataMessage. Если антискринилка
// включена — гасим модули и полностью отменяем обработчик, так что никакого захвата (ни игры, ни десктопа)
// не происходит, и на сервер ничего не уходит.
//
// targets+remap=false: имена не обфусцированы (NeoForge рантайм = Mojmap, это собственный член мода).
// required=false / defaultRequire=0 в exosware.superbwarfare.mixins.json: мод есть только на этом сервере,
// при его отсутствии mixin молча пропускается и не валит старт.
@Mixin(targets = "com.atsuishio.superbwarfare.network.message.receive.TargetSignalMessage", remap = false)
public class MixinTargetSignal {

    @Inject(method = "f(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private static void onetap$blockCapture(String reason, CallbackInfo ci) {
        if (AntiScreenshot.isActive()) {
            AntiScreenshot.panic();
            ci.cancel();
        }
    }
}
