package ru.levin.modules.misc;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

import java.util.UUID;

@FunctionAnnotation(name = "ServerRPSpoff", desc = "", type = Type.Misc)
public class ServerRPSpoff extends Function {


    @Override
    public void onEvent(Event event) {
        if (event instanceof EventPacket packetEvent) {
            if (packetEvent.getPacket() instanceof ClientboundResourcePackPushPacket packet) {
                ClientPacketListener networkHandler = mc.getConnection();
                if (networkHandler != null) {
                    networkHandler.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
                    networkHandler.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                }

                event.setCancel(true);
            }
        }
    }
}