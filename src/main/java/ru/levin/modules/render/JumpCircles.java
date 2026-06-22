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
@FunctionAnnotation(name = "JumpCircles", type = Type.Render, desc = "Красивые круги при прыжке")
public class JumpCircles extends Function {

    private final ModeSetting circleType = new ModeSetting("Тип круга", "Type-2", "Type-1", "Type-2", "Type-3").withDesc("Текстура круга");
    private final SliderSetting rotateSpeed = new SliderSetting("Скорость", 0.1f, 0.1f, 5f, 0.1f).withDesc("Скорость вращения круга");
    private final SliderSetting circleScale = new SliderSetting("Размер", 0.7f, 0.6f, 5f, 0.1f).withDesc("Размер круга");
    private final MultiSetting targets = new MultiSetting(
            "Отображать у",
            Arrays.asList("Друзей", "Меня"),
            new String[]{"Игроков", "Друзей", "Меня"}
    ).withDesc("У кого показывать круги");

    private final ResourceLocation CIRCLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("exosware", "images/circles/circle.png");
    private final ResourceLocation CIRCLE2_TEXTURE = ResourceLocation.fromNamespaceAndPath("exosware", "images/circles/circle2.png");
    private final ResourceLocation CIRCLE3_TEXTURE = ResourceLocation.fromNamespaceAndPath("exosware", "images/circles/circle3.png");
    public JumpCircles() {
        addSettings(circleType, targets, rotateSpeed, circleScale);
    }

    private final List<Circle> circles = new ArrayList<>();
    private final Map<Player, Boolean> wasOnGround = new HashMap<>();

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            circles.removeIf(c -> c.timer.getTime() > 8000);
            for (Player player : Manager.SYNC_MANAGER.getPlayers()) {
                if (player == null || player.isRemoved()) continue;

                boolean isFriend = Manager.FRIEND_MANAGER != null && player.getName() != null && Manager.FRIEND_MANAGER.isFriend(player.getName().getString());
                boolean isMe = player == mc.player;

                boolean showPlayers = targets.get("Игроков");
                boolean showFriends = targets.get("Друзей");
                boolean showMe = targets.get("Меня");

                boolean shouldTrack = (isMe && showMe) || (!isMe && isFriend && showFriends) || (!isMe && !isFriend && showPlayers);

                if (!shouldTrack) {
                    wasOnGround.remove(player);
                    continue;
                }

                boolean previouslyOnGround = wasOnGround.getOrDefault(player, true);
                boolean currentlyOnGround = player.onGround();

                if (previouslyOnGround && !currentlyOnGround) {
                    Circle circle = new Circle(new Vec3(player.getX(), Math.floor(player.getY()) + 0.001f, player.getZ()), new TimerUtil(), new EaseBackIn(400, 1, 1.3f));
                    circle.animation.setDirection(Direction.AxisDirection.POSITIVE);
                    circles.add(circle);
                }

                wasOnGround.put(player, currentlyOnGround);
            }
        }

        if (event instanceof EventRender3D render3D) {
            renderCircles(render3D);
        }
    }

    private ResourceLocation texture = null;

    private void renderCircles(EventRender3D eventRender3D) {
        Collections.reverse(circles);
        eventRender3D.getMatrixStack().pushPose();
        RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);

        switch (circleType.get()) {
            case "Type-1" -> texture = CIRCLE_TEXTURE;
            case "Type-2" -> texture = CIRCLE2_TEXTURE;
            case "Type-3" -> texture = CIRCLE3_TEXTURE;
        }

        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (Circle c : circles) {
            float elapsed = (float) c.timer.getTime();
            float alphaFade = MathUtil.clamp(1f - (elapsed / 8000f), 0f, 1f);
            float animScale = (float) c.animation.getOutput();

            eventRender3D.getMatrixStack().pushPose();
            eventRender3D.getMatrixStack().translate(c.pos().x - mc.getEntityRenderDispatcher().camera.getPosition().x(), c.pos().y - mc.getEntityRenderDispatcher().camera.getPosition().y(), c.pos().z - mc.getEntityRenderDispatcher().camera.getPosition().z());
            eventRender3D.getMatrixStack().mulPose(Axis.XP.rotationDegrees(90));
            eventRender3D.getMatrixStack().mulPose(Axis.ZP.rotationDegrees((elapsed / 50f) * rotateSpeed.get().floatValue()));

            RenderAddon.sizeAnimation(eventRender3D.getMatrixStack(), 0, 0, animScale * circleScale.get().floatValue());

            float size = 1f;
            Matrix4f matrix = eventRender3D.getMatrixStack().last().pose();

            buffer.addVertex(matrix, -size, size, 0).setUv(0, 1).setColor(RenderUtil.applyOpacity(ColorUtil.getColorStyle(270), alphaFade));
            buffer.addVertex(matrix, size, size, 0).setUv(1, 1).setColor(RenderUtil.applyOpacity(ColorUtil.getColorStyle(0), alphaFade));
            buffer.addVertex(matrix, size, -size, 0).setUv(1, 0).setColor(RenderUtil.applyOpacity(ColorUtil.getColorStyle(180), alphaFade));
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

    record Circle(Vec3 pos, TimerUtil timer, Animation animation) {}
}
