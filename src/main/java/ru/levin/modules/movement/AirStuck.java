package ru.levin.modules.movement;

import net.minecraft.world.phys.Vec3;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;

@FunctionAnnotation(name = "AirStuck", desc = "Позволяет зависать в воздухе", type = Type.Move)
public class AirStuck extends Function {
    private BooleanSetting packet = new BooleanSetting("Отменять движение",true,"Отменяет пакет на движения для сервер");
    private Vec3 freezePosition = Vec3.ZERO;

    public AirStuck() {
        addSettings(packet);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            freezePosition = mc.player.position();
        }
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventMotion eventMotion) {
            if (mc.player != null && freezePosition != Vec3.ZERO) {
                if (packet.get()) {
                    eventMotion.setCancel(true);
                }
                mc.player.setPos(freezePosition);
                mc.player.setDeltaMovement(Vec3.ZERO);
            }
        }
    }
}