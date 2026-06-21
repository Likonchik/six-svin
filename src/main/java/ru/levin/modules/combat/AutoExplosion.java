package ru.levin.modules.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.input.EventKey;
import ru.levin.events.impl.input.EventKeyBoard;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.world.EventObsidianPlace;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BindSetting;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.move.MoveUtil;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.*;

@FunctionAnnotation(name = "AutoExplosion", type = Type.Combat, desc = "Автоматически ставит кристал, при ставке обсидиана")
public class AutoExplosion extends Function {

    private final BooleanSetting correction = new BooleanSetting("Коррекция движения", true);
    private final SliderSetting delay = new SliderSetting("Задержка", 100f, 50f, 300f, 1f);

    private final BooleanSetting sanya = new BooleanSetting("Ставить по бинду", false);
    private final BindSetting bind = new BindSetting("Кнопка", 0, () -> sanya.get());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private BlockPos crystalPos = null;
    private Entity crystalEntity = null;
    private int previousSlot = -1;

    public Vector2f serverRot = null;
    private boolean rotating = false;

    public AutoExplosion() {
        addSettings(correction, delay, sanya, bind);
    }
    public boolean check() {
        return correction.get() &&
                crystalEntity != null &&
                crystalPos != null &&
                serverRot != null &&
                state;
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventKey key && sanya.get()) {
            if (key.key == bind.getKey()) {
                schedulePlaceSequence();
            }
        }
        if (event instanceof EventKeyBoard input && shouldCorrect()) {
            MoveUtil.fixMovement(input, serverRot.x);
        }

        if (event instanceof EventMotion motion) {
            handleRotation(motion);
        }

        if (event instanceof EventObsidianPlace place) {
            scheduleCrystalPlace(place.getPos());
        }

        if (event instanceof EventUpdate) {
            updateLogic();
        }
    }

    private void schedulePlaceSequence() {
        BlockPos pos = getLookingBlockPos();
        if (pos == null) return;

        int obsidianSlot = findObsidianSlot();
        if (obsidianSlot == -1) return;

        previousSlot = mc.player.getInventory().selected;
        mc.player.getInventory().selected = obsidianSlot;

        scheduler.schedule(() -> mc.execute(() -> {
            placeBlock(pos);
            mc.player.getInventory().selected = previousSlot;
            scheduleCrystalPlace(pos);

        }), 50, TimeUnit.MILLISECONDS);
    }

    private void placeBlock(BlockPos pos) {
        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bhr);
        if (result == InteractionResult.SUCCESS) mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void scheduleCrystalPlace(BlockPos pos) {
        int crystalSlot = findCrystalSlot();
        if (crystalSlot == -1 || !canPlaceCrystal(pos)) return;

        scheduler.schedule(() -> mc.execute(() -> {
            previousSlot = mc.player.getInventory().selected;
            mc.player.getInventory().selected = crystalSlot;

            BlockHitResult hit = new BlockHitResult(
                    Vec3.atCenterOf(pos),
                    Direction.UP,
                    pos,
                    false
            );

            InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            if (result == InteractionResult.SUCCESS) {
                mc.player.swing(InteractionHand.MAIN_HAND);
                crystalPos = pos;
            }

            mc.player.getInventory().selected = previousSlot;

        }), (long) delay.get().longValue(), TimeUnit.MILLISECONDS);
    }

    private boolean canPlaceCrystal(BlockPos pos) {
        BlockPos above = pos.above();
        return mc.level.isEmptyBlock(above);
    }
    private void updateLogic() {
        if (crystalPos == null) return;

        if (mc.player.position().distanceTo(Vec3.atCenterOf(crystalPos)) > 6.0) {
            reset();
            return;
        }

        List<Entity> crystals = mc.level.getEntities(
                        null,
                        new AABB(crystalPos).inflate(1.0))
                .stream()
                .filter(e -> e instanceof EndCrystal)
                .toList();

        if (!crystals.isEmpty()) {
            crystalEntity = crystals.get(0);
            tryAttack(crystalEntity);
        }
    }

    private void handleRotation(EventMotion event) {
        if (crystalEntity == null) return;

        Vector2f targetRot = rotationToEntity(crystalEntity);
        if (serverRot == null) serverRot = targetRot;

        serverRot.x += clampRotation(targetRot.x - serverRot.x, 10);
        serverRot.y += clampRotation(targetRot.y - serverRot.y, 10);

        event.setYaw(serverRot.x);
        event.setPitch(serverRot.y);
    }

    private float clampRotation(float value, float maxStep) {
        if (value > maxStep) return maxStep;
        if (value < -maxStep) return -maxStep;
        return value;
    }

    private void tryAttack(Entity entity) {
        if (entity == null || mc.player.getAttackStrengthScale(0) < 1) return;

        mc.gameMode.attack(mc.player, entity);
        mc.player.swing(InteractionHand.MAIN_HAND);
        reset();
    }

    private int findCrystalSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(Items.END_CRYSTAL)) return i;
        return -1;
    }

    private int findObsidianSlot() {
        for (int i = 0; i < 9; i++)
            if (mc.player.getInventory().getItem(i).is(Items.OBSIDIAN)) return i;
        return -1;
    }

    private BlockPos getLookingBlockPos() {
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getViewVector(1f).scale(4);

        BlockHitResult bhr = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eyes,
                eyes.add(look),
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                mc.player
        ));

        if (bhr == null || bhr.getBlockPos() == null) return null;
        return bhr.getBlockPos();
    }

    private boolean shouldCorrect() {
        return correction.get() && crystalEntity != null && crystalPos != null && serverRot != null && state;
    }

    private void reset() {
        crystalEntity = null;
        crystalPos = null;
        serverRot = null;
        previousSlot = -1;
    }

    public static Vector2f rotationToEntity(Entity entity) {
        Vec3 diff = entity.position().subtract(mc.player.position());
        double flatDist = Math.hypot(diff.x, diff.z);

        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, flatDist));

        return new Vector2f(yaw, pitch);
    }

    @Override
    protected void onDisable() {
        reset();
        super.onDisable();
    }
}
