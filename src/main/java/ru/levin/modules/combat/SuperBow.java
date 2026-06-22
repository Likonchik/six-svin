package ru.levin.modules.combat;


import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "SuperBow", type = Type.Combat,desc = "Увеличивает силу урона от лука")
public class SuperBow extends Function {

    private final SliderSetting power = new SliderSetting("Сила", 30, 1, 200, 1).withDesc("Множитель урона лука");

    public SuperBow() {
        addSettings(power);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;

        if (event instanceof EventPacket e) {
            if (e.getPacket() instanceof ServerboundPlayerActionPacket p) {
                if (p.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
                    mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.START_SPRINTING));
                    for (int i = 0; i < power.get().intValue(); i++) {
                        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() - 0.000000001, mc.player.getZ(), true));
                        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.000000001, mc.player.getZ(), true));

                    }
                }
            }
        }
    }
}
