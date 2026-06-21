package ru.levin.util.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Pair;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4i;
import org.lwjgl.opengl.GL11;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.IMinecraft;
import ru.levin.mixin.iface.LevelRendererAccessor;

import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@UtilityClass
public class Render3DUtil implements IMinecraft {
    private final Map<VoxelShape, Pair<List<AABB>, List<Line>>> SHAPE_OUTLINES = new HashMap<>();
    private final Map<VoxelShape, List<AABB>> SHAPE_BOXES = new HashMap<>();
    public final List<Texture> TEXTURE_DEPTH = new ArrayList<>();
    public final List<Texture> TEXTURE = new ArrayList<>();
    public final List<Line> LINE_DEPTH = new ArrayList<>();
    public final List<Line> LINE = new ArrayList<>();
    public final List<Quad> QUAD_DEPTH = new ArrayList<>();
    public final List<Quad> QUAD = new ArrayList<>();
    @Setter
    public PoseStack.Pose lastWorldSpaceMatrix = new PoseStack().last();
    @Setter
    public Matrix4f lastProjMat = new Matrix4f();
    public void onWorldRender(EventRender3D e) {
        if (!TEXTURE.isEmpty()) {
            Set<ResourceLocation> identifiers = TEXTURE.stream().map(texture -> texture.id).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            identifiers.forEach(id -> {
                RenderSystem.setShaderTexture(0, id);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                TEXTURE.stream().filter(texture -> texture.id.equals(id)).forEach(tex -> quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color));
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            });
            RenderSystem.disableBlend();
            TEXTURE.clear();
        }
        if (!TEXTURE_DEPTH.isEmpty()) {
            Set<ResourceLocation> identifiers = TEXTURE_DEPTH.stream().map(texture -> texture.id).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            identifiers.forEach(id -> {
                RenderSystem.setShaderTexture(0, id);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                TEXTURE_DEPTH.stream().filter(texture -> texture.id.equals(id)).forEach(tex -> quadTexture(tex.entry, buffer, tex.x, tex.y, tex.width, tex.height, tex.color));
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            });
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            TEXTURE_DEPTH.clear();
        }
        if (!LINE.isEmpty()) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            Set<Float> widths = LINE.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            widths.forEach(width -> {
                RenderSystem.lineWidth(width);
                BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                LINE.stream().filter(line -> line.width == width).forEach(line -> vertexLine(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd));
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            });
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            LINE.clear();
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }
        if (!QUAD.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            QUAD.forEach(quad -> vertexQuad(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.color));
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            QUAD.clear();
        }
        if (!LINE_DEPTH.isEmpty()) {
            GL11.glEnable(GL11.GL_POLYGON_SMOOTH);
            Set<Float> widths = LINE_DEPTH.stream().map(line -> line.width).collect(Collectors.toCollection(LinkedHashSet::new));
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
            widths.forEach(width -> {
                RenderSystem.lineWidth(width);
                BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                LINE_DEPTH.stream().filter(line -> line.width == width).forEach(line -> vertexLine(line.entry, buffer, line.start.toVector3f(), line.end.toVector3f(), line.colorStart, line.colorEnd));
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            });
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            LINE_DEPTH.clear();
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
        }
        if (!QUAD_DEPTH.isEmpty()) {
            RenderSystem.enableBlend();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_CONSTANT_ALPHA);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            QUAD_DEPTH.forEach(quad -> vertexQuad(quad.entry, buffer, quad.x, quad.y, quad.w, quad.z, quad.color));
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            QUAD_DEPTH.clear();
        }
    }

    public void endBuilding(BufferBuilder bb) {
        MeshData mesh = bb.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width) {
        drawShape(blockPos, voxelShape, color, width, true, false);
    }
    public boolean canSee(AABB box) {
        Frustum frustum = ((LevelRendererAccessor) mc.levelRenderer).getCapturedFrustum();
        return box != null && frustum != null && frustum.isVisible(box);
    }

    public void drawShape(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        if (SHAPE_BOXES.containsKey(voxelShape)) {
            SHAPE_BOXES.get(voxelShape).forEach(box -> {
                box = box.move(blockPos);
                if (canSee(box)) drawBox(box, color, width, true, fill, depth);
            });
            return;
        }
        SHAPE_BOXES.put(voxelShape, voxelShape.toAabbs());
    }

    public void drawShapeAlternative(BlockPos blockPos, VoxelShape voxelShape, int color, float width, boolean fill, boolean depth) {
        Vec3 vec3d = Vec3.atLowerCornerOf(blockPos);

        if (SHAPE_OUTLINES.containsKey(voxelShape)) {
            Pair<List<AABB>, List<Line>> pair = SHAPE_OUTLINES.get(voxelShape);
            if (fill) pair.getFirst().forEach(box -> drawBox(box.move(vec3d), color, width, false, true, depth));
            pair.getSecond().forEach(line -> drawLine(line.start.add(vec3d), line.end.add(vec3d), color, width, depth));
            return;
        }
        List<Line> lines = new ArrayList<>();
        voxelShape.forAllEdges((minX, minY, minZ, maxX, maxY, maxZ) -> lines.add(new Line(null, new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ), 0, 0, 0)));
        SHAPE_OUTLINES.put(voxelShape, new Pair<>(voxelShape.toAabbs(), lines));

    }

    public void drawBox(AABB box, int color, float width) {
        drawBox(box, color, width, true, true, false);
    }

    public void drawBox(AABB box, int color, float width, boolean line, boolean fill, boolean depth) {
        drawBox(null, box, color, width, line, fill, depth) ;
    }

    public void drawBox(PoseStack.Pose entry, AABB box, int color, float width, boolean line, boolean fill, boolean depth) {
        box = box.inflate(1e-3);

        double x1 = box.minX;
        double y1 = box.minY;
        double z1 = box.minZ;
        double x2 = box.maxX;
        double y2 = box.maxY;
        double z2 = box.maxZ;

        if (fill) {
            int fillColor = ColorUtilTest.multAlpha(color, 0.1f);
            drawQuad(entry, new Vec3(x1, y1, z1), new Vec3(x2, y1, z1), new Vec3(x2, y1, z2), new Vec3(x1, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y1, z1), new Vec3(x1, y2, z1), new Vec3(x2, y2, z1), new Vec3(x2, y1, z1), fillColor, depth);
            drawQuad(entry, new Vec3(x2, y1, z1), new Vec3(x2, y2, z1), new Vec3(x2, y2, z2), new Vec3(x2, y1, z2), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y1, z2), new Vec3(x2, y1, z2), new Vec3(x2, y2, z2), new Vec3(x1, y2, z2), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y1, z1), new Vec3(x1, y1, z2), new Vec3(x1, y2, z2), new Vec3(x1, y2, z1), fillColor, depth);
            drawQuad(entry, new Vec3(x1, y2, z1), new Vec3(x1, y2, z2), new Vec3(x2, y2, z2), new Vec3(x2, y2, z1), fillColor, depth);
        }

        if (line) {
            drawLine(entry, x1, y1, z1, x2, y1, z1, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y1, z2, color, width, depth);
            drawLine(entry, x2, y1, z2, x1, y1, z2, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y1, z1, color, width, depth);
            drawLine(entry, x1, y1, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y1, z1, x1, y2, z1, color, width, depth);
            drawLine(entry, x2, y1, z2, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y1, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x1, y2, z1, x2, y2, z1, color, width, depth);
            drawLine(entry, x2, y2, z1, x2, y2, z2, color, width, depth);
            drawLine(entry, x2, y2, z2, x1, y2, z2, color, width, depth);
            drawLine(entry, x1, y2, z2, x1, y2, z1, color, width, depth);
        }
    }

    public void vertexLine(PoseStack matrices, VertexConsumer buffer, Vec3 start, Vec3 end, int startColor, int endColor) {
        vertexLine(matrices.last(), buffer, start.toVector3f(), end.toVector3f(), startColor, endColor);
    }
    public void vertexLine(PoseStack.Pose entry, VertexConsumer buffer, Vector3f start, Vector3f end, int startColor, int endColor) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        Vector3f vec = getNormal(start, end);
        buffer.addVertex(entry, start).setColor(startColor).setNormal(entry, vec.x(), vec.y(), vec.z());
        buffer.addVertex(entry, end).setColor(endColor).setNormal(entry, vec.x(), vec.y(), vec.z());
    }
    public void vertexQuad(PoseStack.Pose entry, VertexConsumer buffer, Vec3 vec1, Vec3 vec2, Vec3 vec3, Vec3 vec4, int color) {
        vertexQuad(entry, buffer, vec1.toVector3f(), vec2.toVector3f(), vec3.toVector3f(), vec4.toVector3f(), color);
    }
    public void vertexQuad(PoseStack.Pose entry, VertexConsumer buffer, Vector3f vec1, Vector3f vec2, Vector3f vec3, Vector3f vec4, int color) {
        if (entry == null) entry = lastWorldSpaceMatrix;
        buffer.addVertex(entry, vec1).setColor(color);
        buffer.addVertex(entry, vec2).setColor(color);
        buffer.addVertex(entry, vec3).setColor(color);
        buffer.addVertex(entry, vec4).setColor(color);
    }
    public void quadTexture(PoseStack.Pose entry, BufferBuilder buffer, float x, float y, float width, float height, Vector4i color) {
        buffer.addVertex(entry, x, y + height, 0).setUv(0, 0).setColor(color.x);
        buffer.addVertex(entry, x + width, y + height, 0).setUv(0, 1).setColor(color.y);
        buffer.addVertex(entry, x + width, y, 0).setUv(1, 1).setColor(color.w);
        buffer.addVertex(entry, x, y, 0).setUv(1, 0).setColor(color.z);
    }
    public @NotNull Vector3f getNormal(Vector3f start, Vector3f end) {
        Vector3f normal = new Vector3f(start).sub(end);
        float sqrt = Mth.sqrt(normal.lengthSquared());
        return normal.div(sqrt);
    }

    public void drawLine(PoseStack.Pose entry, double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color, float width, boolean depth) {
        drawLine(entry, new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ), color, color, width, depth);
    }

    public void drawLine(Vec3 start, Vec3 end, int color, float width, boolean depth) {
        drawLine(null, start, end, color, color, width, depth);
    }

    public void drawLine(PoseStack.Pose entry, Vec3 start, Vec3 end, int colorStart, int colorEnd, float width, boolean depth) {
        Line line = new Line(entry, start, end, colorStart, colorEnd, width);
        if (depth) LINE_DEPTH.add(line); else LINE.add(line);
    }

    public void drawQuad(Vec3 x, Vec3 y, Vec3 w, Vec3 z, int color, boolean depth) {
        drawQuad(null,x,y,w,z,color,depth);
    }

    public void drawQuad(PoseStack.Pose entry, Vec3 x, Vec3 y, Vec3 w, Vec3 z, int color, boolean depth) {
        Quad quad = new Quad(entry, x, y, w, z, color);
        if (depth) QUAD_DEPTH.add(quad); else QUAD.add(quad);
    }

    public void drawTexture(PoseStack.Pose entry, ResourceLocation id, float x, float y, float width, float height, Vector4i color, boolean depth) {
        Texture texture = new Texture(entry, id, x, y, width, height, color);
        if (depth) TEXTURE_DEPTH.add(texture); else TEXTURE.add(texture);
    }

    public record Texture(PoseStack.Pose entry, ResourceLocation id, float x, float y, float width, float height, Vector4i color) {}
    public record Line(PoseStack.Pose entry, Vec3 start, Vec3 end, int colorStart, int colorEnd, float width) {}
    public record Quad(PoseStack.Pose entry, Vec3 x, Vec3 y, Vec3 w, Vec3 z, int color) {}
}
