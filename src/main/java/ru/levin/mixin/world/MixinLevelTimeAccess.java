package ru.levin.mixin.world;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelTimeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.Manager;
import ru.levin.modules.render.World;

// Настоящий чокпоинт времени для НЕБА: getTimeOfDay(float) -> dimensionType().timeOfDay(dayTime()).
// Солнце/звёзды/цвет неба/рассвет каждый кадр читают именно ЭТО, а НЕ getDayTime() (его читают только
// сложность/спавн) — поэтому хук getDayTime убирал мерцание лишь частично. getTimeOfDay — DEFAULT-метод
// интерфейса LevelTimeAccess (тело на интерфейсе, не на Level/ClientLevel) -> миксим интерфейс. Возвращаем
// дробь от выбранного времени тем же расчётом, что и ваниль -> значение неизменно между кадрами = нет
// мерцания, независимо от time-пакета и tickTime()'s +1. Гейт по isClientSide (серверный Level не трогаем).
@Mixin(LevelTimeAccess.class)
public interface MixinLevelTimeAccess {

    @Inject(method = "getTimeOfDay(F)F", at = @At("HEAD"), cancellable = true)
    private void onetap$forceTimeOfDay(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!(((Object) this) instanceof Level self)) return;
        if (!self.isClientSide) return;
        if (Manager.FUNCTION_MANAGER == null) return;
        World w = Manager.FUNCTION_MANAGER.customWorld;
        if (w != null && w.isTimeOverride()) {
            cir.setReturnValue(self.dimensionType().timeOfDay(w.resolveTime()));
        }
    }
}
