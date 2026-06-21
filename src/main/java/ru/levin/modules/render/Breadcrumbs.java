package ru.levin.modules.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.levin.manager.IMinecraft;
import ru.levin.modules.setting.*;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.animations.Animation;
import ru.levin.util.animations.impl.EaseBackIn;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.math.MathUtil;
import ru.levin.util.player.TimerUtil;
import ru.levin.util.render.RenderAddon;
import ru.levin.util.render.RenderUtil;

import java.util.*;

@SuppressWarnings("All")
@FunctionAnnotation(name = "Breadcrumbs", type = Type.Render, desc = "Красивые круги при движении")
public class Breadcrumbs extends Function {

    private final ResourceLocation IMAGE = ResourceLocation.fromNamespaceAndPath("exosware", "images/circles/circles5.png");

    private final List<Circle> circles = new ArrayList<>();
    private final Map<Player, TimerUtil> spawnTimers = new HashMap<>();

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            circles.removeIf(c -> c.timer.getTime() > 8000);
            Vec3 velocity = mc.player.getDeltaMovement();
            boolean isMoving = velocity.x * velocity.x + velocity.z * velocity.z > 0.001;

            TimerUtil spawnTimer = spawnTimers.computeIfAbsent(mc.player, p -> new TimerUtil());

            if (isMoving && mc.player.onGround() && spawnTimer.hasTimeElapsed(150)) {
                spawnTimer.reset();

                Vec3 spawnPos = new Vec3(mc.player.getX(), Math.floor(mc.player.getY()) + 0.001f, mc.player.getZ());
                Circle circle = new Circle(spawnPos, new TimerUtil(), new EaseBackIn(400, 1, 1.3f));
                circle.animation.setDirection(Direction.AxisDirection.POSITIVE);

                circle.yaw = getYawFromVelocity(velocity);

                circles.add(circle);
            }
        }

        if (event instanceof EventRender3D render3D) {
            render(render3D);
        }
    }

    private static float getYawFromVelocity(Vec3 velocity) {
        if (velocity.lengthSqr() < 0.0001) return 0f;
        double dx = velocity.x;
        double dz = velocity.z;
        return (float) -(Math.atan2(dx, dz) * (180 / Math.PI));
    }

    private void render(EventRender3D eventRender3D) {
        Collections.reverse(circles);
        eventRender3D.getMatrixStack().pushPose();

        RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        RenderSystem.setShaderTexture(0, IMAGE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (Circle c : circles) {
            float elapsed = (float) c.timer.getTime();
            float alphaFade = MathUtil.clamp(1f - (elapsed / 1200f), 0f, 1f);
            float animScale = (float) c.animation.getOutput();

            eventRender3D.getMatrixStack().pushPose();
            eventRender3D.getMatrixStack().translate(
                    c.pos.x - mc.getEntityRenderDispatcher().camera.getPosition().x(),
                    c.pos.y - mc.getEntityRenderDispatcher().camera.getPosition().y(),
                    c.pos.z - mc.getEntityRenderDispatcher().camera.getPosition().z()
            );

            eventRender3D.getMatrixStack().mulPose(Axis.XP.rotationDegrees(90));
            eventRender3D.getMatrixStack().mulPose(Axis.ZP.rotationDegrees(c.yaw - 180));
            RenderAddon.sizeAnimation(eventRender3D.getMatrixStack(), 0, 0, animScale * 0.45f);

            float size = 1f;
            Matrix4f matrix = eventRender3D.getMatrixStack().last().pose();

            buffer.addVertex(matrix, -size, size, 0).setUv(0, 1).setColor(RenderUtil.applyOpacity(ColorUtil.getColorStyle(270), alphaFade));
            buffer.addVertex(matrix, size - 0.3f, size, 0).setUv(1, 1).setColor(RenderUtil.applyOpacity(ColorUtil.getColorStyle(0), alphaFade));
            buffer.addVertex(matrix, size - 0.3f, -size, 0).setUv(1, 0).setColor(RenderUtil.applyOpacity(ColorUtil.getColorStyle(180), alphaFade));
            buffer.addVertex(matrix, -size, -size, 0).setUv(0, 0).setColor(RenderUtil.applyOpacity(ColorUtil.getColorStyle(90), alphaFade));

            eventRender3D.getMatrixStack().popPose();
        }

        RenderUtil.render3D.endBuilding(buffer);
        RenderSystem.depthMask(true);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        eventRender3D.getMatrixStack().popPose();
        Collections.reverse(circles);
    }

    static class Circle {
        private final Vec3 pos;
        private final TimerUtil timer;
        private final Animation animation;
        private float yaw;

        public Circle(Vec3 pos, TimerUtil timer, Animation animation) {
            this.pos = pos;
            this.timer = timer;
            this.animation = animation;
        }

        public Vec3 pos() { return pos; }
        public TimerUtil timer() { return timer; }
        public Animation animation() { return animation; }
        public float yaw() { return yaw; }
    }
}
