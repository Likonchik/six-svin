package ru.levin.mixin.display;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.levin.manager.Manager;

@Mixin(LevelRenderer.class)
public abstract class MixinWorldRenderer {

    // 1.21.1: renderMain(FrameGraphBuilder, ...) does not exist. The block-outline branch lives in
    // LevelRenderer.renderLevel(DeltaTracker, boolean renderBlockOutline, ...) gated on the boolean arg.
    // We force that flag to false while the BlockHighLight module is active to suppress the vanilla hit outline.
    @ModifyVariable(method = "renderLevel", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private boolean onRenderLevel(boolean renderBlockOutline) {
        return renderBlockOutline && !Manager.FUNCTION_MANAGER.blockHighLight.isState();
    }
}
