package ru.levin.modules.misc;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundMoveVehiclePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.player.EventPlayerTravel;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.move.MoveUtil;
import ru.levin.util.move.NetworkUtils;

import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

@FunctionAnnotation(name = "KTLeave", desc = "вы стали калестиум юзером", type = Type.Misc)
public class KTLeave extends Function {

    private final ModeSetting mode = new ModeSetting("Мод", "Motion", "Motion", "Packet").withDesc("Режим движения лодки");
    private final BooleanSetting phase = new BooleanSetting("Phase", false, "Проход сквозь блоки");
    private final BooleanSetting gravity = new BooleanSetting("Gravity", false, "Включить гравитацию");
    private final BooleanSetting automount = new BooleanSetting("AutoMount", false, "Авто-посадка в лодку");
    private final BooleanSetting allowShift = new BooleanSetting("AllowShift", false, "Разрешить шифт");

    private final SliderSetting speed = new SliderSetting("Speed", 2f, 0.0f, 25f, 0.1f).withDesc("Скорость движения");
    private final SliderSetting yspeed = new SliderSetting("YSpeed", 1f, 0.0f, 10f, 0.1f).withDesc("Скорость по вертикали");

    private final BooleanSetting cancel = new BooleanSetting("Cancel", false, "Отмена входящих пакетов");

    private final BooleanSetting stopunloaded = new BooleanSetting("StopUnloaded", false, "Стоп вне прогруженных чанков");
    private final BooleanSetting cancelrotations = new BooleanSetting("CancelRotations", false, "Отмена пакетов поворота");
    private final BooleanSetting limit = new BooleanSetting("Limit", false, "Ограничение пакетов");
    private final SliderSetting jitter = new SliderSetting("Jitter", 0.1f, 0.0f, 10f, 0.1f).withDesc("Дрожание позиции");
    private final BooleanSetting spoofpackets = new BooleanSetting("SpoofPackets", false, "Подмена пакетов");
    private final BooleanSetting ongroundpacket = new BooleanSetting("OnGroundPacket", false, "Телепорт лодки на землю");
    private final CopyOnWriteArrayList<ServerboundMoveVehiclePacket> vehiclePackets = new CopyOnWriteArrayList<>();

    private int ticksEnabled = 0;
    private int enableDelay = 0;
    private boolean waitedCooldown = false;
    private boolean returnGravity = false;
    private boolean jitterSwitch = false;

    public KTLeave() {
        addSettings(mode, phase, gravity, automount, allowShift, speed, yspeed,
                cancel, stopunloaded, cancelrotations, limit, jitter, spoofpackets, ongroundpacket);
    }

    private boolean fullNullCheck() {
        return mc.player == null || mc.level == null;
    }

    @Override
    public void onEnable() {
        if (fullNullCheck()) {
            toggle();
            return;
        }

        if (automount.get()) mountToBoat();
    }

    @Override
    public void onDisable() {
        vehiclePackets.clear();
        waitedCooldown = false;

        if (mc.player == null) return;

        if (phase.get() && mode.is("Motion")) {
            if (mc.player.getControlledVehicle() != null)
                mc.player.getControlledVehicle().noPhysics = false;
            mc.player.noPhysics = false;
        }
        if (mc.player.getControlledVehicle() != null)
            mc.player.getControlledVehicle().setNoGravity(false);
        mc.player.setNoGravity(false);
    }

    private float randomizeYOffset() {
        jitterSwitch = !jitterSwitch;
        return jitterSwitch ? jitter.get().floatValue() : -jitter.get().floatValue();
    }

    private void sendMovePacket(ServerboundMoveVehiclePacket pac) {
        vehiclePackets.add(pac);
        send(pac);
    }

    private void teleportToGround(Entity boat) {
        if (boat == null || mc.level == null) return;
        BlockPos blockPos = BlockPos.containing(boat.position());
        for (int i = 0; i < 255; ++i) {
            if (!mc.level.getBlockState(blockPos).canBeReplaced() || mc.level.getBlockState(blockPos).getBlock() == Blocks.WATER) {
                boat.setPos(boat.getX(), blockPos.getY() + 1, boat.getZ());
                sendMovePacket(new ServerboundMoveVehiclePacket(boat));
                boat.setPos(boat.getX(), boat.getY(), boat.getZ());
                break;
            }
            blockPos = blockPos.below();
        }
    }

    private void mountToBoat() {
        if (mc.player == null || mc.level == null) return;
        for (Entity entity : Manager.SYNC_MANAGER.getEntities()) {
            if (!(entity instanceof Boat) || mc.player.distanceToSqr(entity) > 25.0f) continue;
            send(ServerboundInteractPacket.createInteractionPacket(entity, false, InteractionHand.MAIN_HAND));
            break;
        }
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventPlayerTravel ev) {
            if (!ev.isPre() || fullNullCheck()) return;
            Entity entity = mc.player.getControlledVehicle();

            if (entity == null) {
                if (automount.get()) mountToBoat();
                return;
            }

            if (phase.get() && mode.is("Motion")) {
                entity.noPhysics = true;
                entity.setNoGravity(true);
                mc.player.noPhysics = true;
            }

            if (!returnGravity) {
                entity.setNoGravity(!gravity.get());
                mc.player.setNoGravity(!gravity.get());
            }

            if ((!mc.level.hasChunk((int) entity.getX() >> 4, (int) entity.getZ() >> 4) || entity.getY() < -60) && stopunloaded.get()) {
                returnGravity = true;
                return;
            }

            entity.setYRot(mc.player.getYRot());
            double[] boatMotion = MoveUtil.forward(speed.get().floatValue());
            double predictedX = entity.getX() + boatMotion[0];
            double predictedZ = entity.getZ() + boatMotion[1];
            double predictedY = entity.getY();

            if ((!mc.level.hasChunk((int) predictedX >> 4, (int) predictedZ >> 4) || predictedY < -60) && stopunloaded.get()) {
                returnGravity = true;
                return;
            }

            returnGravity = false;

            if (mode.is("Motion"))
                entity.setDeltaMovement(boatMotion[0], entity.getDeltaMovement().y, boatMotion[1]);

            if (mc.options.keyJump.isDown()) {
                if (mode.is("Motion"))
                    entity.setDeltaMovement(entity.getDeltaMovement().x, entity.getDeltaMovement().y + yspeed.get().floatValue(), entity.getDeltaMovement().z);
                else predictedY += yspeed.get().floatValue();
            } else if (mc.options.keyShift.isDown()) {
                if (mode.is("Motion"))
                    entity.setDeltaMovement(entity.getDeltaMovement().x, entity.getDeltaMovement().y - yspeed.get().floatValue(), entity.getDeltaMovement().z);
                else predictedY -= yspeed.get().floatValue();
            }

            if (!MoveUtil.isMoving()) entity.setDeltaMovement(0, entity.getDeltaMovement().y, 0);

            if (ongroundpacket.get()) teleportToGround(entity);

            if (mode.is("Packet")) {
                entity.setPos(predictedX, predictedY, predictedZ);
                sendMovePacket(new ServerboundMoveVehiclePacket(entity));
            }

            ev.setCancel(true);
            ++ticksEnabled;
        }

        if (event instanceof EventPacket eventPacket) {
            if (eventPacket.isReceivePacket()) {
                if (fullNullCheck()) return;
                if (eventPacket.getPacket() instanceof ClientboundDisconnectPacket) toggle();

                if (!mc.player.isPassenger() || returnGravity || waitedCooldown) return;

                if (cancel.get()) {
                    if (eventPacket.getPacket() instanceof ClientboundMoveVehiclePacket
                            || eventPacket.getPacket() instanceof ClientboundMoveEntityPacket
                            || eventPacket.getPacket() instanceof ClientboundSetEntityLinkPacket
                        || !(eventPacket.getPacket() instanceof ServerboundKeepAlivePacket)) {
                        eventPacket.setCancel(true);
                    }
                }
            }
        }

        if (event instanceof EventPacket eventPacket2) {
            if (eventPacket2.isSendPacket()) {
                if (fullNullCheck()) return;

                if ((eventPacket2.getPacket() instanceof ServerboundMovePlayerPacket.Rot && cancelrotations.get() || eventPacket2.getPacket() instanceof ServerboundPlayerInputPacket) && mc.player.isPassenger())
                    eventPacket2.setCancel(true);

                if (returnGravity && eventPacket2.getPacket() instanceof ServerboundMoveVehiclePacket)
                    eventPacket2.setCancel(true);

                if (eventPacket2.getPacket() instanceof ServerboundPlayerInputPacket && allowShift.get())
                    eventPacket2.setCancel(true);

                if (mc.player.getControlledVehicle() == null || returnGravity || waitedCooldown)
                    return;

                Vec3 boatPos = mc.player.getControlledVehicle().position();
                if ((!mc.level.hasChunk((int) boatPos.x >> 4, (int) boatPos.z >> 4) || boatPos.y < -60) && stopunloaded.get())
                    return;

                if (eventPacket2.getPacket() instanceof ServerboundMoveVehiclePacket pac && limit.get() && mode.is("Packet")) {
                    if (vehiclePackets.contains(pac)) vehiclePackets.remove(pac);
                    else eventPacket2.setCancel(true);
                }
            }
        }
    }

    private void send(Packet<?> packet) {
        if (mc.player != null && mc.player.connection != null)
            NetworkUtils.sendSilentPacket(packet);
    }
}
