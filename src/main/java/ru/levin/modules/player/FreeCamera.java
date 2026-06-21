package ru.levin.modules.player;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import ru.levin.events.impl.input.EventKeyBoard;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.EventPacket;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.math.MathUtil;
import ru.levin.util.move.MoveUtil;

@FunctionAnnotation(name = "FreeCamera",desc = "Свободная камера", type = Type.Player)
public class FreeCamera extends Function {
    private final SliderSetting speed = new SliderSetting("X - Скорость", 1f, 0.1f, 3f,0.1f);
    private final SliderSetting yspeed = new SliderSetting("Y - Скорость", 0.42f, 0.1f, 3f,0.1f);

    private float fakeYaw, fakePitch, prevFakeYaw, prevFakePitch;
    private double fakeX, fakeY, fakeZ, prevFakeX, prevFakeY, prevFakeZ;
    public LivingEntity trackEntity;
    private Vec3 freezePosition = Vec3.ZERO;

    public FreeCamera() {
        addSettings(speed,yspeed);
    }
    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) {
            toggle();
        }
        if (event instanceof EventPacket eventPacket) {
            if (eventPacket.getPacket() instanceof ServerboundMovePlayerPacket) {
                eventPacket.setCancel(true);
            }
        }
        if (event instanceof EventKeyBoard) {
            if (mc.player == null) return;

            if (trackEntity == null) {

                double[] motion = MoveUtil.forward(speed.get().floatValue());

                prevFakeX = fakeX;
                prevFakeY = fakeY;
                prevFakeZ = fakeZ;

                fakeX += motion[0];
                fakeZ += motion[1];

                if (mc.options.keyJump.isDown())
                    fakeY += yspeed.get().floatValue();

                if (mc.options.keyShift.isDown())
                    fakeY -= yspeed.get().floatValue();
            }

            mc.player.input.forwardImpulse = 0;
            mc.player.input.leftImpulse = 0;
        }
        if (event instanceof EventMotion eventMotion) {
            if (mc.player != null && freezePosition != Vec3.ZERO) {
                eventMotion.setCancel(true);
                mc.player.setPos(freezePosition);
                mc.player.setDeltaMovement(Vec3.ZERO);
            }

            prevFakeYaw = fakeYaw;
            prevFakePitch = fakePitch;

            if (trackEntity != null) {
                fakeYaw = trackEntity.getYRot();
                fakePitch = trackEntity.getXRot();

                prevFakeX = fakeX;
                prevFakeY = fakeY;
                prevFakeZ = fakeZ;

                fakeX = trackEntity.getX();
                fakeY = trackEntity.getY() + trackEntity.getEyeHeight(trackEntity.getPose());
                fakeZ = trackEntity.getZ();
            } else {
                fakeYaw = mc.player.getYRot();
                fakePitch = mc.player.getXRot();
            }
        }
        if (event instanceof EventMotion eventMove) {
            eventMove.setX(0.);
            eventMove.setY(0.);
            eventMove.setZ(0.);
            eventMove.setCancel(true);
        }
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            freezePosition = mc.player.position();
        }

        mc.smartCull = false;
        trackEntity = null;

        fakePitch = mc.player.getXRot();
        fakeYaw = mc.player.getYRot();

        prevFakePitch = fakePitch;
        prevFakeYaw = fakeYaw;

        fakeX = mc.player.getX();
        fakeY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
        fakeZ = mc.player.getZ();

        prevFakeX = mc.player.getX();
        prevFakeY = mc.player.getY();
        prevFakeZ = mc.player.getZ();
    }
    @Override
    public void onDisable() {
        mc.smartCull = true;
    }

    public float getFakeYaw() {
        return (float) interpolate(prevFakeYaw, fakeYaw, mc.getTimer().getGameTimeDeltaPartialTick(true));
    }

    public float getFakePitch() {
        return (float) interpolate(prevFakePitch, fakePitch, mc.getTimer().getGameTimeDeltaPartialTick(true));
    }

    public double getFakeX() {
        return MathUtil.interpolate(prevFakeX, fakeX, mc.getTimer().getGameTimeDeltaPartialTick(true));
    }

    public double getFakeY() {
        return MathUtil.interpolate(prevFakeY, fakeY, mc.getTimer().getGameTimeDeltaPartialTick(true));
    }

    public double getFakeZ() {
        return MathUtil.interpolate(prevFakeZ, fakeZ, mc.getTimer().getGameTimeDeltaPartialTick(true));
    }
    public static double interpolate(double oldValue, double newValue, double interpolationValue) {
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }
}