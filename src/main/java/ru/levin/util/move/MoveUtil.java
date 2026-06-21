package ru.levin.util.move;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import ru.levin.events.impl.input.EventKeyBoard;
import ru.levin.manager.IMinecraft;

public class MoveUtil implements IMinecraft {
    public static boolean isMoving() {
        return mc.player != null && mc.level != null && mc.player.input != null && (mc.player.input.forwardImpulse != 0.0 || mc.player.input.leftImpulse != 0.0);
    }
    public static boolean isInWeb() {
        AABB box = mc.player.getBoundingBox();
        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++) {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++) {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++) {
                    BlockPos blockPos = new BlockPos(x, y, z);
                    if (mc.level.getBlockState(blockPos).is(Blocks.COBWEB)) {
                        AABB blockBox = new AABB(blockPos);
                        if (blockBox.intersects(box)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public static void setSpeed(float motion) {
        float forward = mc.player.input.forwardImpulse;
        float strafe = mc.player.input.leftImpulse;
        float yaw = mc.player.getYRot();

        if (forward == 0 && strafe == 0) {
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
        } else {
            if (forward != 0) {
                if (strafe > 0) {
                    yaw += (forward > 0) ? -45 : 45;
                } else if (strafe < 0) {
                    yaw += (forward > 0) ? 45 : -45;
                }
                strafe = 0;
                forward = (forward > 0) ? 1 : -1;
            }

            double rad = Math.toRadians(yaw + 90);
            double x = forward * motion * Math.cos(rad) + strafe * motion * Math.sin(rad);
            double z = forward * motion * Math.sin(rad) - strafe * motion * Math.cos(rad);

            mc.player.setDeltaMovement(x, mc.player.getDeltaMovement().y, z);
        }
    }
    public static void fixMovement(final EventKeyBoard event, float yaw) {
        final float forward = event.getMovementForward();
        final float strafe = event.getMovementStrafe();

        final double angle = Mth.wrapDegrees(Math.toDegrees(MoveUtil.direction(mc.player.isFallFlying() ? yaw : mc.player.getYRot(), forward, strafe)));

        if (forward == 0 && strafe == 0) {
            return;
        }

        float closestForward = 0, closestStrafe = 0, closestDifference = Float.MAX_VALUE;

        for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
            for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
                if (predictedStrafe == 0 && predictedForward == 0) continue;

                final double predictedAngle = Mth.wrapDegrees(Math.toDegrees(MoveUtil.direction(yaw, predictedForward, predictedStrafe)));
                final double difference = Math.abs(angle - predictedAngle);

                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }

        event.setMovementForward(closestForward);
        event.setMovementStrafe(closestStrafe);
    }

    public static double direction(float rotationYaw, final double moveForward, final double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }
    public static double getSpeed() {
        return Math.hypot(mc.player.getDeltaMovement().x, mc.player.getDeltaMovement().z);
    }

    public static double[] forward(final double d) {
        float f = mc.player.input.forwardImpulse;
        float f2 = mc.player.input.leftImpulse;
        float f3 = mc.player.getYRot();
        if (f != 0.0f) {
            if (f2 > 0.0f) {
                f3 += ((f > 0.0f) ? -45 : 45);
            } else if (f2 < 0.0f) {
                f3 += ((f > 0.0f) ? 45 : -45);
            }
            f2 = 0.0f;
            if (f > 0.0f) {
                f = 1.0f;
            } else if (f < 0.0f) {
                f = -1.0f;
            }
        }
        final double d2 = Math.sin(Math.toRadians(f3 + 90.0f));
        final double d3 = Math.cos(Math.toRadians(f3 + 90.0f));
        final double d4 = f * d * d3 + f2 * d * d2;
        final double d5 = f * d * d2 - f2 * d * d3;
        return new double[]{d4, d5};
    }

    public static void setMotion(double speed) {
        double forward = mc.player.input.forwardImpulse;
        double strafe = mc.player.input.leftImpulse;
        float yaw = mc.player.getYRot();
        if (forward == 0 && strafe == 0) {
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
        } else {
            if (forward != 0) {
                if (strafe > 0) {
                    yaw += (float) (forward > 0 ? -45 : 45);
                } else if (strafe < 0) {
                    yaw += (float) (forward > 0 ? 45 : -45);
                }
                strafe = 0;
                if (forward > 0) {
                    forward = 1;
                } else if (forward < 0) {
                    forward = -1;
                }
            }
            double sin = Mth.sin((float) Math.toRadians(yaw + 90));
            double cos = Mth.cos((float) Math.toRadians(yaw + 90));
            mc.player.setDeltaMovement(forward * speed * cos + strafe * speed * sin, mc.player.getDeltaMovement().y, forward * speed * sin - strafe * speed * cos);
        }
    }
}
