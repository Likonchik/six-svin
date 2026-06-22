package ru.levin.modules.movement;

import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.move.EventNoSlow;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.ModeSetting;

@FunctionAnnotation(name = "NoSlow", desc = "Предотвращает замедление при использовании предметов", type = Type.Move)
public class NoSlow extends Function {

    private final ModeSetting mode = new ModeSetting("Режим", "Grim", "Grim","ReallyWorld","LonyGrief").withDesc("Режим обхода замедления");

    private int ticks;

    public NoSlow() {
        addSettings(mode);
    }
    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            if (mode.is("ReallyWorld")) {
                if (!mc.player.isFallFlying()) {
                    if (mc.player.isUsingItem()) {
                        ticks++;
                    } else {
                        ticks = 0;
                    }
                }
            }
        }
        if (event instanceof EventNoSlow eventNoSlow) {
            if (mode.is("Grim")) {
                eventNoSlow.setCancel(true);
            } else if (mode.is("ReallyWorld")) {
                if (ticks == 1 || ticks == 2) {
                    eventNoSlow.setCancel(true);
                }
                if (ticks >= 2) {
                    ticks = 0;
                }
                if (ticks == 0) {
                    eventNoSlow.setCancel(false);
                }
            }
            if (mode.is("LonyGrief")) {
                InteractionHand active = mc.player.getUsedItemHand();
                if (active != null) {
                    InteractionHand opposite = (active == InteractionHand.MAIN_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                    mc.player.connection.send(new ServerboundUseItemPacket(opposite, 0, mc.player.getYRot(), mc.player.getXRot()));
                    eventNoSlow.setCancel(true);
                }
            }
        }
    }
}
