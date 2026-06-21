package ru.levin.modules.render;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.player.EventAttack;
import ru.levin.modules.render.littlePet.GhostWolfEntity;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.events.Event;

@FunctionAnnotation(name = "LittlePet",desc = "Пёс за вами (скоро подключу AI)", type = Type.Render)
public class LittleSnickers extends Function {
    private LivingEntity fakeEntity = null;
    private boolean active = false;
    private Level lastWorld = null;

    private final BooleanSetting bumbum = new BooleanSetting("Бегать за таргетом", false);

    private int ticksAway = 0;
    private int stuckTicks = 0;

    private LivingEntity currentTarget = null;

    public LittleSnickers() {
        addSettings(bumbum);
    }

    private LivingEntity createEntity() {
        var entity = new GhostWolfEntity(EntityType.WOLF, mc.level);
        if (entity == null) return null;

        entity.moveTo(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot());
        entity.setInvisible(false);
        entity.setCustomNameVisible(false);
        entity.setHealth(20.0F);

        if (entity instanceof TamableAnimal tameable) {
            tameable.setTame(true, false);
            tameable.setOwnerUUID(mc.player.getUUID());
        }

        return entity;
    }

    private boolean isSolid(BlockPos pos) {
        BlockState blockState = mc.level.getBlockState(pos);
        return !blockState.isAir() && !blockState.getCollisionShape(mc.level, pos).isEmpty();
    }

    private void moveEntityTowardsTarget(Vec3 targetPos) {
        Vec3 entityPos = fakeEntity.position();
        double dx = targetPos.x - entityPos.x;
        double dz = targetPos.z - entityPos.z;
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        double dy = targetPos.y - entityPos.y;

        boolean isMoving = false;
        boolean isJumping = false;

        BlockPos entityBlockPos = new BlockPos(
                (int) Math.floor(entityPos.x),
                (int) Math.floor(entityPos.y - 0.1),
                (int) Math.floor(entityPos.z)
        );
        boolean onGround = isSolid(entityBlockPos);

        if (Math.abs(dy) >= 12 || distanceXZ > 20) {
            fakeEntity.moveTo(targetPos.x, targetPos.y, targetPos.z, fakeEntity.getYRot(), fakeEntity.getXRot());
            fakeEntity.setDeltaMovement(Vec3.ZERO);
            stuckTicks = 0;
            return;
        }

        if (distanceXZ > 1.2) {
            double speed = 0.21;
            double normX = dx / distanceXZ;
            double normZ = dz / distanceXZ;

            BlockPos frontPos = new BlockPos(
                    (int) Math.floor(entityPos.x + normX * 0.6),
                    (int) Math.floor(entityPos.y),
                    (int) Math.floor(entityPos.z + normZ * 0.6)
            );
            BlockPos aboveFrontPos = frontPos.above();
            BlockPos blockBelowFront = frontPos.below();
            BlockPos blockBelow = entityBlockPos.below();

            boolean frontSolid = isSolid(frontPos);
            boolean aboveClear = !isSolid(aboveFrontPos);
            boolean belowSolid = isSolid(blockBelow);
            boolean belowFrontSolid = isSolid(blockBelowFront);

            if (frontSolid && aboveClear && onGround) {
                int blockHeight = frontPos.getY() - entityBlockPos.getY();
                if (blockHeight <= 1) {
                    Vec3 vel = fakeEntity.getDeltaMovement();
                    fakeEntity.setDeltaMovement(vel.x, 0.5, vel.z);
                    isJumping = true;
                    onGround = false;
                } else {
                    stuckTicks++;
                    return;
                }
            }

            if (!frontSolid && !belowFrontSolid) {
                boolean foundGround = false;
                for (int drop = 1; drop <= 3; drop++) {
                    BlockPos check = frontPos.below(drop);
                    if (isSolid(check)) {
                        Vec3 vel = fakeEntity.getDeltaMovement();
                        fakeEntity.setDeltaMovement(vel.x, -0.1, vel.z);
                        foundGround = true;
                        break;
                    }
                }
                if (!foundGround) {
                    stuckTicks++;
                    return;
                }
            }

            if (!isJumping && (belowSolid || onGround)) {
                fakeEntity.move(MoverType.SELF, new Vec3(normX * speed, 0, normZ * speed));
                isMoving = true;
                stuckTicks = 0;
            } else if (!isJumping) {
                stuckTicks++;
            }

            if (isMoving) {
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                fakeEntity.setYRot(yaw);
                fakeEntity.setYBodyRot(yaw);
                if (fakeEntity instanceof Mob mob) mob.setYHeadRot(yaw);
            }
        }

        if (!onGround) {
            Vec3 velocity = fakeEntity.getDeltaMovement();
            fakeEntity.setDeltaMovement(
                    velocity.x * 0.91,
                    Math.max(velocity.y - 0.08, -0.5),
                    velocity.z * 0.91
            );
            fakeEntity.move(MoverType.SELF, fakeEntity.getDeltaMovement());
        }

        if (!isMoving && onGround) {
            fakeEntity.setDeltaMovement(Vec3.ZERO);
        }

        if (fakeEntity instanceof Wolf wolf) {
            wolf.setSprinting(isMoving && !isJumping);
            wolf.setInSittingPose(!isMoving && onGround);
        }
    }

    private void updateEntityRotation(Entity target) {
        if (target == null) return;

        Vec3 entityEyes = fakeEntity.position().add(0, fakeEntity.getEyeHeight(), 0);
        Vec3 targetEyes = target.position().add(0, target.getEyeHeight(), 0);
        Vec3 diff = targetEyes.subtract(entityEyes);

        double flatDist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-diff.x, diff.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(diff.y, flatDist));

        fakeEntity.setYRot(yaw);
        fakeEntity.setXRot(pitch);
        fakeEntity.setYBodyRot(yaw);

        if (fakeEntity instanceof Mob mob) {
            mob.setYHeadRot(yaw);
        }
    }

    private void onTick() {
        if (!active || mc.player == null || mc.level == null) return;

        if (fakeEntity == null) {
            fakeEntity = createEntity();
            if (fakeEntity != null) {
                mc.level.addEntity(fakeEntity);
                lastWorld = mc.level;
            }
            return;
        }

        if (mc.level != lastWorld) {
            if (fakeEntity != null)
                mc.level.removeEntity(fakeEntity.getId(), Entity.RemovalReason.DISCARDED);
            fakeEntity = createEntity();
            if (fakeEntity == null) return;
            mc.level.addEntity(fakeEntity);
            lastWorld = mc.level;
        }

        Vec3 playerPos = mc.player.position();
        Vec3 entityPos = fakeEntity.position();
        double distToPlayer = playerPos.distanceTo(entityPos);

        if (distToPlayer > 10.0 || stuckTicks > 40) {
            ticksAway++;

            if (ticksAway >= 60 || stuckTicks > 40) {
                double targetX = playerPos.x;
                double targetY = playerPos.y;
                double targetZ = playerPos.z;

                if (mc.player.isFallFlying() || !mc.player.onGround()) {
                    BlockPos.MutableBlockPos posBelow = new BlockPos.MutableBlockPos(
                            (int) Math.floor(playerPos.x),
                            (int) Math.floor(playerPos.y),
                            (int) Math.floor(playerPos.z)
                    );

                    for (int yOffset = 0; yOffset < 64; yOffset++) {
                        if (isSolid(posBelow)) {
                            targetY = posBelow.getY() + 1.0;
                            break;
                        }
                        posBelow.move(0, -1, 0);
                    }
                }

                fakeEntity.moveTo(
                        targetX, targetY, targetZ,
                        fakeEntity.getYRot(), fakeEntity.getXRot()
                );
                fakeEntity.setDeltaMovement(Vec3.ZERO);
                ticksAway = 0;
                stuckTicks = 0;
            }
        } else {
            ticksAway = 0;
        }

        if (currentTarget != null && (currentTarget.isRemoved() || currentTarget.isDeadOrDying())) {
            currentTarget = null;
        }

        Vec3 moveTo = playerPos;
        Entity lookTarget = mc.player;

        if (bumbum.get() && currentTarget != null) {
            double distToTarget = currentTarget.position().distanceTo(entityPos);

            if (distToTarget <= 12) {
                moveTo = currentTarget.position();
                lookTarget = currentTarget;
            } else {
                currentTarget = null;
                moveTo = playerPos;
                lookTarget = mc.player;
            }
        }

        moveEntityTowardsTarget(moveTo);
        fakeEntity.aiStep();
        updateEntityRotation(lookTarget);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            onTick();
            return;
        }
        if (event instanceof EventAttack attack && bumbum.get()) {
            Entity attacked = attack.getTarget();
            if (attacked instanceof LivingEntity living && living != mc.player) {
                currentTarget = living;
            }
        }
    }

    @Override
    public void onEnable() {
        active = true;
        lastWorld = mc.level;

        if (mc.player != null && mc.level != null) {
            fakeEntity = createEntity();
            if (fakeEntity != null) {
                mc.level.addEntity(fakeEntity);
            }
        }
    }

    @Override
    public void onDisable() {
        if (!active) return;

        if (mc.level != null && fakeEntity != null) {
            mc.level.removeEntity(fakeEntity.getId(), Entity.RemovalReason.DISCARDED);
            fakeEntity = null;
        }

        active = false;
        ticksAway = 0;
        stuckTicks = 0;
        currentTarget = null;
    }
}
