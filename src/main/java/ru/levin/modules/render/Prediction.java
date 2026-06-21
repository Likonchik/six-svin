package ru.levin.modules.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.math.MathUtil;
import ru.levin.util.render.RenderAddon;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.shader.ShaderManager;
import ru.levin.util.vector.VectorUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static ru.levin.util.render.RenderUtil.render3D.drawHoleOutline;

@SuppressWarnings("All")
@FunctionAnnotation(name = "Prediction", type = Type.Render, desc = "Рисует линию куда упадёт эндер-жемчюг")
public class Prediction extends Function {
    private final BooleanSetting box = new BooleanSetting("Рисовать бокс",false);
    private final BooleanSetting rect = new BooleanSetting("Рисовать рект под эндер-жемчюгом",false);
    private static final ItemStack ENDER_PEARL_STACK = new ItemStack(Items.ENDER_PEARL);
    private static final Color BOX_COLOR = new Color(255, 255, 255, 255);
    private static final int MAX_STEPS = 150;
    private static final float FADE_LEN = 6.0f;

    private final List<PearlPoint> pearlPoints = new ArrayList<>();

    public Prediction() {
        addSettings(box,rect);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventRender2D render2D) {
            for (PearlPoint pearlPoint : pearlPoints) {

                Vector3d projection = VectorUtil.toScreen(pearlPoint.position.x, pearlPoint.position.y - 0.3F, pearlPoint.position.z);
                if (projection == null || projection.z < 0) continue;
                double time = pearlPoint.ticks * 0.05;
                String text = String.format("%.1f сек", time);

                float fontHeight = FontUtils.durman[15].getHeight();
                float textWidth = FontUtils.durman[15].getWidth(text);

                float paddingX = 3f;

                float bgWidth = textWidth + paddingX * 2;
                float bgHeight = fontHeight + 1;

                float centerX = (float) projection.x;
                float centerY = (float) projection.y;

                float bgX = centerX - bgWidth / 2f;
                float bgY = centerY;
                RenderUtil.drawRoundedRect(render2D.getMatrixStack(), bgX, bgY, bgWidth, bgHeight, 2, 0xB2060712);
                float textX = centerX - textWidth / 2f;
                float textY = bgY + (bgHeight - fontHeight) / 2f;
                FontUtils.durman[15].drawLeftAligned(render2D.getDrawContext().pose(), text, textX, textY, -1);

                float pearlSize = 11;
                float pearlX = centerX - pearlSize / 2f;
                float pearlY = bgY - pearlSize - 2f;
                if (rect.get()) {
                    RenderUtil.drawRoundedRect(render2D.getMatrixStack(), pearlX - 0.5f, pearlY - 0.2f, 12, 12, 2, 0xB2060712);
                }
                RenderAddon.renderItem(render2D.getDrawContext(), ENDER_PEARL_STACK, pearlX, pearlY, pearlSize / 16f,false);
            }
        }
        if (event instanceof EventRender3D e3d) {
            renderTrajectories(e3d);
        }
    }

    private void renderTrajectories(EventRender3D event) {
        PoseStack stack = event.getMatrixStack();
        Vec3 cameraPos = mc.getEntityRenderDispatcher().camera.getPosition();

        stack.pushPose();
        stack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        RenderSystem.lineWidth(3);

        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        pearlPoints.clear();

        for (Entity entity : Manager.SYNC_MANAGER.getEntities()) {
            if (entity instanceof ThrownEnderpearl enderPearlEntity)
                simulatePearl(stack, buffer, enderPearlEntity);
        }

        RenderUtil.render3D.endBuilding(buffer);

        if (box.get()) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            for (PearlPoint pearlPoint : pearlPoints) {
                Vec3 pos = pearlPoint.position;
                AABB outlineBox = new AABB(pos.x - 0.15, pos.y - 0.15, pos.z - 0.15, pos.x + 0.15, pos.y + 0.15, pos.z + 0.15);
                drawHoleOutline(outlineBox, BOX_COLOR.getRGB(), 1);
            }
        }


        RenderUtil.disableRender();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        stack.popPose();
    }

    private void simulatePearl(PoseStack stack, BufferBuilder buffer, ThrownEnderpearl pearl) {
        Vec3 motion = pearl.getDeltaMovement();
        Vec3 pos = pearl.position();
        int ticks = 0;

        float dist = 0f;
        int baseRGB = ColorUtil.getColorStyle(360) & 0x00FFFFFF;

        for (int i = 0; i < MAX_STEPS; i++) {
            Vec3 prevPos = pos;
            pos = pos.add(motion);
            motion = getNextMotion(pearl, prevPos, motion);

            HitResult hitResult = mc.level.clip(new ClipContext(prevPos, pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pearl));
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                pos = hitResult.getLocation();
            }

            float segLen = (float) prevPos.distanceTo(pos);
            float a1 = MathUtil.smoothstep(0f, FADE_LEN, dist);
            float a2 = MathUtil.smoothstep(0f, FADE_LEN, dist + segLen);

            int c1 = ColorUtil.withAlpha(baseRGB, a1);
            int c2 = ColorUtil.withAlpha(baseRGB, a2);

            vertexLineGradient(stack, buffer, (float) prevPos.x, (float) prevPos.y, (float) prevPos.z, (float) pos.x, (float) pos.y, (float) pos.z, c1, c2);

            dist += segLen;

            if (hitResult.getType() == HitResult.Type.BLOCK || pos.y < -128) {
                pearlPoints.add(new PearlPoint(pos, ticks));
                break;
            }
            ticks++;
        }
    }

    private void vertexLineGradient(PoseStack matrices, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, int color1, int color2) {
        Matrix4f model = matrices.last().pose();
        float[] col1 = ColorUtil.rgba(color1);
        float[] col2 = ColorUtil.rgba(color2);
        Vector3f normalVec = ShaderManager.getNormal(x1, y1, z1, x2, y2, z2);

        buffer.addVertex(model, x1, y1, z1).setColor(col1[0], col1[1], col1[2], col1[3]).setNormal(matrices.last(), normalVec.x(), normalVec.y(), normalVec.z());
        buffer.addVertex(model, x2, y2, z2).setColor(col2[0], col2[1], col2[2], col2[3]).setNormal(matrices.last(), normalVec.x(), normalVec.y(), normalVec.z());
    }

    private Vec3 getNextMotion(ThrowableProjectile throwable, Vec3 prevPos, Vec3 motion) {
        boolean isInWater = mc.level.getBlockState(BlockPos.containing(prevPos)).getFluidState().is(FluidTags.WATER);

        motion = motion.scale(isInWater ? 0.8 : 0.99);

        if (!throwable.isNoGravity()) {
            motion = motion.add(0, -0.03F, 0);
        }
        return motion;
    }

    record PearlPoint(Vec3 position, int ticks) {}
}
