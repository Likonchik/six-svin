package ru.levin.modules.combat;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@FunctionAnnotation(name = "SelfTrap", keywords = "Trap", type = Type.Combat, desc = "Застраивает ноги любыми блоками")
public class SelfTrap extends Function {

    public final Vector2f rotate = new Vector2f(0f, 0f);

    private final BlockPos[] baseOffsets = new BlockPos[]{
            BlockPos.ZERO.north(),
            BlockPos.ZERO.south(),
            BlockPos.ZERO.east(),
            BlockPos.ZERO.west()
    };

    private List<BlockPos> targets = new ArrayList<>();
    private int index = 0;

    private int originalSlot = -1;
    public boolean active = false;

    private int tickDelay = 0;
    private final Random rnd = new Random();

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) {
            toggle();
            return;
        }

        targets.clear();
        BlockPos base = mc.player.blockPosition();
        for (BlockPos off : baseOffsets) {
            targets.add(base.offset(off));
        }

        originalSlot = mc.player.getInventory().selected;
        index = 0;
        active = true;
        tickDelay = 0;
    }

    @Override
    public void onDisable() {
        if (mc.player != null && originalSlot != -1)
            mc.player.getInventory().selected = originalSlot;
        targets.clear();
        index = 0;
        active = false;
        tickDelay = 0;
    }

    @Override
    public void onEvent(Event event) {
        if (!active) return;
        if (!(event instanceof EventUpdate || event instanceof EventMotion)) return;

        if (event instanceof EventUpdate) {
            if (tickDelay > 0) {
                tickDelay--;
                return;
            }

            if (index >= targets.size()) {
                finish();
                return;
            }

            BlockPos target = targets.get(index);
            if (!mc.level.getBlockState(target).isAir() || isEntityBlocking(target)) {
                index++;
                return;
            }

            Direction placeSide = findPlaceableSide(target);
            if (placeSide != null) {
                BlockPos neighbor = target.relative(placeSide);
                Vec3 hitVec = Vec3.atCenterOf(neighbor).add(Vec3.atLowerCornerOf(placeSide.getNormal()).scale(0.5));
                int blockSlot = findAnyBlockInHotbar();
                if (blockSlot == -1) {
                    finish();
                    return;
                }

                mc.player.getInventory().selected = blockSlot;

                BlockHitResult hit = new BlockHitResult(hitVec, placeSide.getOpposite(), neighbor, false);
                rotateTo(hit.getBlockPos());
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                mc.player.swing(InteractionHand.MAIN_HAND);

                tickDelay = 1 + rnd.nextInt(3);
                index++;
                return;
            }

            index++;
        }

        if (event instanceof EventMotion motion) {
            motion.setYaw(rotate.x);
            motion.setPitch(rotate.y);
        }
    }

    private void finish() {
        active = false;
        if (mc.player != null && originalSlot != -1)
            mc.player.getInventory().selected = originalSlot;
    }

    private int findAnyBlockInHotbar() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (block != Blocks.AIR) return i;
            }
        }
        return -1;
    }

    private boolean isEntityBlocking(BlockPos pos) {
        AABB box = new AABB(pos);
        for (Entity e : mc.level.getEntities(null, box)) {
            if (e != mc.player) return true;
        }
        return false;
    }

    private Direction findPlaceableSide(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (!mc.level.getBlockState(neighbor).isAir()
                    && mc.level.getBlockState(neighbor).isSolidRender(mc.level, neighbor)) {
                return dir;
            }
        }
        return null;
    }

    private void rotateTo(BlockPos pos) {
        if (mc.player == null) return;
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 0.5 - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));
        double dz = pos.getZ() + 0.5 - mc.player.getZ();

        double distXZ = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        rotate.x = yaw;
        rotate.y = pitch;
    }
}
