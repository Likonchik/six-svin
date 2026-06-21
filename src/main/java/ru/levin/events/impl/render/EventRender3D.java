package ru.levin.events.impl.render;

import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import ru.levin.events.Event;

@SuppressWarnings("All")
public class EventRender3D extends Event {
    private DeltaTracker deltatick;
    private PoseStack matrixStack;

    public EventRender3D(PoseStack matrixStack, DeltaTracker deltatick) {
        this.matrixStack = matrixStack;
        this.deltatick = deltatick;
    }

    public PoseStack getMatrixStack() {
        return matrixStack;
    }
    public DeltaTracker getDeltatick() {
        return deltatick;
    }
}
