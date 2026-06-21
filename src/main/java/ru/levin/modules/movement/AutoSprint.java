package ru.levin.modules.movement;

import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "AutoSprint" ,desc  = "Автоматически включает бег", type = Type.Move)
public class AutoSprint extends Function {
    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            mc.options.keySprint.setDown(true);
        }
    }

    @Override
    protected void onDisable() {
        mc.options.keySprint.setDown(false);
        super.onDisable();
    }
}