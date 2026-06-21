package ru.levin.util.player;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;

import static java.lang.Math.clamp;

public class AuraUtil implements IMinecraft {

    public static Vec3 getVelocityTowards(Entity target, double speed, boolean defaultSpeed, boolean predict) {
        if (target == null || mc.player == null) return Vec3.ZERO;

        double finalSpeed = defaultSpeed
                ? mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED)
                : speed;

        Vec3 look = target.getLookAngle();
        double predictDistance = predict ? Manager.FUNCTION_MANAGER.targetStrafe.predict.get().floatValue() : 0;
        double dx = (target.getX() + look.x * predictDistance) - mc.player.getX();
        double dz = (target.getZ() + look.z * predictDistance) - mc.player.getZ();

        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance == 0) return Vec3.ZERO;

        double vx = dx / distance * finalSpeed;
        double vz = dz / distance * finalSpeed;

        return new Vec3(vx, 0, vz);
    }


    public static float getArmor(LivingEntity entity) {
        if (entity instanceof Player player) {
            return player.getArmorValue();
        }
        return 0;
    }
    public static double getDistance(LivingEntity entity) {
        return AuraUtil.getVector(entity).length();
    }
    public static Vector3d getVector(LivingEntity target) {
        Vec3 basePos = target.position();

        double wHalf = target.getBbWidth() / 2.0;
        double yMin = basePos.y;
        double yMax = basePos.y + target.getBbHeight();
        double playerEyeY = mc.player.getEyeY();

        int steps = 10;
        Vector3d bestVector = null;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i <= steps; i++) {
            double y = yMin + (yMax - yMin) * ((double) i / steps);
            double xOffset = clamp(mc.player.getX() - basePos.x, -wHalf, wHalf);
            double zOffset = clamp(mc.player.getZ() - basePos.z, -wHalf, wHalf);

            Vector3d currentVector = new Vector3d(basePos.x - mc.player.getX() + xOffset, y - playerEyeY, basePos.z - mc.player.getZ() + zOffset);

            double distance = currentVector.length();
            if (distance < bestDistance) {
                bestDistance = distance;
                bestVector = currentVector;
            }
        }

        if (bestVector == null) {
            bestVector = new Vector3d(basePos.x - mc.player.getX(), (yMin + yMax) / 2 - playerEyeY, basePos.z - mc.player.getZ());
        }

        return bestVector;
    }
}
