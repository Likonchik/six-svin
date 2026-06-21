package ru.levin.modules.combat;

import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

/**
 * Убирает отдачу оружия TACZ. Сама логика — в миксине ru.levin.mixin.tacz.MixinCameraSetupEvent,
 * который отменяет CameraSetupEvent#applyCameraRecoil (там TACZ крутит player.setXRot/setYRot).
 * Пассивный модуль: миксин читает его state. Если TACZ не установлен — миксин-конфиг (required:false)
 * просто не применяется, модуль безвреден.
 */
@FunctionAnnotation(name = "GunNoRecoil", desc = "Убирает отдачу оружия TACZ", type = Type.Combat)
public class NoRecoil extends Function {
    @Override
    public void onEvent(Event event) {
    }
}
