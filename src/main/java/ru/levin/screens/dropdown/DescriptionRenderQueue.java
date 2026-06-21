package ru.levin.screens.dropdown;

import net.minecraft.client.gui.GuiGraphics;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.render.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class DescriptionRenderQueue {

    private static final List<QueuedDescription> DESCRIPTIONS = new ArrayList<>();

    public static void add(String text, float x, float y) {
        if (text == null || text.isEmpty()) return;
        DESCRIPTIONS.add(new QueuedDescription(text, x, y));
    }

    public static void renderAll(GuiGraphics context) {
        for (QueuedDescription desc : DESCRIPTIONS) {
            final float width = FontUtils.durman[14].getWidth(desc.text) + 8;
            final float height = 12;

            RenderUtil.drawRoundedRect(context.pose(), desc.x, desc.y, width, height, 2f, new Color(0, 0, 0, 255).getRGB());
            FontUtils.durman[14].drawLeftAligned(context.pose(), desc.text, desc.x + 4, desc.y + 1.5f, Color.WHITE.getRGB());
        }
        DESCRIPTIONS.clear();
    }

    private static class QueuedDescription {
        final String text;
        final float x, y;

        QueuedDescription(String text, float x, float y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}
