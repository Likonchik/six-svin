package ru.levin.mixin.display;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.modules.render.ESP;

// Chams — ЗАЛИВКА силуэта модели цветом (не хитбокс-коробка). Корень прошлого бага: OutlineBufferSource
// рендерит в offscreen outline-таргет (оттуда пост-шейдер выводит только края). Поэтому здесь перерисовываем
// модель ЕЩЁ РАЗ в ТОТ ЖЕ (главный) буфер, оборачивая VertexConsumer так, чтобы у каждой вершины был наш
// flat-цвет (модельный setColor игнорируется). Результат: модель залита нашим цветом поверх текстуры.
// Guard от рекурсии; пропускаем ванильный outline-проход. Цель/цвет — ESP.getFillColor.
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Unique private static final ThreadLocal<Boolean> onetap$inChams = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "render", at = @At("TAIL"))
    private <E extends Entity> void onetap$chams(E entity, double x, double y, double z, float rotationYaw,
                                                 float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                                                 int packedLight, CallbackInfo ci) {
        if (onetap$inChams.get()) return;
        if (buffer instanceof OutlineBufferSource) return;            // не во время ванильного outline-прохода
        int color = ESP.getFillColor(entity);
        if (color == 0) return;
        onetap$inChams.set(Boolean.TRUE);
        try {
            ((EntityRenderDispatcher) (Object) this).render(
                    entity, x, y, z, rotationYaw, partialTicks, poseStack, new FlatColorSource(buffer, color), packedLight);
        } catch (Throwable ignored) {
        } finally {
            onetap$inChams.set(Boolean.FALSE);
        }
    }

    // оборачивает каждый VertexConsumer модели, форся flat-цвет
    @Unique
    private static final class FlatColorSource implements MultiBufferSource {
        private final MultiBufferSource delegate;
        private final int argb;
        FlatColorSource(MultiBufferSource delegate, int argb) { this.delegate = delegate; this.argb = argb; }
        @Override public VertexConsumer getBuffer(RenderType type) {
            return new FlatColorConsumer(delegate.getBuffer(type), argb);
        }
    }

    // ставит наш цвет сразу после каждой вершины; модельный setColor игнорирует; остальное делегирует
    @Unique
    private static final class FlatColorConsumer implements VertexConsumer {
        private final VertexConsumer d;
        private final int r, g, b, a;
        FlatColorConsumer(VertexConsumer d, int argb) {
            this.d = d;
            this.a = (argb >>> 24) & 0xFF;
            this.r = (argb >> 16) & 0xFF;
            this.g = (argb >> 8) & 0xFF;
            this.b = argb & 0xFF;
        }
        @Override public VertexConsumer addVertex(float vx, float vy, float vz) {
            d.addVertex(vx, vy, vz).setColor(r, g, b, a);
            return this;
        }
        @Override public VertexConsumer setColor(int cr, int cg, int cb, int ca) { return this; } // игнор модельного цвета
        @Override public VertexConsumer setUv(float u, float v) { d.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { d.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { d.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float nx, float ny, float nz) { d.setNormal(nx, ny, nz); return this; }
    }
}
