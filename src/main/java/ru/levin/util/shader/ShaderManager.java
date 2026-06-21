package ru.levin.util.shader;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import ru.levin.manager.IMinecraft;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.render.RenderUtil;

import java.awt.*;

public class ShaderManager implements IMinecraft {
    /**
     * 1 цвет
     */
    public static void vertexShader(PoseStack matrixStack, float x, float y, float width, float height, int color) {
        float[] rgba = ColorUtil.rgba(color);
        Matrix4f matrix = matrixStack.last().pose();
        BufferBuilder builder = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        builder.addVertex(matrix, x, y, 0).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        builder.addVertex(matrix, x, y + height, 0).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        builder.addVertex(matrix, x + width, y + height, 0).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);
        builder.addVertex(matrix, x + width, y, 0).setColor(rgba[0], rgba[1], rgba[2], rgba[3]);

        RenderUtil.render3D.endBuilding(builder);
    }
    /**
     * 4 цвета
     */
    public static void vertexShader(PoseStack matrixStack, float x, float y, float width, float height, int color1, int color2, int color3, int color4) {
        Matrix4f matrix = matrixStack.last().pose();
        BufferBuilder builder = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        builder.addVertex(matrix, x, y,0).setColor(ColorUtil.getRed(color1) / 255F, ColorUtil.getGreen(color1) / 255F, ColorUtil.getBlue(color1) / 255F, ColorUtil.getAlpha(color1) / 255F);

        builder.addVertex(matrix, x, y + height, 0).setColor(ColorUtil.getRed(color2) / 255F, ColorUtil.getGreen(color2) / 255F, ColorUtil.getBlue(color2) / 255F, ColorUtil.getAlpha(color2) / 255F);
        builder.addVertex(matrix, x + width, y + height, 0).setColor(ColorUtil.getRed(color3) / 255F, ColorUtil.getGreen(color3) / 255F, ColorUtil.getBlue(color3) / 255F, ColorUtil.getAlpha(color3) / 255F);
        builder.addVertex(matrix, x + width, y, 0).setColor(ColorUtil.getRed(color4) / 255F, ColorUtil.getGreen(color4) / 255F, ColorUtil.getBlue(color4) / 255F, ColorUtil.getAlpha(color4) / 255F);

        RenderUtil.render3D.endBuilding(builder);
    }
    public static void vertexLine(PoseStack matrices, VertexConsumer buffer, float x1, float y1, float z1, float x2, float y2, float z2, int lineColor) {
        Matrix4f model = matrices.last().pose();
        PoseStack.Pose entry = matrices.last();
        Vector3f normalVec = getNormal(x1, y1, z1, x2, y2, z2);
        buffer.addVertex(model, x1, y1, z1).setColor(ColorUtil.getRed(lineColor) / 255F, ColorUtil.getGreen(lineColor) / 255F, ColorUtil.getBlue(lineColor) / 255F, ColorUtil.getAlpha(lineColor) / 255F).setNormal(entry, normalVec.x(), normalVec.y(), normalVec.z());
        buffer.addVertex(model, x2, y2, z2).setColor(ColorUtil.getRed(lineColor) / 255F, ColorUtil.getGreen(lineColor) / 255F, ColorUtil.getBlue(lineColor) / 255F, ColorUtil.getAlpha(lineColor) / 255F).setNormal(entry, normalVec.x(), normalVec.y(), normalVec.z());
    }
    public static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);

        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }
}
