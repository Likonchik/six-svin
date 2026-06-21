package ru.levin.util.math;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import ru.levin.manager.IMinecraft;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

public class RayTraceUtil implements IMinecraft {

    private static final Map<UUID, Long> lastHitTimes = new HashMap<>();
    private static final long EFFECT_DURATION = 200;

    public static void markHit(Entity entity) {
        lastHitTimes.put(entity.getUUID(), System.currentTimeMillis());
    }
    public static float getHitProgress(Entity entity) {
        Long hitTime = lastHitTimes.get(entity.getUUID());
        if (hitTime == null) return 0f;

        long elapsed = System.currentTimeMillis() - hitTime;
        if (elapsed > EFFECT_DURATION) {
            lastHitTimes.remove(entity.getUUID());
            return 0f;
        }

        return 1f - ((float) elapsed / EFFECT_DURATION);
    }
    private static Vec3 getVector(float pitch, float yaw) {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = (float) Math.cos(-pitchRad);
        return new Vec3(
                -Math.sin(yawRad) * cosPitch,
                -Math.sin(pitchRad),
                Math.cos(yawRad) * cosPitch
        );
    }
    public static Entity getMouseOver(Entity target, float yaw, float pitch, double distance) {
        Entity entity = mc.getCameraEntity();
        if (entity == null || mc.level == null || target == null) {
            return null;
        }

        AABB playerBox = entity.getBoundingBox();
        AABB targetBox = target.getBoundingBox();
        Vec3 startVec = entity.getEyePosition();
        Vec3 directionVec = getVector(pitch, yaw);
        Vec3 endVec = startVec.add(directionVec.x * distance, directionVec.y * distance, directionVec.z * distance);
        if (playerBox.intersects(targetBox)) {
            EntityHitResult hitResult = rayCastEntity(distance, yaw, pitch, (e) -> e == target && !e.isSpectator() && e.canBeHitByProjectile());
            if (hitResult != null && hitResult.getEntity() == target) {
                return target;
            }
        }

        EntityHitResult entityHitResult = rayCastEntities(entity, startVec, endVec, targetBox, (e) -> e == target && !e.isSpectator() && e.canBeHitByProjectile(), distance);

        if (entityHitResult != null && startVec.distanceTo(entityHitResult.getLocation()) <= distance) {
            return entityHitResult.getEntity();
        }

        return null;
    }

    public static EntityHitResult rayCastEntity(double range, float yaw, float pitch, Predicate<Entity> filter) {
        Entity entity = mc.getCameraEntity();
        if (entity == null || mc.level == null) {
            return null;
        }
        Vec3 cameraVec = entity.getEyePosition(1.0F);
        float pitchRad = pitch * 0.017453292F;
        float yawRad = -yaw * 0.017453292F;
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        Vec3 rotationVec = new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
        Vec3 end = cameraVec.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);
        AABB box = entity.getBoundingBox().expandTowards(rotationVec.scale(range)).inflate(1.0, 1.0, 1.0);

        return ProjectileUtil.getEntityHitResult(entity, cameraVec, end, box, filter, range * range);
    }
    private static EntityHitResult rayCastEntities(Entity source, Vec3 start, Vec3 end, AABB boundingBox, java.util.function.Predicate<Entity> predicate, double maxDistance) {
        Level world = source.level();
        double closestDistance = maxDistance;
        Entity closestEntity = null;
        Vec3 closestHitPos = null;

        for (Entity entity : world.getEntitiesOfClass(Entity.class, boundingBox, predicate)) {
            if (entity == source) continue;

            AABB entityBox = entity.getBoundingBox();
            var hit = entityBox.clip(start, end);

            if (hit.isPresent()) {
                Vec3 hitPos = hit.get();
                double distance = start.distanceTo(hitPos);

                if (distance < closestDistance) {
                    closestEntity = entity;
                    closestHitPos = hitPos;
                    closestDistance = distance;
                }
            }
        }

        if (closestEntity != null) {
            return new EntityHitResult(closestEntity, closestHitPos);
        }
        return null;
    }
    public static BlockHitResult rayCast(double range, float yaw, float pitch, boolean includeFluids) {
        Entity entity = mc.getCameraEntity();
        if (entity == null || mc.level == null) {
            return null;
        }
        Vec3 start = entity.getEyePosition(1.0F);
        float pitchRad = pitch * 0.017453292F;
        float yawRad = -yaw * 0.017453292F;
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        Vec3 rotationVec = new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
        Vec3 end = start.add(rotationVec.x * range, rotationVec.y * range, rotationVec.z * range);
        Level world = mc.level;
        ClipContext.Fluid fluidHandling = includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE;
        ClipContext context = new ClipContext(start, end, ClipContext.Block.OUTLINE, fluidHandling, entity);

        return world.clip(context);
    }
}