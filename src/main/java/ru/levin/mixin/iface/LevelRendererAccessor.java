package ru.levin.mixin.iface;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    // 1.21.1: LevelRenderer has no public getCapturedFrustum() (the Yarn name). Expose the private field.
    @Accessor("capturedFrustum")
    Frustum getCapturedFrustum();
}
