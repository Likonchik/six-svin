package ru.levin.events.impl.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import ru.levin.events.Event;

@SuppressWarnings("All")
public class EventRender2D extends Event {
    private GuiGraphics drawContext;
    private PoseStack matrixStack;
    private DeltaTracker deltatick;

    public EventRender2D(GuiGraphics drawContext, PoseStack matrixStack, DeltaTracker deltatick) {
        this.drawContext = drawContext;
        this.matrixStack = matrixStack;
        this.deltatick = deltatick;
    }

    public PoseStack getMatrixStack() {
        return matrixStack;
    }

    public GuiGraphics getDrawContext() {
        return drawContext;
    }

    public DeltaTracker getDeltatick() {
        return deltatick;
    }
}
