package ru.levin.modules.player;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import ru.levin.manager.ClientManager;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.TextSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "AutoRespawn" ,desc  = "Автоматически респавнит вас при смерти", type = Type.Player)
public class AutoRespawn extends Function {
    private BooleanSetting autohome = new BooleanSetting("Автоматически телепортироваться домой",true);
    private TextSetting home = new TextSetting("Название точки дома","home",() -> autohome.get());

    public AutoRespawn() {
        addSettings(autohome,home);
    }
    @Override
    public void onEvent(Event event) {
        if (event instanceof EventPacket eventPacket) {
            if (eventPacket.getPacket() instanceof ClientboundSystemChatPacket packet) {
                String message = packet.content().getString();

                if (message.contains("Вы были убиты") || message.contains("убиты") || message.contains("потеряно монет") || message.contains("потеряно") || message.contains("насмерть")) {
                    if (autohome.get()) {
                        mc.player.connection.sendChat("/home " + home.getValue());;
                    }
                }
            }
        }

        if (event instanceof EventUpdate eventUpdate) {
            if (mc.screen instanceof DeathScreen) {
                mc.player.respawn();
                mc.setScreen(null);
            }
        }
    }
}
