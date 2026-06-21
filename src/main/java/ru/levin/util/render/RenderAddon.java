package ru.levin.util.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.IMinecraft;
import ru.levin.util.render.providers.ShaderRegistry;

import java.util.*;

@SuppressWarnings("All")
public class RenderAddon implements IMinecraft {
    private static AbstractClientPlayer fakePlayer;
    public static void renderFakePlayer(Vec3 defensivePos, boolean fakelags, boolean isAccumulatingPackets, EventRender3D render) {
        if (defensivePos == null || !fakelags || !isAccumulatingPackets)
            return;

        if (fakePlayer == null) {
            createFakePlayer();
        }

        double x = defensivePos.x;
        double y = defensivePos.y;
        double z = defensivePos.z;
        fakePlayer.setPos(x, y, z);
        fakePlayer.xo = x;
        fakePlayer.yo = y;
        fakePlayer.zo = z;
        fakePlayer.xOld = x;
        fakePlayer.yOld = y;
        fakePlayer.zOld = z;

        fakePlayer.setYRot(mc.player.getYRot());
        fakePlayer.setXRot(mc.player.getXRot());
        fakePlayer.yHeadRot = mc.player.yHeadRot;
        fakePlayer.yBodyRot = mc.player.yBodyRot;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        EntityRenderer<? super AbstractClientPlayer> rawRenderer = dispatcher.getRenderer(fakePlayer);
        @SuppressWarnings("unchecked")
        EntityRenderer<AbstractClientPlayer> renderer = (EntityRenderer<AbstractClientPlayer>) rawRenderer;

        float partialTick = render.getDeltatick().getGameTimeDeltaPartialTick(true);

        PoseStack matrices = render.getMatrixStack();
        MultiBufferSource.BufferSource vertexConsumers = mc.renderBuffers().bufferSource();

        Vec3 camPos = dispatcher.camera.getPosition();
        matrices.pushPose();
        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.35f);

        renderer.render(fakePlayer, fakePlayer.getYRot(), partialTick, matrices, vertexConsumers, 15728880);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        vertexConsumers.endBatch();
        matrices.popPose();
    }

    public static void sizeAnimation(PoseStack matrix, double width, double height, double scale) {
        matrix.translate(width, height, 0);
        matrix.scale((float) scale, (float) scale, (float) scale);
        matrix.translate(-width, -height, 0);
    }

    public static void renderItem(GuiGraphics drawContext, ItemStack item, float x, float y, float size, boolean stackOverlay) {
        drawContext.pose().pushPose();
        drawContext.pose().translate(x, y, 0);
        drawContext.pose().scale(size, size, 1);
        drawContext.renderItem(item, 0, 0);
        if (stackOverlay) {
            drawContext.renderItemDecorations(mc.font, item, 0, 0);
        }
        drawContext.pose().popPose();
    }

    public static void renderPlayerItems(GuiGraphics e, float x, float y, LivingEntity player, float scale, float offset) {
        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(player.getMainHandItem());
        player.getArmorSlots().forEach(stacks::add);
        stacks.add(player.getOffhandItem());
        stacks.removeIf(i -> i.getItem() instanceof AirItem || i.isEmpty());

        float offset2 = 0;
        for (ItemStack stack : stacks) {
            e.pose().pushPose();
            e.pose().translate(x + offset2, y, 0);
            e.pose().scale(scale, scale, 1.0f);
            e.renderItem(stack, 0, 0, 7, 0);

            e.renderItemDecorations(mc.font, stack, 0, 0);

            e.pose().popPose();

            offset2 += offset;
        }
    }
    public static void drawHead(PoseStack matrix, Entity entity, float x, float y, float size, float round) {
        if (!(entity instanceof LivingEntity living)) return;
        int color = 0xFFFFFFFF;
        if (living.hurtTime > 0) {
            float hurtPercent = living.hurtTime / (float) living.hurtDuration;
            int red = 255;
            int green = (int) (255 * (1.0f - hurtPercent));
            int blue = (int) (255 * (1.0f - hurtPercent));
            color = (255 << 24) | (red << 16) | (green << 8) | blue;
        }
        ResourceLocation texture = null;

        if (entity instanceof Player player) {
            PlayerInfo entry = Optional.ofNullable(mc.getConnection()).map(handler -> handler.getPlayerInfo(player.getUUID())).orElse(null);
            if (entry != null) {
                texture = entry.getSkin().texture();
            }
        }

        if (texture == null) {
            EntityRenderer<? super LivingEntity> baseRenderer = mc.getEntityRenderDispatcher().getRenderer(living);
            if (baseRenderer instanceof LivingEntityRenderer<?, ?>) {
                @SuppressWarnings("unchecked")
                LivingEntityRenderer<LivingEntity, ?> renderer = (LivingEntityRenderer<LivingEntity, ?>) baseRenderer;
                texture = renderer.getTextureLocation(living);
            }
        }

        if (texture != null) {
            drawHeadInternal(matrix, texture, x, y, size, round, color);
        }
    }


    private static void drawHeadInternal(PoseStack matrix, ResourceLocation texture, float x, float y, float size, float rounding, int color) {
        RenderUtil.enableRender();

        ShaderInstance shader = ShaderRegistry.TEXTURE;
        RenderSystem.setShaderTexture(0, texture);

        shader.getUniform("Size").set(size, size);
        shader.getUniform("Radius").set(rounding, rounding, rounding, rounding);
        shader.getUniform("Smoothness").set(1.0f);

        RenderSystem.setShader(() -> shader);

        float u1 = 8f / 64f; float v1 = 8f / 64f;
        float u2 = 16f / 64f; float v2 = 16f / 64f;


        Matrix4f matrix4f = matrix.last().pose();
        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix4f, x, y, 0).setUv(u1, v1).setColor(color);
        buffer.addVertex(matrix4f, x + size, y, 0).setUv(u2, v1).setColor(color);
        buffer.addVertex(matrix4f, x + size, y + size, 0).setUv(u2, v2).setColor(color);
        buffer.addVertex(matrix4f, x, y + size, 0).setUv(u1, v2).setColor(color);
        RenderUtil.render3D.endBuilding(buffer);

        RenderUtil.disableRender();
    }


    public static void drawStaffHead(PoseStack matrix, ResourceLocation texture, float x, float y, float size, float round) {
        if (texture != null) {
            drawHeadInternal(matrix, texture, x, y, size, round, 0xFFFFFFFF);
        }
    }
    private static void createFakePlayer() {
        if (mc.player == null || mc.level == null) return;

        fakePlayer = new AbstractClientPlayer(mc.level, mc.player.getGameProfile()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };

        fakePlayer.restoreFrom(mc.player);
        fakePlayer.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        fakePlayer.yHeadRot = mc.player.yHeadRot;
        fakePlayer.yBodyRot = mc.player.yBodyRot;
    }
}
