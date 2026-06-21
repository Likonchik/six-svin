package ru.levin.modules.render;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

import net.minecraft.client.renderer.MultiBufferSource;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventHeldItemRenderer;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "SwingAnimations", desc = "Красивые анимации для предмета в руке", type = Type.Render)
public class SwingAnimations extends Function {
    private final ModeSetting mode = new ModeSetting("Тип", "Smooth", "Smooth", "Block", "ToBack","SelfBack","360","Down","Glide","DropDown","DeadCode");

    public final BooleanSetting slowAnimation = new BooleanSetting("Плавность", false);
    public final SliderSetting slowAnimationSpeed = new SliderSetting("Сила плавности", 12f, 1, 50, 1, () -> slowAnimation.get());

    private final SliderSetting corner = new SliderSetting("Угол", 12.0f, 1.0f, 360.0f, 1.0f);
    private final SliderSetting slant = new SliderSetting("Наклон", 12.0f, 1.0f, 360.0f, 1.0f);

    public SwingAnimations() {
        addSettings(mode,slowAnimation,slowAnimationSpeed,corner,slant);
    }

    private void renderSwordAnimation(PoseStack matrices, float swingProgress, float equipProgress, HumanoidArm arm) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        switch (mode.get()) {
            case "Smooth" -> {
                matrices.translate(0.56F * i, -0.52F, -0.72F);
                applySwingOffset(matrices, arm, swingProgress);
            }
            case "Block" -> {
                if (swingProgress > 0) {
                    float g = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    matrices.translate(0.56F * i, equipProgress * -0.2f - 0.5F, -0.7F);
                    matrices.mulPose(Axis.YP.rotationDegrees(45 * i));
                    matrices.mulPose(Axis.XP.rotationDegrees(g * -85.0F));
                    matrices.translate(-0.1F * i, 0.28F, 0.2F);
                    matrices.mulPose(Axis.XP.rotationDegrees(-85.0F));
                } else {
                    float n = -0.4f * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    float m = 0.2f * Mth.sin(Mth.sqrt(swingProgress) * ((float) Math.PI * 2));
                    float f1 = -0.2f * Mth.sin(swingProgress * (float) Math.PI);
                    matrices.translate(n * i, m, f1);
                    applyEquipOffset(matrices, arm, equipProgress);
                    applySwingOffset(matrices, arm, swingProgress);
                }
            }
            case "ToBack" -> {
                float g = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                applyEquipOffset(matrices, arm, 0);
                matrices.mulPose(Axis.XP.rotationDegrees(50f));
                matrices.mulPose(Axis.YP.rotationDegrees((-30f * (1f - g) - 30f) * i));
                matrices.mulPose(Axis.ZP.rotationDegrees(110f * i));
            }
            case "SelfBack" -> {
                float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
                applyEquipOffset(matrices, arm, 0);
                matrices.mulPose(Axis.YP.rotationDegrees(90 * i));
                matrices.mulPose(Axis.ZP.rotationDegrees(-70 * i));
                matrices.mulPose(Axis.XP.rotationDegrees(-100 - (6 * 10) * anim));
            }
            case "360" -> {
                matrices.translate(0.56F * i, -0.52F, -0.72F);
                matrices.mulPose(Axis.XP.rotationDegrees(-swingProgress * 360));
            }
            case "Down" -> {
                float yPosition;
                if (swingProgress < 0.8f) {
                    yPosition = -0.52F - (swingProgress * 1.25f);
                } else {
                    float returnProgress = (swingProgress - 0.8f) * 5f;
                    yPosition = -0.52F - (1.0f - returnProgress);
                }
                matrices.translate(0.56F * i, yPosition, -0.72F);
            }
            case "Glide" -> {
                applyEquipOffset(matrices, arm, 0);
                matrices.mulPose(Axis.YP.rotationDegrees(80 * i));
                matrices.mulPose(Axis.XP.rotationDegrees(-80));
                matrices.translate(0, 0, -0.8 * swingProgress);
            }
            case "DropDown" -> {
                float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
                applyEquipOffset(matrices, arm, 0);
                matrices.mulPose(Axis.YP.rotationDegrees(80.0F));
                matrices.mulPose(Axis.XP.rotationDegrees(corner.get().floatValue()));
                matrices.mulPose(Axis.XP.rotationDegrees(-slant.get().floatValue() * anim));
            }
            case "DeadCode" -> {
                float anim = (float) Math.sin(swingProgress * (Math.PI / 2) * 2);
                applyEquipOffset(matrices, arm, 0);
                matrices.mulPose(Axis.YP.rotationDegrees(45.0F));
                matrices.mulPose(Axis.XP.rotationDegrees(anim * -40.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(30.0F));
                matrices.mulPose(Axis.XP.rotationDegrees(-80.0F));
                matrices.mulPose(Axis.YP.rotationDegrees(60.0F));
            }
        }
    }


    public void renderFirstPersonItem(
            AbstractClientPlayer player,
            float tickDelta,
            float pitch,
            InteractionHand hand,
            float swingProgress,
            ItemStack item,
            float equipProgress,
            PoseStack matrices,
            MultiBufferSource vertexConsumers,
            int light
    ) {
        if (!player.isScoping()) {
            boolean bl = hand == InteractionHand.MAIN_HAND;
            HumanoidArm arm = bl ? player.getMainArm() : player.getMainArm().getOpposite();
            matrices.pushPose();
            if (item.is(Items.CROSSBOW)) {
                boolean bl2 = CrossbowItem.isCharged(item);
                boolean bl3 = arm == HumanoidArm.RIGHT;
                int i = bl3 ? 1 : -1;
                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    matrices.translate((float) i * -0.4785682F, -0.094387F, 0.05731531F);
                    matrices.mulPose(Axis.XP.rotationDegrees(-11.935F));
                    matrices.mulPose(Axis.YP.rotationDegrees((float) i * 65.3F));
                    matrices.mulPose(Axis.ZP.rotationDegrees((float) i * -9.785F));
                    float f = (float) item.getUseDuration(mc.player) - ((float) mc.player.getUseItemRemainingTicks() - tickDelta + 1.0F);
                    float g = f / (float) CrossbowItem.getChargeDuration(item, mc.player);
                    if (g > 1.0F) {
                        g = 1.0F;
                    }

                    if (g > 0.1F) {
                        float h = Mth.sin((f - 0.1F) * 1.3F);
                        float j = g - 0.1F;
                        float k = h * j;
                        matrices.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                    }

                    matrices.translate(g * 0.0F, g * 0.0F, g * 0.04F);
                    matrices.scale(1.0F, 1.0F, 1.0F + g * 0.2F);
                    matrices.mulPose(Axis.YN.rotationDegrees((float) i * 45.0F));
                } else {
                    float fx = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    float gx = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
                    float h = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
                    matrices.translate((float) i * fx, gx, h);
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    this.applySwingOffset(matrices, arm, swingProgress);
                    if (bl2 && swingProgress < 0.001F && bl) {
                        matrices.translate((float) i * -0.641864F, 0.0F, 0.0F);
                        matrices.mulPose(Axis.YP.rotationDegrees((float) i * 10.0F));
                    }
                }
                EventHeldItemRenderer event = new EventHeldItemRenderer(hand, item, equipProgress, matrices);
                Event.call(event);
                this.renderItem(player, item, bl3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !bl3, matrices, vertexConsumers, light);
            } else {
                boolean bl2 = arm == HumanoidArm.RIGHT;
                ViewModel viewModel = Manager.FUNCTION_MANAGER.viewModel;
                if (viewModel.state) {
                    if (bl2) {
                        matrices.translate(viewModel.right_x.get().floatValue(), viewModel.right_y.get().floatValue(), viewModel.right_z.get().floatValue());
                    } else {
                        matrices.translate(-viewModel.left_x.get().floatValue(), viewModel.left_y.get().floatValue(), viewModel.left_z.get().floatValue());
                    }
                }

                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    int l = bl2 ? 1 : -1;
                    switch (item.getUseAnimation()) {
                        case NONE, BLOCK:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case EAT:
                        case DRINK:
                            this.applyEatOrDrinkTransformation(matrices, tickDelta, arm, item);
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            break;
                        case BOW:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float) l * -0.2785682F, 0.18344387F, 0.15731531F);
                            matrices.mulPose(Axis.XP.rotationDegrees(-13.935F));
                            matrices.mulPose(Axis.YP.rotationDegrees((float) l * 35.3F));
                            matrices.mulPose(Axis.ZP.rotationDegrees((float) l * -9.785F));
                            float mx = (float) item.getUseDuration(mc.player) - ((float) mc.player.getUseItemRemainingTicks() - tickDelta + 1.0F);
                            float fxx = mx / 20.0F;
                            fxx = (fxx * fxx + fxx * 2.0F) / 3.0F;
                            if (fxx > 1.0F) {
                                fxx = 1.0F;
                            }

                            if (fxx > 0.1F) {
                                float gx = Mth.sin((mx - 0.1F) * 1.3F);
                                float h = fxx - 0.1F;
                                float j = gx * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(fxx * 0.0F, fxx * 0.0F, fxx * 0.04F);
                            matrices.scale(1.0F, 1.0F, 1.0F + fxx * 0.2F);
                            matrices.mulPose(Axis.YN.rotationDegrees((float) l * 45.0F));
                            break;
                        case SPEAR:
                            this.applyEquipOffset(matrices, arm, equipProgress);
                            matrices.translate((float) l * -0.5F, 0.7F, 0.1F);
                            matrices.mulPose(Axis.XP.rotationDegrees(-55.0F));
                            matrices.mulPose(Axis.YP.rotationDegrees((float) l * 35.3F));
                            matrices.mulPose(Axis.ZP.rotationDegrees((float) l * -9.785F));
                            float m = (float) item.getUseDuration(mc.player) - ((float) mc.player.getUseItemRemainingTicks() - tickDelta + 1.0F);
                            float fx = m / 10.0F;
                            if (fx > 1.0F) {
                                fx = 1.0F;
                            }

                            if (fx > 0.1F) {
                                float gx = Mth.sin((m - 0.1F) * 1.3F);
                                float h = fx - 0.1F;
                                float j = gx * h;
                                matrices.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            matrices.translate(0.0F, 0.0F, fx * 0.2F);
                            matrices.scale(1.0F, 1.0F, 1.0F + fx * 0.2F);
                            matrices.mulPose(Axis.YN.rotationDegrees((float) l * 45.0F));
                            break;
                        case BRUSH:
                            this.applyBrushTransformation(matrices, tickDelta, arm, item, equipProgress);
                    }
                } else if (player.isAutoSpinAttack()) {
                    this.applyEquipOffset(matrices, arm, equipProgress);
                    int l = bl2 ? 1 : -1;
                    matrices.translate((float) l * -0.4F, 0.8F, 0.3F);
                    matrices.mulPose(Axis.YP.rotationDegrees((float) l * 65.0F));
                    matrices.mulPose(Axis.ZP.rotationDegrees((float) l * -85.0F));
                } else {
                    if (arm == mc.player.getMainArm() && this.state) {

                        renderSwordAnimation(matrices, swingProgress, equipProgress, arm);
                    } else {
                        float n = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                        float mxx = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
                        float fxxx = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
                        int o = bl2 ? 1 : -1;
                        matrices.translate((float) o * n, mxx, fxxx);
                        this.applyEquipOffset(matrices, arm, equipProgress);
                        this.applySwingOffset(matrices, arm, swingProgress);
                    }
                }
                EventHeldItemRenderer event = new EventHeldItemRenderer(hand, item, equipProgress, matrices);
                Event.call(event);
                this.renderItem(player, item, bl2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND, !bl2, matrices, vertexConsumers, light);
            }

            matrices.popPose();
        }
    }

    private void applyBrushTransformation(PoseStack matrices, float tickDelta, HumanoidArm arm, ItemStack stack, float equipProgress) {
        this.applyEquipOffset(matrices, arm, equipProgress);
        float f = (float) (mc.player.getUseItemRemainingTicks() % 10);
        float g = f - tickDelta + 1.0F;
        float h = 1.0F - g / 10.0F;
        float n = -15.0F + 75.0F * Mth.cos(h * 2.0F * (float) Math.PI);
        if (arm != HumanoidArm.RIGHT) {
            matrices.translate(0.1, 0.83, 0.35);
            matrices.mulPose(Axis.XP.rotationDegrees(-80.0F));
            matrices.mulPose(Axis.YP.rotationDegrees(-90.0F));
            matrices.mulPose(Axis.XP.rotationDegrees(n));
            matrices.translate(-0.3, 0.22, 0.35);
        } else {
            matrices.translate(-0.25, 0.22, 0.35);
            matrices.mulPose(Axis.XP.rotationDegrees(-80.0F));
            matrices.mulPose(Axis.YP.rotationDegrees(90.0F));
            matrices.mulPose(Axis.ZP.rotationDegrees(0.0F));
            matrices.mulPose(Axis.XP.rotationDegrees(n));
        }
    }

    private void applyEatOrDrinkTransformation(PoseStack matrices, float tickDelta, HumanoidArm arm, ItemStack stack) {
        float f = (float) mc.player.getUseItemRemainingTicks() - tickDelta + 1.0F;
        float g = f / (float) stack.getUseDuration(mc.player);
        if (g < 0.8F) {
            float h = Mth.abs(Mth.cos(f / 4.0F * (float) Math.PI) * 0.1F);
            matrices.translate(0.0F, h, 0.0F);
        }

        float h = 1.0F - (float) Math.pow(g, 27.0);
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate(h * 0.6F * (float) i, h * -0.5F, h * 0.0F);
        matrices.mulPose(Axis.YP.rotationDegrees((float) i * h * 90.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(h * 10.0F));
        matrices.mulPose(Axis.ZP.rotationDegrees((float) i * h * 30.0F));
    }

    private void applyEquipOffset(PoseStack matrices, HumanoidArm arm, float equipProgress) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        matrices.translate((float) i * 0.56F, -0.52F + equipProgress * -0.6F, -0.72F);
    }

    private void applySwingOffset(PoseStack matrices, HumanoidArm arm, float swingProgress) {
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        matrices.mulPose(Axis.YP.rotationDegrees((float) i * (45.0F + f * -20.0F)));
        float g = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        matrices.mulPose(Axis.ZP.rotationDegrees((float) i * g * -20.0F));
        matrices.mulPose(Axis.XP.rotationDegrees(g * -80.0F));
        matrices.mulPose(Axis.YP.rotationDegrees((float) i * -45.0F));
    }

    public void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, boolean leftHanded, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        if (!stack.isEmpty()) {
            mc.getItemRenderer().renderStatic(entity, stack, renderMode, leftHanded, matrices, vertexConsumers, entity.level(), light, OverlayTexture.NO_OVERLAY, entity.getId() + renderMode.ordinal());
        }
    }

    @Override
    public void onEvent(Event event) {
    }
}
