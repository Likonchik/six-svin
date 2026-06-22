package ru.levin.modules.combat;

import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;

// No-Sway (TACZ): делает ствол от первого лица устойчивым. Важно: TACZ САМ отменяет ванильный view-bob для
// любого оружия (FirstPersonRenderGunEvent.cancelItemInHandViewBobbing) и рисует СВОЮ анимацию — поэтому
// гасить ванильный bob бесполезно (так не работало раньше). Реальное качание убирают 3 миксина по флагам:
//   - steady -> MixinTickAnimationEvent (форс idle вместо анимации run/walk = ГЛАВНЫЙ источник качания при беге)
//              + MixinGunRootStabilize (обнуляет look-around инерцию корневого узла модели).
//   - noShootJumpSway -> MixinApplyGunMovements (отменяет процедурную отдачу-качание + качание прыжка/приземления).
// Прицел и зум не трогаются (отдельный код-путь). Пассивный модуль: всю работу делают миксины. Без TACZ
// просто бездействует. См. mem fakelag-shoot-resolution / tacz-features.
@FunctionAnnotation(name = "NoSway", keywords = {"NoSway", "Качание", "Стабилизация", "Steady"}, desc = "Устойчивый ствол TACZ: убирает качание при беге/прыжке/выстреле", type = Type.Combat)
public class NoSway extends Function {

    private final BooleanSetting steady = new BooleanSetting("Стабилизация (бег/ходьба)", true, "Убрать качание ствола при беге и ходьбе");
    private final BooleanSetting noShootJumpSway = new BooleanSetting("Без качания (выстрел/прыжок)", true, "Убрать качание ствола от отдачи и прыжка");
    private final BooleanSetting noCamShake = new BooleanSetting("Без тряски камеры", true, "Убрать тряску экрана при выстреле и перезарядке");

    public NoSway() {
        addSettings(steady, noShootJumpSway, noCamShake);
    }

    // читается MixinTickAnimationEvent (форс idle) и MixinGunRootStabilize (обнуление инерции)
    public boolean steadyMovement() {
        return state && steady.get();
    }

    // читается MixinApplyGunMovements (отмена отдачи/прыжка)
    public boolean suppressGunMovements() {
        return state && noShootJumpSway.get();
    }

    // читается MixinGunRootStabilize (отмена камера-анимации TACZ: тряска при отдаче/перезарядке)
    public boolean noCameraShake() {
        return state && noCamShake.get();
    }

    @Override
    public void onEvent(Event event) {
    }
}
