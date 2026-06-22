package ru.levin.util.sbw;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// Предикт траектории снарядов SuperbWarfare — модель выверена по декомпилу 0.8.8.
// ВАЖНО: FastThrowableProjectile.tick() ОТМЕНЯЕТ ванильное трение (scale(1/0.99)) → горизонтальная скорость
// НЕ затухает (поэтому раньше предикт недолетал). Поэтому здесь трения НЕТ. За тик: vel.y -= gravity; pos += vel.
//   - GRENADE_BOUNCE (ручная M67): отскоки (bounce()), взрыв ТОЛЬКО по фитилю. gravity 0.05.
//   - GRENADE_IMPACT (РГО): НЕ отскакивает — взрыв об первый блок ИЛИ по фитилю. gravity 0.05.
//   - PROJECTILE_IMPACT (бомбы/ракеты): взрыв об первый блок. РПГ-ракета: gravity 0.015 + самоускорение ×1.03
//     с 3-го тика (accelerate=true) — поэтому летит по разгоняющейся дуге далеко.
public final class SbwBallistics {

    private SbwBallistics() {}

    public enum Mode { GRENADE_BOUNCE, GRENADE_IMPACT, PROJECTILE_IMPACT }

    public static final class Result {
        public final Vec3 landing;
        public final int ticks;
        public final boolean hitBlock;
        public final List<Vec3> path;
        Result(Vec3 landing, int ticks, boolean hitBlock, List<Vec3> path) {
            this.landing = landing; this.ticks = ticks; this.hitBlock = hitBlock; this.path = path;
        }
    }

    public static Result simulate(Entity e, float gravity, int maxSteps, int fuseTicks, Mode mode, boolean accelerate) {
        if (e == null) return null;
        return simulate(e.position(), e.getDeltaMovement(), gravity, maxSteps, fuseTicks, mode, accelerate, e);
    }

    public static Result simulate(Vec3 startPos, Vec3 startVel, float gravity, int maxSteps, int fuseTicks,
                                  Mode mode, boolean accelerate, Entity ignore) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || startPos == null || startVel == null) return null;
        boolean grenade = (mode == Mode.GRENADE_BOUNCE || mode == Mode.GRENADE_IMPACT);
        Vec3 pos = startPos;
        Vec3 vel = startVel;
        List<Vec3> path = new ArrayList<>();
        path.add(pos);

        // ВАЖНО (как в ваниль ThrowableProjectile.tick): сначала MOVE по текущей скорости, ПОТОМ гравитация.
        // Горизонталь НЕ затухает (SBW гасит ваниль-трение через scale(1/0.99)) — поэтому никакого дрэга/scale.
        for (int step = 1; step <= maxSteps; step++) {
            Vec3 next = pos.add(vel);

            BlockHitResult bhr = mc.level.clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ignore));
            if (bhr.getType() == HitResult.Type.BLOCK) {
                Vec3 hit = bhr.getLocation();
                if (mode == Mode.GRENADE_BOUNCE) {
                    Direction dir = bhr.getDirection();
                    vel = bounce(vel, dir, gravity);
                    Vec3 n = Vec3.atLowerCornerOf(dir.getNormal());
                    pos = hit.add(n.scale(0.02));
                    path.add(pos);
                } else {
                    path.add(hit);
                    return new Result(hit, step, true, path); // РГО/бомба/ракета — взрыв об блок
                }
            } else {
                pos = next;
                path.add(pos);
            }

            vel = vel.subtract(0, gravity, 0);                 // гравитация ПОСЛЕ move
            if (accelerate && step >= 3) vel = vel.scale(1.03); // самоускорение ракеты РПГ

            if (grenade && step >= fuseTicks) {
                return new Result(pos, step, false, path); // взрыв по фитилю
            }
        }
        return new Result(pos, maxSteps, mode == Mode.PROJECTILE_IMPACT, path);
    }

    // отскок дословно из HandGrenadeEntity.bounce(direction)
    private static Vec3 bounce(Vec3 vel, Direction dir, float gravity) {
        switch (dir.getAxis()) {
            case X: return vel.multiply(-0.5, 0.75, 0.75);
            case Y: {
                Vec3 v = vel.multiply(0.75, -0.25, 0.75);
                if (v.y < gravity) v = v.multiply(1.0, 0.0, 1.0);
                return v;
            }
            case Z: return vel.multiply(0.75, 0.75, -0.5);
            default: return vel;
        }
    }
}
