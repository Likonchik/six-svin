package ru.levin.mixin.world;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.Manager;
import ru.levin.modules.render.World;

// Фикс мерцания времени у модуля World. Причина блинка раз в ~1с: сервер шлёт ClientboundSetTimePacket
// каждые 20 тиков и перетирает dayTime; между приходом пакета и следующим тиком модуля рендер успевает
// показать СЕРВЕРНОЕ время -> мигание. Решение: делаем getDayTime() авторитетным — пока модуль активен,
// КЛИЕНТСКИЙ Level всегда возвращает выбранное время, поэтому ни тайм-синк-пакет, ни tickTime()'s +1 не
// меняют то, что видят небо/солнце/свет (они каждый кадр читают getTimeOfDay()->getDayTime()). Гейт по
// isClientSide -> серверный Level (интегрированный сервер в синглплеере) не трогаем, меняем только визуал.
// getDayTime объявлен в Level (ClientLevel его не переопределяет) -> миксим Level, иначе инжект промахнётся.
@Mixin(Level.class)
public class MixinLevel {

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void onetap$forceDayTime(CallbackInfoReturnable<Long> cir) {
        Level self = (Level) (Object) this;
        if (!self.isClientSide) return;                 // только клиентский рендер-уровень
        if (Manager.FUNCTION_MANAGER == null) return;   // защита до bootstrap
        World w = Manager.FUNCTION_MANAGER.customWorld;
        if (w != null && w.isTimeOverride()) {
            cir.setReturnValue(w.resolveTime());
        }
    }
}
