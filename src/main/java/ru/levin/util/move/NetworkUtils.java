package ru.levin.util.move;

import net.minecraft.network.protocol.Packet;
import ru.levin.manager.IMinecraft;

public class NetworkUtils {
    private static boolean sendingSilent = false;
    public static void sendSilentPacket(Packet<?> packet) {
        try {
            sendingSilent = true;
            IMinecraft.mc.player.connection.send(packet);
        } finally {
            sendingSilent = false;
        }
    }

    public static void sendPacket(Packet<?> packet) {
        IMinecraft.mc.player.connection.send(packet);
    }

    public static boolean isSendingSilent() {
        return sendingSilent;
    }
}
