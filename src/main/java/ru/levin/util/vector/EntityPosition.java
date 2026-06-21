package ru.levin.util.vector;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3d;

import static ru.levin.util.math.MathUtil.interpolate;

public class EntityPosition extends Vector3d {
    protected EntityPosition(Entity entity, float height, float pt) {
        super(interpolate(entity.xOld, entity.getX(), pt), interpolate(entity.yOld, entity.getY(), pt) + height, interpolate(entity.zOld, entity.getZ(), pt));
    }
    public static Vector3d get(Entity entity, float height, float pt) {
        return new EntityPosition(entity, height, pt);
    }

    public static Vector3d get(Entity entity, float pt) {
        return get(entity, 0, pt);
    }
}
