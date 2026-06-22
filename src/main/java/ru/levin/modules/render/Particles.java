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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.player.EventAttack;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.manager.IMinecraft;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.math.MathUtil;
import ru.levin.util.render.RenderUtil;
import ru.levin.util.render.providers.ResourceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@FunctionAnnotation(name = "Particles",desc  = "Красивые картинки в воздухе", type = Type.Render)
public class Particles extends Function {
    private final MultiSetting types = new MultiSetting(
            "Типы",
            Arrays.asList("Урон", "Мир"),
            new String[]{"Урон", "Мир"}
    ).withDesc("Где показывать частицы");

    private final ModeSetting mode = new ModeSetting("Текстура", "Звезда",
            "Корона",
            "Доллар",
            "Светлячок",
            "Сердце",
            "Молния",
            "Линия",
            "Точка",
            "Ромб",
            "Снежинка",
            "Искра",
            "Звезда"
    ).withDesc("Текстура частиц");
    private final SliderSetting countDamage = new SliderSetting("Количество при уроне", 20f, 5f, 50f, 1f,() -> types.get("Урон")).withDesc("Число частиц при ударе");
    private final SliderSetting countWorld = new SliderSetting("Количество в мире", 12f, 2f, 15f, 1f,() -> types.get("Мир")).withDesc("Число частиц в мире");
    private final SliderSetting sizeDamage = new SliderSetting("Размер при уроне", 0.3f, 0.1f, 0.6f, 0.1f,() -> types.get("Урон")).withDesc("Размер частиц при ударе");
    private final SliderSetting sizeWorld = new SliderSetting("Размер в мире", 1.1f, 0.1f, 1.2f, 0.1f,() -> types.get("Мир")).withDesc("Размер частиц в мире");
    private final SliderSetting sila = new SliderSetting("Сила разброса", 0.2f, 0.1f, 0.5f, 0.1f).withDesc("Сила разлёта частиц");
    private final SliderSetting time = new SliderSetting("Время жизни", 4000f, 500f, 8000f, 100f).withDesc("Время жизни частиц");
    private final SliderSetting speedMultiplier = new SliderSetting("Скорость", 1.2f, 0.1f, 3f, 0.1f).withDesc("Скорость движения частиц");
    private final BooleanSetting randomRotation = new BooleanSetting("Рандомный поворот", true, "Случайный поворот частиц");


    private final ArrayList<World> worldParticles = new ArrayList<>();
    private final ArrayList<Damage> damageParticles = new ArrayList<>();

    public Particles() {
        addSettings(types,mode,countDamage,countWorld,sizeDamage,sizeWorld,sila,time,speedMultiplier,randomRotation);
    }
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    static {
        TEXTURES.put("Корона", ResourceProvider.crown);
        TEXTURES.put("Доллар", ResourceProvider.dollar);
        TEXTURES.put("Светлячок", ResourceProvider.firefly);
        TEXTURES.put("Сердце", ResourceProvider.heart);
        TEXTURES.put("Молния", ResourceProvider.lightning);
        TEXTURES.put("Линия", ResourceProvider.line);
        TEXTURES.put("Точка", ResourceProvider.point);
        TEXTURES.put("Ромб", ResourceProvider.rhombus);
        TEXTURES.put("Снежинка", ResourceProvider.snowflake);
        TEXTURES.put("Искра", ResourceProvider.spark);
        TEXTURES.put("Звезда", ResourceProvider.star);

    }
    @Override
    public void onEvent(Event event) {
        if (types.get("Мир")) {
            if (event instanceof EventUpdate) {
                worldParticles.removeIf(World::tick);
                float con = countWorld.get().floatValue() * 100f;
                for (int j = worldParticles.size(); j < con; j++) {
                    boolean drop = false;
                    worldParticles.add(new World((float) (mc.player.getX() + MathUtil.random(-48f, 48f)), (float) (mc.player.getY() + MathUtil.random(2, 48f)), (float) (mc.player.getZ() + MathUtil.random(-48f, 48f)), drop ? 0 : MathUtil.random(-0.4f, 0.4f), drop ? MathUtil.random(-0.2f, -0.05f) : MathUtil.random(-0.1f, 0.1f), drop ? 0 : MathUtil.random(-0.4f, 0.4f)));
                }
            }
            if (event instanceof EventRender3D e) {
                e.getMatrixStack().pushPose();
                RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                BufferBuilder bufferBuilder = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                worldParticles.forEach(p -> p.render(bufferBuilder));
                RenderUtil.render3D.endBuilding(bufferBuilder);
                RenderSystem.depthMask(true);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.disableDepthTest();
                RenderSystem.disableBlend();
                e.getMatrixStack().popPose();
            }
        }
        if (types.get("Урон")) {
            if (event instanceof EventUpdate) {
                damageParticles.removeIf(Damage::tick);
            } else if (event instanceof EventAttack eventAttack) {
                Entity target = eventAttack.getTarget();
                if (target == null) return;

                double x = target.getX();
                double y = target.getY() + 0.5f;
                double z = target.getZ();
                float con = countDamage.get().floatValue();
                for (int i = 0; i < con; i++) {
                    float motionX = MathUtil.random(-sila.get().floatValue(), sila.get().floatValue()) * speedMultiplier.get().floatValue();
                    float motionZ = MathUtil.random(-sila.get().floatValue(), sila.get().floatValue()) * speedMultiplier.get().floatValue();
                    float motionY = MathUtil.random(-0.05f, -0.1f);
                    motionY *= speedMultiplier.get().floatValue();
                    float rotation = randomRotation.get() ? MathUtil.random(0f, 360f) : 0f;
                    int color = ColorUtil.getColorStyle((int) (Math.random() * 360));
                    damageParticles.add(new Damage((float) x, (float) y, (float) z, motionX, motionY, motionZ, time.get().longValue(), color, rotation));
                }
            } else if (event instanceof EventRender3D e) {
                PoseStack matrixStack = e.getMatrixStack();
                matrixStack.pushPose();
                RenderUtil.enableRender(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                BufferBuilder bufferBuilder = IMinecraft.tessellator().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                ResourceLocation tex = TEXTURES.getOrDefault(mode.get(), TEXTURES.get("Звезда"));
                RenderSystem.setShaderTexture(0, tex);
                damageParticles.forEach(p -> p.render(bufferBuilder));
                RenderUtil.render3D.endBuilding(bufferBuilder);
                RenderSystem.depthMask(true);
                RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                RenderSystem.disableDepthTest();
                RenderSystem.disableBlend();
                matrixStack.popPose();
            }
        }
    }

    public class Damage {
        private float prevposX, prevposY, prevposZ, posX, posY, posZ, motionX, motionY, motionZ;
        private final long createdTime;
        private final long maxAge;
        private int currentColor;
        private final float rotation;

        public Damage(float posX, float posY, float posZ, float motionX, float motionY, float motionZ, long maxAge, int color, float rotation) {
            this.posX = this.prevposX = posX;
            this.posY = this.prevposY = posY;
            this.posZ = this.prevposZ = posZ;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.createdTime = System.currentTimeMillis();
            this.maxAge = maxAge;
            this.currentColor = color;
            this.rotation = rotation;
        }

        public boolean tick() {
            long now = System.currentTimeMillis();
            if (now - createdTime > maxAge) return true;

            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;

            for (int i = 0; i < 4; i++) {
                tryMove(motionX / 4, motionY / 4, motionZ / 4);
            }
            motionX *= 0.97f;
            motionY *= 0.97f;
            motionZ *= 0.97f;

            return false;
        }

        private boolean isSolid(float x, float y, float z) {
            BlockPos pos = new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
            return mc.level != null && mc.level.getBlockState(pos).isCollisionShapeFullBlock(mc.level, pos);
        }

        private void tryMove(float dx, float dy, float dz) {
            float newX = posX + dx;
            float newY = posY + dy;
            float newZ = posZ + dz;

            if (!isSolid(newX, posY, posZ)) posX = newX;
            else motionX *= -0.4f;

            if (!isSolid(posX, newY, posZ)) posY = newY;
            else motionY *= -0.4f;

            if (!isSolid(posX, posY, newZ)) posZ = newZ;
            else motionZ *= -0.4f;
        }

        public void render(BufferBuilder bufferBuilder) {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 pos = RenderUtil.interpolatePos(prevposX, prevposY, prevposZ, posX, posY, posZ);

            PoseStack matrices = new PoseStack();
            matrices.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            matrices.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
            matrices.translate(pos.x, pos.y, pos.z);
            matrices.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            matrices.mulPose(Axis.ZP.rotation(rotation));
            float alpha = Math.max(0, 1f - (System.currentTimeMillis() - createdTime) / (float) maxAge);
            int colorWithAlpha = RenderUtil.injectAlpha(currentColor, (int) (255 * alpha));

            Matrix4f matrix = matrices.last().pose();
            float s = sizeDamage.get().floatValue();
            bufferBuilder.addVertex(matrix, 0, -s, 0).setUv(0f, 1f).setColor(colorWithAlpha);
            bufferBuilder.addVertex(matrix, -s, -s, 0).setUv(1f, 1f).setColor(colorWithAlpha);
            bufferBuilder.addVertex(matrix, -s, 0, 0).setUv(1f, 0).setColor(colorWithAlpha);
            bufferBuilder.addVertex(matrix, 0, 0, 0).setUv(0, 0).setColor(colorWithAlpha);
        }
    }

    public class World {
        protected float prevposX, prevposY, prevposZ, posX, posY, posZ, motionX, motionY, motionZ;
        protected int age, maxAge;
        public World(float posX, float posY, float posZ, float motionX, float motionY, float motionZ) {
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            age = (int) MathUtil.random(100, 300);
            maxAge = age;
        }
        public boolean tick() {
            if (mc.player.distanceToSqr(posX, posY, posZ) > 4096) age -= 8;
            else age--;

            if (age < 0)
                return true;

            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;

            posX += motionX;
            posY += motionY;
            posZ += motionZ;

            motionX *= 0.9f;
            motionY *= 0.9f;
            motionZ *= 0.9f;

            motionY -= 0.001f;

            return false;
        }
        public void render(BufferBuilder bufferBuilder) {
            ResourceLocation tex = TEXTURES.getOrDefault(mode.get(), TEXTURES.get("Звезда"));
            RenderSystem.setShaderTexture(0, tex);

            Camera camera = mc.gameRenderer.getMainCamera();
            int color1 = ColorUtil.getColorStyle(age * 2);
            Vec3 pos = RenderUtil.interpolatePos(prevposX, prevposY, prevposZ, posX, posY, posZ);

            PoseStack matrices = new PoseStack();
            matrices.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            matrices.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
            matrices.translate(pos.x, pos.y, pos.z);
            matrices.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));

            Matrix4f matrix1 = matrices.last().pose();
            float size = sizeWorld.get().floatValue();
            bufferBuilder.addVertex(matrix1, (float) 0, -size, 0).setUv(0f, 1f).setColor(RenderUtil.injectAlpha(color1, (int) (255 * ((float) age / (float) maxAge))));
            bufferBuilder.addVertex(matrix1, -size, -size, 0).setUv(1f, 1f).setColor(RenderUtil.injectAlpha(color1, (int) (255 * ((float) age / (float) maxAge))));
            bufferBuilder.addVertex(matrix1, -size, 0, 0).setUv(1f, 0).setColor(RenderUtil.injectAlpha(color1, (int) (255 * ((float) age / (float) maxAge))));
            bufferBuilder.addVertex(matrix1, 0, 0, 0).setUv(0, 0).setColor(RenderUtil.injectAlpha(color1, (int) (255 * ((float) age / (float) maxAge))));
        }
    }
}
