package ru.levin.modules.movement;

import net.minecraft.client.CameraType;
import ru.levin.modules.setting.BindSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.input.EventKey;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.movement.freelook.FreeLookState;
import ru.levin.manager.ClientManager;

@FunctionAnnotation(name = "FreeLook", desc = "Позволит вращать камеру, при этом не меняя направления движения", type = Type.Move)
public class FreeLook extends Function {
    private final BindSetting bind = new BindSetting("Кнопка", 0);
    private CameraType previousPerspective;

    public FreeLook() {
        addSettings(bind);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventKey keyEvent)) return;
        if (keyEvent.key != bind.getKey()) return;
        if (mc == null || mc.options == null) return;
        var attackAura = Manager.FUNCTION_MANAGER.attackAura;
        if (attackAura != null && attackAura.state && attackAura.target != null) {
            ClientManager.message("Нельзя использовать с " + attackAura.name);
            return;
        }

        FreeLookState.active = !FreeLookState.active;

        if (FreeLookState.active) {
            previousPerspective = mc.options.getCameraType();
            if (previousPerspective != CameraType.THIRD_PERSON_FRONT) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
        } else {
            mc.options.setCameraType(previousPerspective != null ? previousPerspective : CameraType.FIRST_PERSON);
        }
    }
    @Override
    public void onDisable() {
        FreeLookState.active = false;
        if (mc != null && mc.options != null) {
            mc.options.setCameraType(previousPerspective != null ? previousPerspective : CameraType.FIRST_PERSON);
        }
        previousPerspective = null;
    }

}