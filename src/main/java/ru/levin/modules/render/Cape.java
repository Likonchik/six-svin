package ru.levin.modules.render;

import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

// Кастомный плащ клиента (рисуется на тебе и друзьях). Включён по умолчанию — как и было; выключи
// модуль, чтобы убрать плащ. Сам рендер делает ru.levin.mixin.world.MixinAbstractClientPlayerEntity,
// который теперь подменяет текстуру плаща только когда этот модуль включён.
@FunctionAnnotation(name = "Cape", keywords = {"Плащ", "Cape", "Накидка"}, desc = "Кастомный плащ клиента", type = Type.Render)
public class Cape extends Function {

    public Cape() {
        state = true; // плащ включён по умолчанию; модуль даёт возможность его отключить
    }

    @Override
    public void onEvent(Event event) {
    }
}
