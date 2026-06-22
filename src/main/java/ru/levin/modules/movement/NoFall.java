package ru.levin.modules.movement;

import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.SliderSetting;

// Пакетный onGround-спуф: при падении выше порога отправляем onGround=true в движение, чтобы сервер
// не насчитывал урон от падения. EventMotion.setOnGround влияет именно на отправляемый пакет.
// Строгий re-simulating античит (Grim и т.п.) такое ловит — это и есть проверка fall-чеков.
@FunctionAnnotation(name = "NoFall", keywords = {"NoFall", "Падение"}, desc = "Спуф onGround при падении (тест fall-чеков)", type = Type.Move)
public class NoFall extends Function {

    private final SliderSetting minFall = new SliderSetting("Мин. падение", 2.0, 0, 5, 0.5).withDesc("Порог высоты для спуфа");

    public NoFall() {
        addSettings(minFall);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventMotion motion)) return;
        if (mc.player == null) return;
        if (mc.player.getAbilities().flying || mc.player.isFallFlying()) return;
        if (mc.player.fallDistance > minFall.get().floatValue()) {
            motion.setOnGround(true);
        }
    }
}
