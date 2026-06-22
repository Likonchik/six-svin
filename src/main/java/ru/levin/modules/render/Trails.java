package ru.levin.modules.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import ru.levin.manager.IMinecraft;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.IEntity;
import ru.levin.util.render.RenderUtil;

import java.awt.*;
import java.util.Arrays;
import java.util.List;

@FunctionAnnotation(name = "Trails", type = Type.Render, desc = "Красивая линия за вами")
public class Trails extends Function {

    private final MultiSetting targets = new MultiSetting("Отображать у",
            Arrays.asList("Друзей", "Меня"),
            new String[]{"Игроков", "Друзей", "Меня"}).withDesc("Кому рисовать след");

    private final long trailLifetimeMs = 250L;
    private final double minDistance = 0.01;

    public Trails() {
        addSettings(targets);
    }

    @Override
    public void onEvent(Event event) {
        long now = System.currentTimeMillis();
        if (event instanceof EventUpdate) {
            for (Player entity : Manager.SYNC_MANAGER.getPlayers()) {
                if (!shouldRenderTrails(entity)) continue;
                List<Trail> trails = ((IEntity) entity).exosWareFabric1_21_4$getTrails();
                trails.removeIf(t -> t.isExpired(now));
            }
            return;
        }

        if (event instanceof EventRender3D renderEvent) {
            float tickDelta = renderEvent.getDeltatick().getGameTimeDeltaPartialTick(true);
            Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
            for (Player entity : Manager.SYNC_MANAGER.getPlayers()) {
                if (!shouldRenderTrails(entity)) continue;
                Vec3 interp = interpolateEntityPosition(entity, tickDelta);
                List<Trail> trails = ((IEntity) entity).exosWareFabric1_21_4$getTrails();
                if (trails.isEmpty()) {
                    trails.add(new Trail(interp, getTrailColor(entity), now));
                } else {
                    Trail last = trails.get(trails.size() - 1);
                    if (last.pos.distanceTo(interp) >= minDistance) {
                        trails.add(new Trail(interp, getTrailColor(entity), now));
                    }
                }
                render(renderEvent, entity, cameraPos, now);
            }
        }
    }

    private int getTrailColor(Player entity) {
        if (Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) {
            return new Color(0, 255, 0).getRGB();
        }
        return ColorUtil.getColorStyle(360);
    }

    private boolean shouldRenderTrails(Player entity) {
        if (entity == mc.player) {
            if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                return false;
            }
            return targets.get("Меня");
        }
        if (targets.get("Друзей") && Manager.FRIEND_MANAGER.isFriend(entity.getName().getString())) {
            return true;
        }
        return targets.get("Игроков");
    }

    private Vec3 interpolateEntityPosition(Player entity, float tickDelta) {
        double ix = entity.xo + (entity.getX() - entity.xo) * tickDelta;
        double iy = entity.yo + (entity.getY() - entity.yo) * tickDelta;
        double iz = entity.zo + (entity.getZ() - entity.zo) * tickDelta;
        return new Vec3(ix, iy, iz);
    }

    private void render(EventRender3D event, Player entity, Vec3 cameraPos, long now) {
        List<Trail> trails = ((IEntity) entity).exosWareFabric1_21_4$getTrails();
        if (trails.isEmpty()) return;

        float playerHeight = entity.getBbHeight();
        event.getMatrixStack().pushPose();
        RenderSystem.disableCull();
        RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = IMinecraft.tessellator().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        for (Trail p : trails) {
            if (p.isExpired(now)) continue;
            float ageFrac = (float) (now - p.time) / (float) trailLifetimeMs;
            float alpha = 1f - Math.min(1f, ageFrac);
            alpha = Math.max(0.01f, alpha);
            int color = RenderUtil.injectAlpha(p.color, (int) (alpha * 255));
            Vec3 posRel = p.pos.subtract(cameraPos);

            buffer.addVertex(event.getMatrixStack().last().pose(), (float) posRel.x, (float) (posRel.y + playerHeight), (float) posRel.z).setColor(color);
            buffer.addVertex(event.getMatrixStack().last().pose(), (float) posRel.x, (float) posRel.y, (float) posRel.z).setColor(color);
        }

        RenderUtil.render3D.endBuilding(buffer);
        RenderUtil.disableRender();
        RenderSystem.disableDepthTest();
        event.getMatrixStack().popPose();
    }

    public class Trail {
        public final Vec3 pos;
        public final int color;
        public final long time;

        public Trail(Vec3 pos, int color, long time) {
            this.pos = pos;
            this.color = color;
            this.time = time;
        }

        public boolean isExpired(long now) {
            return (now - time) > trailLifetimeMs;
        }
    }
}
