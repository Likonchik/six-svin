package ru.levin.mixin.client;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.ClientManager;

@Mixin(DeltaTracker.Timer.class)
public class RenderTickCounterDynamicAccessor {
    @Shadow
    private float deltaTicks;
    @Shadow
    private float deltaTickResidual;
    @Shadow
    private long lastMs;
    @Final
    @Shadow
    private float msPerTick;
    @Final
    @Shadow
    private FloatUnaryOperator targetMsptProvider;

    @Inject(method = "advanceTime(JZ)I", at = @At("HEAD"), cancellable = true)
    private void advanceTimeHook(long timeMillis, boolean runsNormally, CallbackInfoReturnable<Integer> cir) {
        if (ClientManager.TICK_TIMER == 1)
            return;
        if (!runsNormally)
            return;

        this.deltaTicks = ((timeMillis - this.lastMs) / this.targetMsptProvider.apply(this.msPerTick)) * ClientManager.TICK_TIMER;
        this.lastMs = timeMillis;
        this.deltaTickResidual += this.deltaTicks;
        int i = (int) this.deltaTickResidual;
        this.deltaTickResidual -= i;
        cir.setReturnValue(i);
    }
}
