package ru.levin.modules.movement;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.move.NetworkUtils;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("All")
@FunctionAnnotation(name = "Phase", desc = "Позволяет ходить в блоках по x,z на ReallyWorld", type = Type.Move)
public class Phase extends Function {
    private final List<Packet<?>> bufferedPackets = new CopyOnWriteArrayList<>();

    private boolean semiPacketSent;
    private boolean skipReleaseOnDisable;

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) {
            toggle();
        }

        if (event instanceof EventPacket ep) {
            if (ep.isSendPacket()) {
                Packet<?> packet = ep.getPacket();
                if (packet instanceof ServerboundMovePlayerPacket) {
                    bufferedPackets.add(packet);
                    ep.setCancel(true);
                }
            }
        }

        if (event instanceof EventUpdate) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.0, mc.player.getDeltaMovement().z);

            AABB box = mc.player.getBoundingBox().inflate(0.001D);

            int minX = Mth.floor(box.minX);
            int minY = Mth.floor(box.minY);
            int minZ = Mth.floor(box.minZ);
            int maxX = Mth.floor(box.maxX);
            int maxY = Mth.floor(box.maxY);
            int maxZ = Mth.floor(box.maxZ);

            long totalStates = 0;
            long solidStates = 0;

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = mc.level.getBlockState(pos);

                        totalStates++;
                        if (state.isSolid()) {
                            solidStates++;
                        }
                    }
                }
            }

            boolean noSolidInAABB = solidStates == 0;
            boolean semiInsideBlock = solidStates > 0 && solidStates < totalStates;

            if (!semiPacketSent && semiInsideBlock) {
                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();
                float yaw = mc.player.getYRot();
                float pitch = mc.player.getXRot();
                boolean onGround = mc.player.onGround();

                for (int i = 0; i < 2; i++) {
                    mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, onGround));
                }
                semiPacketSent = true;
                return;
            }

            if (semiPacketSent && noSolidInAABB) {
                skipReleaseOnDisable = true;
                toggle();
            }
        }
    }

    @Override
    public void onDisable() {
        if (!skipReleaseOnDisable && semiPacketSent) {
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            float yaw = mc.player.getYRot();
            float pitch = mc.player.getXRot();
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(x - 5000, y, z - 5000, yaw, pitch, false));
            mc.player.connection.send(new ServerboundMovePlayerPacket.PosRot(x, y, z, yaw, pitch, mc.player.onGround()));
        }

        if (mc.player != null && mc.player.connection != null && !bufferedPackets.isEmpty()) {
            for (Packet<?> packet : bufferedPackets) {
                NetworkUtils.sendSilentPacket(packet);
            }
            bufferedPackets.clear();
        }

        super.onDisable();
    }

    @Override
    public void onEnable() {
        bufferedPackets.clear();
        semiPacketSent = false;
        skipReleaseOnDisable = false;
        super.onEnable();
    }
}
