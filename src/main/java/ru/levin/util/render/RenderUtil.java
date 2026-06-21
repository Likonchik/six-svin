package ru.levin.util.render;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.*;
import org.lwjgl.opengl.GL11;
import ru.levin.manager.IMinecraft;
import ru.levin.modules.render.HUD;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.providers.ShaderRegistry;
import ru.levin.util.shader.ShaderManager;

import java.awt.*;

import static org.lwjgl.opengl.GL11C.GL_ONE;
import static ru.levin.util.math.MathUtil.interpolate;

@SuppressWarnings("All")
public class RenderUtil implements IMinecraft {
    private static final Supplier<TextureTarget> TEMP_FBO_SUPPLIER = Suppliers.memoize(() -> new TextureTarget(mc.getWindow().getWidth(), mc.getWindow().getHeight(), false, false));
    private static RenderTarget getMainFbo() {
        return mc.getMainRenderTarget();
    }
    public static boolean isHovered(int mouseX, int mouseY, double x, double y, double width, double height) {
        return mouseX > x && mouseX < x + width && mouseY > y && mouseY < y + height;
    }
    public static boolean isInRegion(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    public static boolean isInRegion(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    public static int injectAlpha(int color, int alpha) {
        alpha = Mth.clamp(alpha, 0, 255);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    public static Vec3 interpolatePos(float prevX, float prevY, float prevZ, float x, float y, float z) {
        final Vec3 camPos = mc.getEntityRenderDispatcher().camera.getPosition();
        final double delta = IMinecraft.tickCounter().getGameTimeDeltaPartialTick(true);
        return new Vec3(interpolate(prevX, x, delta) - camPos.x, interpolate(prevY, y, delta) - camPos.y, interpolate(prevZ, z, delta) - camPos.z);
    }

    public static int applyOpacity(int color, float opacity) {
        opacity = Mth.clamp(opacity, 0f, 1f);
        int alpha = (int) (((color >>> 24) & 0xFF) * opacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static void setShaderUniforms(ShaderInstance shader, float width, float height, Vector4f radius, float smoothness) {
        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(radius.x, radius.y, radius.z, radius.w);
        shader.getUniform("Smoothness").set(smoothness);
    }

    private static void setShaderUniforms(ShaderInstance shader, float width, float height, float radius, float smoothness) {
        setShaderUniforms(shader, width, height, new Vector4f(radius, radius, radius, radius), smoothness);
    }

    public static void drawRoundedRect(PoseStack matrices, float x, float y, float width, float height, float rounding, int color) {
        drawRoundedRect(matrices, x, y, width, height, new Vector4f(rounding, rounding, rounding, rounding), color);
    }

    public static void drawRoundedRect(PoseStack matrices, float x, float y, float width, float height, Vector4f rounding, int color) {
        enableRender();
        ShaderInstance shader = ShaderRegistry.RECTANGLE;
        setShaderUniforms(shader, width, height, rounding, 1.0f);
        RenderSystem.setShader(() -> shader);
        ShaderManager.vertexShader(matrices, x, y, width, height, color);
        disableRender();
    }

    public static void rectRGB(PoseStack matrices, float x, float y, float width, float height, float rounding, int color1, int color2, int color3, int color4) {
        rectRGB(matrices, x, y, width, height, new Vector4f(rounding, rounding, rounding, rounding), color1, color2, color3, color4);
    }

    public static void rectRGB(PoseStack matrices, float x, float y, float width, float height, Vector4f rounding, int color1, int color2, int color3, int color4) {
        enableRender();
        ShaderInstance shader = ShaderRegistry.RECTANGLE;
        setShaderUniforms(shader, width, height, rounding, 1.0f);
        RenderSystem.setShader(() -> shader);
        ShaderManager.vertexShader(matrices, x, y, width, height, color1, color2, color3, color4);
        disableRender();
    }

    public static void drawRoundedBorder(PoseStack matrices, float x, float y, float width, float height, float rounding, float thickness, int color) {
        drawRoundedBorder(matrices, x, y, width, height, new Vector4f(rounding, rounding, rounding, rounding), thickness, color);
    }

    public static void drawRoundedBorder(PoseStack matrices, float x, float y, float width, float height, Vector4f rounding, float thickness, int color) {
        enableRender();
        ShaderInstance shader = ShaderRegistry.BORDER;
        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(rounding.x, rounding.y, rounding.z, rounding.w);
        shader.getUniform("Thickness").set(thickness);
        shader.getUniform("Smoothness").set(1.0f);
        RenderSystem.setShader(() -> shader);
        ShaderManager.vertexShader(matrices, x, y, width, height, color);
        disableRender();
    }
    public static void drawBlur(PoseStack matrices, float x, float y, float width, float height, float rounding, float blurRadius, int color) {
        drawBlur(matrices, x, y, width, height, new Vector4f(rounding, rounding, rounding, rounding), blurRadius, color);
    }

    public static void drawBlur(PoseStack matrices, float x, float y, float width, float height, Vector4f rounding, float blurRadius, int color) {
        final TextureTarget fbo = TEMP_FBO_SUPPLIER.get();
        final RenderTarget mainFbo = getMainFbo();

        if (fbo.width != mainFbo.width || fbo.height != mainFbo.height) {
            fbo.resize(mainFbo.width, mainFbo.height, false);
        }

        enableRender();
        fbo.bindWrite(false);
        mainFbo.blitToScreen(fbo.width, fbo.height);
        mainFbo.bindWrite(false);

        ShaderInstance shader = ShaderRegistry.BLUR;
        RenderSystem.setShaderTexture(0, fbo.getColorTextureId());

        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(rounding.x, rounding.y, rounding.z, rounding.w);
        shader.getUniform("Smoothness").set(1f);
        shader.getUniform("BlurRadius").set(blurRadius);
        RenderSystem.setShader(() -> shader);

        ShaderManager.vertexShader(matrices, x, y, width, height, color);

        RenderSystem.setShaderTexture(0, 0);
        disableRender();
    }
    public static void drawLiquidRect(PoseStack matrices, float x, float y, float width, float height, Vector4f rounding, float cornerSmoothness, float fresnelPower, float fresnelAlpha, float baseAlpha, boolean fresnelInvert, float fresnelMix, float distortStrength, ColorRGBA color) {
        matrices.pushPose();
        Matrix4f matrix4f = matrices.last().pose();
        RenderTarget screenFBO = mc.getMainRenderTarget();
        int screenTexture = screenFBO.getColorTextureId();
        ShaderInstance shader = ShaderRegistry.GLASS;
        BufferBuilder builder = RenderSystem.renderThreadTesselator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        shader.getUniform("ModelViewMat").set(matrix4f);
        shader.getUniform("ProjMat").set(RenderSystem.getProjectionMatrix());
        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(rounding.x, rounding.y, rounding.z, rounding.w);
        shader.getUniform("Smoothness").set(1.0f);
        shader.getUniform("CornerSmoothness").set(cornerSmoothness);
        shader.getUniform("GlobalAlpha").set(color.getAlpha() / 255f);
        shader.getUniform("FresnelPower").set(fresnelPower);
        shader.getUniform("FresnelColor").set(1f, 1f, 1f);
        shader.getUniform("FresnelAlpha").set(fresnelAlpha);
        shader.getUniform("BaseAlpha").set(baseAlpha);
        shader.getUniform("FresnelInvert").set(fresnelInvert ? 1 : 0);
        shader.getUniform("FresnelMix").set(fresnelMix);
        shader.getUniform("DistortStrength").set(distortStrength);
        RenderSystem.setShaderTexture(0, screenTexture);
        RenderSystem.setShader(() -> shader);
        enableRender();

        float scaleX = (float) screenFBO.width / mc.getWindow().getGuiScaledWidth();
        float scaleY = (float) screenFBO.height / mc.getWindow().getGuiScaledHeight();

        float fx = x * scaleX;
        float fy = y * scaleY;
        float fwidth = width * scaleX;
        float fheight = height * scaleY;
        fy = screenFBO.height - fy - fheight;

        float u0 = fx / screenFBO.width;
        float v0 = fy / screenFBO.height;
        float u1 = (fx + fwidth) / screenFBO.width;
        float v1 = (fy + fheight) / screenFBO.height;
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        int a = color.getAlpha();
        builder.addVertex(matrix4f, x, y, 0f).setUv(u0, v1).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x, y + height, 0f).setUv(u0, v0).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x + width, y + height, 0f).setUv(u1, v0).setColor(r, g, b, a);
        builder.addVertex(matrix4f, x + width, y, 0f).setUv(u1, v1).setColor(r, g, b, a);
        RenderUtil.render3D.endBuilding(builder);
        RenderSystem.setShaderTexture(0, 0);
        RenderUtil.disableRender();
        RenderSystem.enableDepthTest();
        matrices.popPose();
    }

    public static void drawTexture(PoseStack matrices, Object texture, float x, float y, float width, float height, float rounding, int color) {
        enableRender();
        ResourceLocation textureId;
        if (texture instanceof String path) {
            textureId = ResourceLocation.fromNamespaceAndPath("exosware", path);
        } else if (texture instanceof ResourceLocation id) {
            textureId = id;
        } else {
            throw new IllegalArgumentException("Texture must be ResourceLocation or String");
        }

        int glTextureId = mc.getTextureManager().getTexture(textureId).getId();
        ShaderInstance shader = ShaderRegistry.TEXTURE;
        RenderSystem.setShaderTexture(0, glTextureId);
        shader.getUniform("Size").set(width, height);
        shader.getUniform("Radius").set(rounding, rounding, rounding, rounding);
        shader.getUniform("Smoothness").set(1f);
        RenderSystem.setShader(() -> shader);

        Matrix4f mat = matrices.last().pose();
        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        buffer.addVertex(mat, x, y, 0).setUv(0f, 0f).setColor(color);
        buffer.addVertex(mat, x + width, y, 0).setUv(1f, 0f).setColor(color);
        buffer.addVertex(mat, x + width, y + height, 0).setUv(1f, 1f).setColor(color);
        buffer.addVertex(mat, x, y + height, 0).setUv(0f, 1f).setColor(color);

        RenderUtil.render3D.endBuilding(buffer);
        disableRender();
    }

    public static void drawCircleBorder(PoseStack matrices, float centerX, float centerY, float diameter, float thickness, int color) {
        enableRender();
        ShaderInstance shader = ShaderRegistry.BORDER;

        float radius = diameter / 2f;

        shader.getUniform("Size").set(diameter, diameter);
        shader.getUniform("Radius").set(radius, radius, radius, radius);
        shader.getUniform("Thickness").set(thickness);
        shader.getUniform("Smoothness").set(1.0f);
        RenderSystem.setShader(() -> shader);

        ShaderManager.vertexShader(matrices, centerX - radius, centerY - radius, diameter, diameter, color);
        disableRender();
    }

    public static void drawLine(float x1, float y1, float x2, float y2, int color) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(x1, y1, 0f).setColor(color);
        buffer.addVertex(x2, y2, 0f).setColor(color);
        RenderUtil.render3D.endBuilding(buffer);
    }
    public static void drawCircle(PoseStack matrix, float x, float y, float radius, int color) {
        drawRoundedRect(matrix, x - radius / 2f, y - radius / 2f, radius, radius, radius / 2f - 1, color);
    }
    public static void enableRender(GlStateManager.SourceFactor srcFactor, GlStateManager.DestFactor dstFactor) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(srcFactor, dstFactor);
    }
    public static void enableRender() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
    }
    public static void disableRender() {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 3D Рендеринг
     */
    public class render3D {
        public static final Matrix4f lastProjMat = new Matrix4f();
        public static final Matrix4f lastModMat = new Matrix4f();
        public static final Matrix4f lastWorldSpaceMatrix = new Matrix4f();

        public static void setTranslation(PoseStack matrixStack) {
            RenderUtil.render3D.lastProjMat.set(RenderSystem.getProjectionMatrix());
            RenderUtil.render3D.lastModMat.set(RenderSystem.getModelViewMatrix());
            RenderUtil.render3D.lastWorldSpaceMatrix.set(matrixStack.last().pose());
        }

        public static Vec3 worldSpaceToScreenSpace(Vec3 pos) {
            Camera camera = mc.getEntityRenderDispatcher().camera;
            int displayHeight = mc.getWindow().getHeight();
            int[] viewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            Vector3f target = new Vector3f();

            double deltaX = pos.x - camera.getPosition().x;
            double deltaY = pos.y - camera.getPosition().y;
            double deltaZ = pos.z - camera.getPosition().z;

            Vector4f transformedCoordinates = new Vector4f((float) deltaX, (float) deltaY, (float) deltaZ, 1.f).mul(lastWorldSpaceMatrix);
            Matrix4f matrixProj = new Matrix4f(lastProjMat);
            Matrix4f matrixModel = new Matrix4f(lastModMat);
            matrixProj.mul(matrixModel).project(transformedCoordinates.x(), transformedCoordinates.y(), transformedCoordinates.z(), viewport, target);

            return new Vec3(target.x / mc.getWindow().getGuiScale(), (displayHeight - target.y) / mc.getWindow().getGuiScale(), target.z);
        }


        public static PoseStack matrixFrom(double x, double y, double z) {
            PoseStack matrices = new PoseStack();

            Camera camera = mc.gameRenderer.getMainCamera();
            matrices.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            matrices.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));

            matrices.translate(x - camera.getPosition().x, y - camera.getPosition().y, z - camera.getPosition().z);

            return matrices;
        }
        public static void drawShape(BlockPos blockPos, VoxelShape shape, boolean depth, int color1, int color2, int color3, int color4) {
            Vec3 offset = Vec3.atLowerCornerOf(blockPos);
            shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
                AABB box = new AABB(minX, minY, minZ, maxX, maxY, maxZ).move(offset);
                PoseStack matrices = matrixFrom(box.minX, box.minY, box.minZ);
                AABB shiftedBox = box.move(new Vec3(-box.minX, -box.minY, -box.minZ));
                renderFillBox(matrices, shiftedBox ,depth, color1, color2, color3, color4);
            });
        }


        public static void drawHoleOutline(AABB box, int color, float lineWidth) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            RenderUtil.enableRender();
            PoseStack matrices = matrixFrom(box.minX, box.minY, box.minZ);

            BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            RenderSystem.lineWidth(lineWidth);

            box = box.move(new Vec3(box.minX, box.minY, box.minZ).reverse());

            float x1 = (float) box.minX;
            float y1 = (float) box.minY;
            float y2 = (float) box.maxY;
            float z1 = (float) box.minZ;
            float x2 = (float) box.maxX;
            float z2 = (float) box.maxZ;

            ShaderManager.vertexLine(matrices, buffer, x1, y2, z1, x2, y2, z1, color);
            ShaderManager.vertexLine(matrices, buffer, x2, y2, z1, x2, y2, z2, color);
            ShaderManager.vertexLine(matrices, buffer, x2, y2, z2, x1, y2, z2, color);
            ShaderManager.vertexLine(matrices, buffer, x1, y2, z2, x1, y2, z1, color);

            ShaderManager.vertexLine(matrices, buffer, x1, y1, z1, x2, y1, z1, color);
            ShaderManager.vertexLine(matrices, buffer, x2, y1, z1, x2, y1, z2, color);
            ShaderManager.vertexLine(matrices, buffer, x2, y1, z2, x1, y1, z2, color);
            ShaderManager.vertexLine(matrices, buffer, x1, y1, z2, x1, y1, z1, color);

            ShaderManager.vertexLine(matrices, buffer, x1, y1, z1, x1, y2, z1, color);
            ShaderManager.vertexLine(matrices, buffer, x2, y1, z2, x2, y2, z2, color);
            ShaderManager.vertexLine(matrices, buffer, x1, y1, z2, x1, y2, z2, color);
            ShaderManager.vertexLine(matrices, buffer, x2, y1, z1, x2, y2, z1, color);

            endBuilding(buffer);
            RenderUtil.disableRender();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }
        private static void renderFillBox(PoseStack stack, AABB box, boolean depth, int color1, int color2, int color3, int color4) {
            BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Matrix4f matrix = stack.last().pose();

            float minX = (float) box.minX;
            float minY = (float) box.minY;
            float minZ = (float) box.minZ;
            float maxX = (float) box.maxX;
            float maxY = (float) box.maxY;
            float maxZ = (float) box.maxZ;
            if (depth) {
                RenderSystem.disableDepthTest();
                RenderUtil.enableRender();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            }
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            buffer.addVertex(matrix, minX, minY, minZ).setColor(ColorUtil.getRed(color1) / 255F, ColorUtil.getGreen(color1) / 255F, ColorUtil.getBlue(color1) / 255F, ColorUtil.getAlpha(color1) / 255F);
            buffer.addVertex(matrix, minX, minY, maxZ).setColor(ColorUtil.getRed(color2) / 255F, ColorUtil.getGreen(color2) / 255F, ColorUtil.getBlue(color2) / 255F, ColorUtil.getAlpha(color2) / 255F);
            buffer.addVertex(matrix, maxX, minY, maxZ).setColor(ColorUtil.getRed(color3) / 255F, ColorUtil.getGreen(color3) / 255F, ColorUtil.getBlue(color3) / 255F, ColorUtil.getAlpha(color3) / 255F);
            buffer.addVertex(matrix, maxX, minY, minZ).setColor(ColorUtil.getRed(color4) / 255F, ColorUtil.getGreen(color4) / 255F, ColorUtil.getBlue(color4) / 255F, ColorUtil.getAlpha(color4) / 255F);

            buffer.addVertex(matrix, minX, maxY, minZ).setColor(ColorUtil.getRed(color1) / 255F, ColorUtil.getGreen(color1) / 255F, ColorUtil.getBlue(color1) / 255F, ColorUtil.getAlpha(color1) / 255F);
            buffer.addVertex(matrix, maxX, maxY, minZ).setColor(ColorUtil.getRed(color2) / 255F, ColorUtil.getGreen(color2) / 255F, ColorUtil.getBlue(color2) / 255F, ColorUtil.getAlpha(color2) / 255F);
            buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(ColorUtil.getRed(color3) / 255F, ColorUtil.getGreen(color3) / 255F, ColorUtil.getBlue(color3) / 255F, ColorUtil.getAlpha(color3) / 255F);
            buffer.addVertex(matrix, minX, maxY, maxZ).setColor(ColorUtil.getRed(color4) / 255F, ColorUtil.getGreen(color4) / 255F, ColorUtil.getBlue(color4) / 255F, ColorUtil.getAlpha(color4) / 255F);

            buffer.addVertex(matrix, minX, minY, minZ).setColor(ColorUtil.getRed(color1) / 255F, ColorUtil.getGreen(color1) / 255F, ColorUtil.getBlue(color1) / 255F, ColorUtil.getAlpha(color1) / 255F);
            buffer.addVertex(matrix, minX, maxY, minZ).setColor(ColorUtil.getRed(color2) / 255F, ColorUtil.getGreen(color2) / 255F, ColorUtil.getBlue(color2) / 255F, ColorUtil.getAlpha(color2) / 255F);
            buffer.addVertex(matrix, maxX, maxY, minZ).setColor(ColorUtil.getRed(color3) / 255F, ColorUtil.getGreen(color3) / 255F, ColorUtil.getBlue(color3) / 255F, ColorUtil.getAlpha(color3) / 255F);
            buffer.addVertex(matrix, maxX, minY, minZ).setColor(ColorUtil.getRed(color4) / 255F, ColorUtil.getGreen(color4) / 255F, ColorUtil.getBlue(color4) / 255F, ColorUtil.getAlpha(color4) / 255F);

            buffer.addVertex(matrix, minX, minY, maxZ).setColor(ColorUtil.getRed(color1) / 255F, ColorUtil.getGreen(color1) / 255F, ColorUtil.getBlue(color1) / 255F, ColorUtil.getAlpha(color1) / 255F);
            buffer.addVertex(matrix, maxX, minY, maxZ).setColor(ColorUtil.getRed(color2) / 255F, ColorUtil.getGreen(color2) / 255F, ColorUtil.getBlue(color2) / 255F, ColorUtil.getAlpha(color2) / 255F);
            buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(ColorUtil.getRed(color3) / 255F, ColorUtil.getGreen(color3) / 255F, ColorUtil.getBlue(color3) / 255F, ColorUtil.getAlpha(color3) / 255F);
            buffer.addVertex(matrix, minX, maxY, maxZ).setColor(ColorUtil.getRed(color4) / 255F, ColorUtil.getGreen(color4) / 255F, ColorUtil.getBlue(color4) / 255F, ColorUtil.getAlpha(color4) / 255F);

            buffer.addVertex(matrix, minX, minY, minZ).setColor(ColorUtil.getRed(color1) / 255F, ColorUtil.getGreen(color1) / 255F, ColorUtil.getBlue(color1) / 255F, ColorUtil.getAlpha(color1) / 255F);
            buffer.addVertex(matrix, minX, minY, maxZ).setColor(ColorUtil.getRed(color2) / 255F, ColorUtil.getGreen(color2) / 255F, ColorUtil.getBlue(color2) / 255F, ColorUtil.getAlpha(color2) / 255F);
            buffer.addVertex(matrix, minX, maxY, maxZ).setColor(ColorUtil.getRed(color3) / 255F, ColorUtil.getGreen(color3) / 255F, ColorUtil.getBlue(color3) / 255F, ColorUtil.getAlpha(color3) / 255F);
            buffer.addVertex(matrix, minX, maxY, minZ).setColor(ColorUtil.getRed(color4) / 255F, ColorUtil.getGreen(color4) / 255F, ColorUtil.getBlue(color4) / 255F, ColorUtil.getAlpha(color4) / 255F);

            buffer.addVertex(matrix, maxX, minY, minZ).setColor(ColorUtil.getRed(color1) / 255F, ColorUtil.getGreen(color1) / 255F, ColorUtil.getBlue(color1) / 255F, ColorUtil.getAlpha(color1) / 255F);
            buffer.addVertex(matrix, maxX, minY, maxZ).setColor(ColorUtil.getRed(color2) / 255F, ColorUtil.getGreen(color2) / 255F, ColorUtil.getBlue(color2) / 255F, ColorUtil.getAlpha(color2) / 255F);
            buffer.addVertex(matrix, maxX, maxY, maxZ).setColor(ColorUtil.getRed(color3) / 255F, ColorUtil.getGreen(color3) / 255F, ColorUtil.getBlue(color3) / 255F, ColorUtil.getAlpha(color3) / 255F);
            buffer.addVertex(matrix, maxX, maxY, minZ).setColor(ColorUtil.getRed(color4) / 255F, ColorUtil.getGreen(color4) / 255F, ColorUtil.getBlue(color4) / 255F, ColorUtil.getAlpha(color4) / 255F);
            endBuilding(buffer);
            if (depth) {
                RenderUtil.disableRender();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        }
        public static void setFilledBoxVertexes(BufferBuilder bufferBuilder, Matrix4f m, AABB box, Color c) {
            float minX = (float) (box.minX - mc.getEntityRenderDispatcher().camera.getPosition().x());
            float minY = (float) (box.minY - mc.getEntityRenderDispatcher().camera.getPosition().y());
            float minZ = (float) (box.minZ - mc.getEntityRenderDispatcher().camera.getPosition().z());
            float maxX = (float) (box.maxX - mc.getEntityRenderDispatcher().camera.getPosition().x());
            float maxY = (float) (box.maxY - mc.getEntityRenderDispatcher().camera.getPosition().y());
            float maxZ = (float) (box.maxZ - mc.getEntityRenderDispatcher().camera.getPosition().z());

            bufferBuilder.addVertex(m, minX, minY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, minY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, minY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, minX, minY, maxZ).setColor(c.getRGB());

            bufferBuilder.addVertex(m, minX, minY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, minX, maxY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, maxY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, minY, minZ).setColor(c.getRGB());

            bufferBuilder.addVertex(m, maxX, minY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, maxY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, maxY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, minY, maxZ).setColor(c.getRGB());

            bufferBuilder.addVertex(m, minX, minY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, minY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, maxY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, minX, maxY, maxZ).setColor(c.getRGB());

            bufferBuilder.addVertex(m, minX, minY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, minX, minY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, minX, maxY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, minX, maxY, minZ).setColor(c.getRGB());

            bufferBuilder.addVertex(m, minX, maxY, minZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, minX, maxY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, maxY, maxZ).setColor(c.getRGB());
            bufferBuilder.addVertex(m, maxX, maxY, minZ).setColor(c.getRGB());
        }

        public static void endBuilding(BufferBuilder bb) {
            MeshData builtBuffer = bb.build();
            if (builtBuffer != null) {
                BufferUploader.drawWithShader(builtBuffer);
            }
        }
    }
}
