package ru.levin.modules.combat;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.input.EventKeyBoard;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.*;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.move.MoveUtil;
import ru.levin.util.player.InventoryUtil;
import ru.levin.util.player.TimerUtil;
import ru.levin.util.render.RenderUtil;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("All")
@FunctionAnnotation(
        name = "CrystalAura",
        type = Type.Combat,
        desc = "Умное автоустановление и взрыв кристаллов (test)"
)
public class CrystalAura extends Function {

    private final MultiSetting options = new MultiSetting(
            "Настройки",
            List.of("Не взрывать себя", "Коррекция движения", "Игрок в падении", "Подсветка блока"),
            new String[]{"Не взрывать себя", "Коррекция движения", "Игрок в падении", "Подсветка блока"}
    ).withDesc("Доп. настройки аура");

    private final ModeSetting distanceMode = new ModeSetting("Тип радиуса", "Свой", "Свой", "Grim").withDesc("Способ задать радиус");
    private final SliderSetting customDistance = new SliderSetting("Радиус", 5, 2.5f, 6, 0.05f, () -> distanceMode.is("Свой")).withDesc("Радиус действия");
    private final SliderSetting breakDelay = new SliderSetting("Задержка", 100, 0, 500, 1).withDesc("Задержка между действиями");
    public final BooleanSetting offHandCrystal = new BooleanSetting("Брать крист. в левую руку", true, "Кристалл в левую руку");
    private final BooleanSetting renderBlock = new BooleanSetting("Подсветка блока", true, "Подсветка блока установки");
    private final BooleanSetting rgCheck = new BooleanSetting("Проверка региона", true, "для гриферских серверов");
    private final BooleanSetting twoPlace = new BooleanSetting("Ставить в несколько мест", true, "Несколько кристаллов сразу");

    private BlockPos closestObsidian = null;
    public EndCrystal closestCrystal = null;
    private Vec3 obsidianVec = null;

    private final TimerUtil attackTimer = new TimerUtil();
    private final TimerUtil placeTimer = new TimerUtil();
    public final Vector2f rotate = new Vector2f(0f, 0f);
    private int originalSlot = -1;

    private boolean regionBlocked = false;
    private Vec3 regionBlockPos = null;
    private static final double REGION_BLOCK_RADIUS = 12.0;
    private static final double MIN_DISTANCE_BETWEEN_CRYSTALS = 2.5;

    private final List<BlockPos> multiPlaceTargets = new ArrayList<>();
    private Item originalOffhandItem = null;

    public CrystalAura() {
        addSettings(distanceMode, options, customDistance, breakDelay, offHandCrystal, renderBlock, rgCheck, twoPlace);
    }

    private double getEffectiveDistance() {
        return distanceMode.is("Grim") ? 3.6 : customDistance.get().intValue();
    }

    public boolean check() {
        return (closestObsidian != null || closestCrystal != null) && rotate != null && options.get("Коррекция движения");
    }

    @Override
    public void onDisable() {
        reset();
        super.onDisable();
    }

    @Override
    public void onEvent(Event event) {
        if (rgCheck.get() && event instanceof EventPacket eventPacket) {
            if (eventPacket.getPacket() instanceof ClientboundSystemChatPacket packet) {
                String message = packet.content().getString();
                if (message.contains("can't place that block here")) {
                    regionBlocked = true;
                    regionBlockPos = mc.player.position();
                }
            }
        }

        if (event instanceof EventRender3D) {
            if (renderBlock.get() && obsidianVec != null) {
                BlockPos pos = BlockPos.containing(obsidianVec);
                RenderUtil.render3D.drawHoleOutline(new AABB(pos), ColorUtil.getColorStyle(360), 2);

                if (!multiPlaceTargets.isEmpty()) {
                    for (BlockPos p : multiPlaceTargets) {
                        RenderUtil.render3D.drawHoleOutline(new AABB(p), ColorUtil.getColorStyle(220), 1);
                    }
                }
            }
        }

        if (event instanceof EventKeyBoard input && check()) {
            MoveUtil.fixMovement(input, rotate.x);
        }

        if (event instanceof EventMotion motion) {
            if (offHandCrystal.get()) {
                float currentHp = mc.player.getHealth();
                float minHp = Manager.FUNCTION_MANAGER.autoTotem.hp.get().floatValue();
                if (currentHp > minHp) {
                    InventoryUtil.moveToOffhand(Items.END_CRYSTAL);
                }
            }
            handleCrystalLogic(motion);
        }
    }

    private void handleCrystalLogic(EventMotion motion) {
        if (regionBlocked && regionBlockPos != null) {
            if (mc.player.position().distanceTo(regionBlockPos) < REGION_BLOCK_RADIUS) return;
            regionBlocked = false;
            regionBlockPos = null;
        }
        double maxDist = getEffectiveDistance();
        closestCrystal = findNearestCrystal(maxDist);
        if (closestCrystal != null) {
            breakCrystal(closestCrystal, motion);
            return;
        }
        if (originalSlot == -1) originalSlot = mc.player.getInventory().selected;
        int crystalSlot = InventoryUtil.getItem(Items.END_CRYSTAL.getClass(), true);
        if (crystalSlot == -1 && !offHandCrystal.get()) {
            restoreOriginalSlot();
            return;
        }
        closestObsidian = null;
        double bestDamage = 0.0;
        for (Entity e : Manager.SYNC_MANAGER.getEntities()) {
            if (!(e instanceof LivingEntity target) || e == mc.player || !e.isAlive()) continue;
            BlockPos pos = findBestCrystalPosition(target, maxDist);
            if (pos == null) continue;
            double damage = calculateCrystalDamage(pos, e);
            if (options.get("Не взрывать себя") && calculateCrystalDamage(pos, mc.player) > 6.0) continue;
            if (damage > bestDamage) {
                bestDamage = damage;
                closestObsidian = pos;
            }
        }
        multiPlaceTargets.clear();
        if (closestObsidian != null) {
            obsidianVec = Vec3.atCenterOf(closestObsidian);
            aimAt(obsidianVec, motion);
            multiPlaceTargets.add(closestObsidian);
            if (twoPlace.get()) {
                List<BlockPos> candidates = new ArrayList<>();
                BlockPos[] nearby = new BlockPos[]{closestObsidian.north(), closestObsidian.south(), closestObsidian.east(), closestObsidian.west(), closestObsidian.north().east(), closestObsidian.north().west(), closestObsidian.south().east(), closestObsidian.south().west()};
                for (BlockPos pos : nearby) {
                    if (!canPlaceCrystal(pos, maxDist)) continue;
                    boolean tooClose = false;
                    for (BlockPos existing : multiPlaceTargets) {
                        if (existing.distSqr(pos) < MIN_DISTANCE_BETWEEN_CRYSTALS * MIN_DISTANCE_BETWEEN_CRYSTALS) {
                            tooClose = true;
                            break;
                        }
                    }
                    if (!tooClose) candidates.add(pos);
                }
                Collections.shuffle(candidates);
                for (BlockPos pos : candidates) {
                    if (multiPlaceTargets.size() >= 3) break;
                    multiPlaceTargets.add(pos);
                }
            }
        }
        if (!multiPlaceTargets.isEmpty() && placeTimer.hasTimeElapsed((long) breakDelay.get().doubleValue())) {
            if (!offHandCrystal.get()) mc.player.getInventory().selected = crystalSlot;
            for (BlockPos pos : multiPlaceTargets) {
                tryPlaceCrystal(pos, motion);
            }
            multiPlaceTargets.clear();
            placeTimer.reset();
        }
    }
    private void breakCrystal(Entity crystal, EventMotion motion) {
        if (crystal == null) return;
        aimAt(crystal.position().add(0, 0.5, 0), motion);

        if (attackTimer.hasTimeElapsed((long) breakDelay.get().doubleValue())) {
            mc.player.swing(InteractionHand.MAIN_HAND);
            mc.getConnection().send(ServerboundInteractPacket.createAttackPacket(crystal, mc.player.isShiftKeyDown()));
            attackTimer.reset();
        }
    }

    private BlockPos findBestCrystalPosition(LivingEntity target, double maxDist) {
        BlockPos base = target.blockPosition();
        BlockPos[] positions = new BlockPos[]{
                base.north(), base.south(), base.east(), base.west(),
                base.north().east(), base.north().west(), base.south().east(), base.south().west(),
                base.above(), base.above().north(), base.above().south(), base.above().east(), base.above().west(),
                base.above().north().east(), base.above().north().west(), base.above().south().east(), base.above().south().west()
        };

        BlockPos bestPos = null;
        double bestDamage = 0.0;

        for (BlockPos airPos : positions) {
            BlockPos baseBlock = airPos.below();
            if (baseBlock.equals(mc.player.blockPosition().below())) continue;
            if (!canPlaceCrystal(baseBlock, maxDist)) continue;

            double damage = calculateCrystalDamage(baseBlock, target);
            double selfDamage = calculateCrystalDamage(baseBlock, mc.player);
            if (options.get("Не взрывать себя") && selfDamage > 6.0) continue;

            if (damage > bestDamage) {
                bestDamage = damage;
                bestPos = baseBlock;
            }
        }
        return bestPos;
    }

    private boolean canPlaceCrystal(BlockPos baseBlock, double maxDist) {
        if (mc.level == null || mc.player == null) return false;
        if (!(mc.level.getBlockState(baseBlock).getBlock() == Blocks.OBSIDIAN || mc.level.getBlockState(baseBlock).getBlock() == Blocks.BEDROCK))
            return false;

        BlockPos air1 = baseBlock.above();
        BlockPos air2 = baseBlock.above(2);
        if (!mc.level.getBlockState(air1).isAir() || !mc.level.getBlockState(air2).isAir()) return false;
        if (mc.player.position().distanceTo(Vec3.atCenterOf(baseBlock)) > maxDist) return false;
        AABB placeBox = new AABB(air1.getX(), air1.getY(), air1.getZ(), air1.getX() + 1, air1.getY() + 1, air1.getZ() + 1);
        for (Entity e : mc.level.getEntities(null, placeBox)) {
            if (e instanceof EndCrystal) return false;
        }

        return true;
    }

    private void tryPlaceCrystal(BlockPos pos, EventMotion motion) {
        if (pos == null || mc.player == null || mc.gameMode == null) return;

        BlockHitResult hitResult = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(0.5, 1, 0.5), Direction.UP, pos, false);
        mc.gameMode.useItemOn(mc.player, offHandCrystal.get() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(offHandCrystal.get() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);

        EndCrystal newCrystal = null;
        for (Entity e : Manager.SYNC_MANAGER.getEntities()) {
            if (e instanceof EndCrystal && e.distanceToSqr(Vec3.atCenterOf(pos.above())) < 3.2) {
                newCrystal = (EndCrystal) e;
                break;
            }
        }
        if (newCrystal != null) breakCrystal(newCrystal, motion);
    }

    private EndCrystal findNearestCrystal(double maxDist) {
        EndCrystal closest = null;
        double minDist = Double.MAX_VALUE;
        Vec3 eyePos = mc.player.getEyePosition();

        for (Entity entity : Manager.SYNC_MANAGER.getEntities()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (!crystal.isAlive()) continue;

            double dist = mc.player.distanceToSqr(crystal);
            if (dist <= maxDist * maxDist && dist < minDist) {
                closest = crystal;
                minDist = dist;
            }
        }
        return closest;
    }
    private double calculateCrystalDamage(BlockPos pos, Entity target) {
        if (target == null || mc.level == null) return 0.0;
        double distance = target.distanceToSqr(Vec3.atCenterOf(pos));
        if (distance > 12.0) return 0.0;
        double exposure = 1.0 - (distance / 12.0);
        double damage = exposure * 12.0;
        return Math.max(0, damage);
    }

    private void aimAt(Vec3 vec, EventMotion motion) {
        float[] rot = rotations(vec);
        if (motion != null) {
            motion.setYaw(rot[0]);
            motion.setPitch(rot[1]);
        }
        rotate.set(rot[0], rot[1]);
    }

    private void restoreOriginalSlot() {
        if (originalSlot >= 0 && originalSlot <= 8) {
            mc.player.getInventory().selected = originalSlot;
            originalSlot = -1;
        }
    }

    public void reset() {
        restoreOriginalSlot();
        closestObsidian = null;
        closestCrystal = null;
        obsidianVec = null;
        multiPlaceTargets.clear();
        attackTimer.reset();
        placeTimer.reset();
        regionBlocked = false;
        regionBlockPos = null;
    }

    public static float[] rotations(Vec3 vec) {
        double dx = vec.x - mc.player.getX();
        double dy = vec.y - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = vec.z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{yaw, pitch};
    }
}
