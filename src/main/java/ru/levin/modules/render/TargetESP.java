package ru.levin.modules.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.levin.events.Event;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.util.animations.impl.EaseInOutQuad;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.math.RayTraceUtil;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.providers.ResourceProvider;

import java.awt.*;
import java.util.Arrays;

import static ru.levin.util.math.MathUtil.interpolate;
import static ru.levin.util.math.MathUtil.interpolateFloat;
import static ru.levin.util.render.RenderUtil.*;

@SuppressWarnings("All")
@FunctionAnnotation(name = "TargetESP", desc = "Красивый указатель на вашем противнике", type = Type.Render)
public class TargetESP extends Function {
    private final ModeSetting mode = new ModeSetting("Мод","Призраки","Маркер","Маркер2","Призраки","Кружок");
    // откуда брать цель для указателя: ближний бой (AttackAura) и/или аимбот по стволам (GunAimbot)
    private final MultiSetting source = new MultiSetting("Источник",
            Arrays.asList("AttackAura", "GunAimbot"),
            new String[]{"AttackAura", "GunAimbot"});

    private final float[] SCALE_CACHE = new float[101];
    private final EaseInOutQuad animation = new EaseInOutQuad(800, 1);
    private Entity lastTarget = null;
    private double scale = 0.0D;
    public TargetESP() {
        addSettings(mode, source);
        for (int i = 0; i <= 100; i++) SCALE_CACHE[i] = Math.max(0.28f * (i / 100f), 0.2f);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventRender3D renderEvent)) return;
        Entity currentTarget = null;
        if (source.get("AttackAura")) currentTarget = Manager.FUNCTION_MANAGER.attackAura.target;
        if (currentTarget == null && source.get("GunAimbot")) currentTarget = Manager.FUNCTION_MANAGER.gunAimbot.getTarget();

        if (currentTarget != null && (lastTarget == null || !lastTarget.equals(currentTarget))) {
            animation.setDirection(Direction.AxisDirection.POSITIVE);
        } else if (currentTarget == null) {
            animation.setDirection(Direction.AxisDirection.NEGATIVE);
        }

        lastTarget = currentTarget;

        if (currentTarget != null) {
            if (mode.is("Маркер") || mode.is("Маркер2")) {
                render(currentTarget);
            } else if (mode.is("Призраки")) {
                renderGhosts(14, 8, 1.8f, 3f, currentTarget);
            } else if (mode.is("Кружок")) {
                cicle(currentTarget, renderEvent.getMatrixStack(), renderEvent.getDeltatick().getGameTimeDeltaPartialTick(true));
            }
        }
    }
    @Override
    public void onDisable() {
        super.onDisable();
    }

    public void renderGhosts(int espLength, int factor, float shaking, float amplitude, Entity target) {
        if (target == null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;
        float hitProgress = RayTraceUtil.getHitProgress(target);
        float delta = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 camPos = camera.getPosition();
        double tX = interpolate(target.xo, target.getX(), delta) - camPos.x;
        double tY = interpolate(target.yo, target.getY(), delta) - camPos.y;
        double tZ = interpolate(target.zo, target.getZ(), delta) - camPos.z;
        float age = interpolateFloat(target.tickCount - 1, target.tickCount, delta);

        boolean canSee = mc.player.hasLineOfSight(target);

        RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderTexture(0, ResourceProvider.firefly);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (canSee) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
        } else {
            RenderSystem.disableDepthTest();
        }

        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float pitch = camera.getXRot();
        float yaw = camera.getYRot();
        float ghostAlpha = (float) animation.getOutput();

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i <= espLength; i++) {
                float offset = (float) i / espLength;
                double radians = Math.toRadians(((i / 1.5f + age) * factor + j * 120) % (factor * 360));
                double sinQuad = Math.sin(Math.toRadians(age * 2.5f + i * (j + 1)) * amplitude) / shaking;

                PoseStack matrices = new PoseStack();
                matrices.mulPose(Axis.XP.rotationDegrees(pitch));
                matrices.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
                matrices.translate(tX + Math.cos(radians) * target.getBbWidth(), tY + 1 + sinQuad, tZ + Math.sin(radians) * target.getBbWidth());
                matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
                matrices.mulPose(Axis.XP.rotationDegrees(pitch));

                Matrix4f matrix = matrices.last().pose();
                int baseColor;
                if (hitProgress > 0) {
                    baseColor = Color.RED.getRGB();
                } else {
                    baseColor = ColorUtil.getColorStyle((int) (180 * offset));
                }

                int color = applyOpacity(baseColor, offset * ghostAlpha);

                float scale = SCALE_CACHE[Math.min((int)(offset * 100), 100)];
                buffer.addVertex(matrix, -scale,  scale, 0).setUv(0f, 1f).setColor(color);
                buffer.addVertex(matrix,  scale,  scale, 0).setUv(1f, 1f).setColor(color);
                buffer.addVertex(matrix,  scale, -scale, 0).setUv(1f, 0).setColor(color);
                buffer.addVertex(matrix, -scale, -scale, 0).setUv(0f, 0).setColor(color);
            }
        }
        RenderUtil.render3D.endBuilding(buffer);

        if (canSee) {
            RenderSystem.depthMask(true);
            RenderSystem.disableDepthTest();
        } else {
            RenderSystem.enableDepthTest();
        }
        RenderSystem.disableBlend();
    }

    private void cicle(Entity target, PoseStack matrices, float tickDelta) {
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        double x = Mth.lerp(tickDelta, target.xOld, target.getX()) - camPos.x;
        double z = Mth.lerp(tickDelta, target.zOld, target.getZ()) - camPos.z;
        double y = Mth.lerp(tickDelta, target.yOld, target.getY()) - camPos.y + Math.min(Math.sin(System.currentTimeMillis() / 400.0) + 0.95, target.getBbHeight());

        disableDepth();
        RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f matrix = matrices.last().pose();
        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        int baseColor = ColorUtil.getColorStyle(360);
        float r = ((baseColor >> 16) & 0xFF) / 255f;
        float g = ((baseColor >> 8) & 0xFF) / 255f;
        float b = (baseColor & 0xFF) / 255f;

        float alpha = (float) animation.getOutput();

        float radius = target.getBbWidth() * 0.8f;

        for (float i = 0; i <= Math.PI * 2 + (Math.PI * 5 / 100); i += Math.PI * 5 / 100) {
            double vecX = x + radius * Math.cos(i);
            double vecZ = z + radius * Math.sin(i);

            buffer.addVertex(matrix, (float) vecX, (float) (y - Math.cos(System.currentTimeMillis() / 400.0) / 2), (float) vecZ).setColor(r, g, b, 0.01f * alpha);
            buffer.addVertex(matrix, (float) vecX, (float) y, (float) vecZ).setColor(r, g, b, 1f * alpha);
        }

        RenderUtil.render3D.endBuilding(buffer);
        endRender();
    }

    private static void disableDepth() {
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
    }

    private static void endRender() {
        RenderUtil.disableRender();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private void render(Entity target) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (camera == null) return;

        scale = animation.getOutput();
        if (scale == 0.0) return;

        float delta = mc.getTimer().getGameTimeDeltaPartialTick(true);
        float hitProgress = RayTraceUtil.getHitProgress(target);
        Vec3 camPos = camera.getPosition();
        double tX = interpolate(target.xo, target.getX(), delta) - camPos.x;
        double tY = interpolate(target.yo, target.getY(), delta) - camPos.y;
        double tZ = interpolate(target.zo, target.getZ(), delta) - camPos.z;
        PoseStack matrices = setupMatrices(camera, target, delta, tX, tY, tZ);
        Matrix4f matrix = matrices.last().pose();
        disableDepth();
        RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        if (mode.is("Маркер")) {
            RenderSystem.setShaderTexture(0, ResourceProvider.marker);
        }
        if (mode.is("Маркер2")) {
            RenderSystem.setShaderTexture(0, ResourceProvider.marker2);
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        float alpha = (float) animation.getOutput();
        int[] baseColors = hitProgress > 0 ? new int[]{Color.RED.getRGB(), ColorUtil.getColorStyle(0), Color.RED.getRGB(), ColorUtil.getColorStyle(270)} : new int[]{ColorUtil.getColorStyle(90), ColorUtil.getColorStyle(0), ColorUtil.getColorStyle(180), ColorUtil.getColorStyle(270)};

        drawQuad(matrix, applyAlphaToColors(baseColors, alpha));
        endRender();
    }

    private PoseStack setupMatrices(Camera camera, Entity target, float delta, double tX, double tY, double tZ) {
        PoseStack matrices = new PoseStack();
        float pitch = camera.getXRot();
        float yaw = camera.getYRot();

        matrices.mulPose(Axis.XP.rotationDegrees(pitch));
        matrices.mulPose(Axis.YP.rotationDegrees(yaw + 180f));
        matrices.translate(tX, tY + target.getEyeHeight(target.getPose()) / 2f, tZ);
        matrices.mulPose(Axis.YP.rotationDegrees(-yaw));
        matrices.mulPose(Axis.XP.rotationDegrees(pitch));

        float interpolatedAngle = interpolateFloat(1f, 1f, delta);
        matrices.mulPose(Axis.ZP.rotationDegrees(interpolatedAngle));

        float radians = (float) Math.toRadians(System.currentTimeMillis() % 3600 / 5f);
        matrices.mulPose(new Matrix4f().rotate(radians, 0, 0, 1));
        matrices.translate(-0.75, -0.75, -0.01);
        return matrices;
    }

    private int[] applyAlphaToColors(int[] colors, float alpha) {
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            Color color = new Color(colors[i]);
            out[i] = new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * alpha)).getRGB();
        }
        return out;
    }

    private void drawQuad(Matrix4f matrix, int[] colors) {
        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix,0,1.5f,0).setUv(0f,1f).setColor(colors[0]);
        buffer.addVertex(matrix,1.5f,1.5f,0).setUv(1f,1f).setColor(colors[1]);
        buffer.addVertex(matrix,1.5f,0,0).setUv(1f,0).setColor(colors[2]);
        buffer.addVertex(matrix,0,0,0).setUv(0f,0).setColor(colors[3]);
        RenderUtil.render3D.endBuilding(buffer);
    }
}
