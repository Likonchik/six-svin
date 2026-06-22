package ru.levin.modules.misc;


import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.render.RenderUtil;

import java.awt.*;


@FunctionAnnotation(name = "Xray", desc = "Иксрей", type = Type.Misc)
public class Xray extends Function {
    public static SliderSetting radius = new SliderSetting("Радиус", 20f, 1f, 30f, 1f).withDesc("Радиус подсветки руд");
    public static BooleanSetting ancient = new BooleanSetting("Незерит", true, "Подсветка древних обломков");

    public static BooleanSetting diamond = new BooleanSetting("Алмазы", true, "Подсветка алмазной руды");

    public static BooleanSetting emerald = new BooleanSetting("Изумруды", true, "Подсветка изумрудной руды");

    public static BooleanSetting gold = new BooleanSetting("Золото", true, "Подсветка золотой руды");

    public static BooleanSetting iron = new BooleanSetting("Железо", true, "Подсветка железной руды");

    public static BooleanSetting coal = new BooleanSetting("Уголь", true, "Подсветка угольной руды");

    public static BooleanSetting redstone = new BooleanSetting("Редстоун", true, "Подсветка редстоуновой руды");

    public static BooleanSetting lapise = new BooleanSetting("Лазурит", true, "Подсветка лазуритовой руды");


    public Xray() {
        addSettings(radius,
                ancient,
                diamond,
                emerald,
                gold,
                iron,
                coal,
                redstone,
                lapise);
    }
    @Override
    public void onEvent(Event event) {
        if (event instanceof EventRender3D e) {
            for (int x = (int) (mc.player.getX() - 30); x <= mc.player.getX() + radius.get().floatValue(); x++) {
                for (int y = (int) (mc.player.getY() - 30); y <= mc.player.getY() + radius.get().floatValue(); y++) {
                    for (int z = (int) (mc.player.getZ() - 30); z <= mc.player.getZ() + radius.get().floatValue(); z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = mc.level.getBlockState(pos);
                        AABB box = new AABB(pos).deflate(0.01);
                        PoseStack poseStack = new PoseStack();
                        if (ancient.get()) {
                            if (state.getBlock() == Blocks.ANCIENT_DEBRIS) {
                                RenderUtil.render3D.drawHoleOutline(box, Color.green.getRGB(),2);
                            }
                        }
                        if (diamond.get()) {
                            if (state.getBlock() == Blocks.DIAMOND_ORE) {
                                RenderUtil.render3D.drawHoleOutline(box, new Color(0, 255, 255,80).getRGB(),2);
                            }
                        }
                        if (emerald.get()) {
                            if (state.getBlock() == Blocks.EMERALD_ORE) {
                                RenderUtil.render3D.drawHoleOutline(box, new Color(0, 128, 0,80).getRGB(),2);
                            }
                        }
                        if (gold.get()) {
                            if (state.getBlock() == Blocks.GOLD_ORE) {
                                RenderUtil.render3D.drawHoleOutline(box, new Color(255, 255, 0,80).getRGB(),2);
                            }
                        }
                        if (iron.get()) {
                            if (state.getBlock() == Blocks.IRON_ORE) {
                                RenderUtil.render3D.drawHoleOutline(box, new Color(	192, 192, 192,80).getRGB(),2);
                            }
                        }
                        if (coal.get()) {
                            if (state.getBlock() == Blocks.COAL_ORE) {
                                RenderUtil.render3D.drawHoleOutline(box, new Color(	0, 0, 0,80).getRGB(),2);
                            }
                        }
                        if (redstone.get()) {
                            if (state.getBlock() == Blocks.REDSTONE_ORE) {
                                RenderUtil.render3D.drawHoleOutline(box, new Color(	255, 0, 0,80).getRGB(),2);
                            }
                        }
                        if (lapise.get()) {
                            if (state.getBlock() == Blocks.LAPIS_ORE) {
                                RenderUtil.render3D.drawHoleOutline(box, new Color(	0, 0, 255,80).getRGB(),2);
                            }
                        }
                    }
                }
            }
        }
    }
}
