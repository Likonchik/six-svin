package ru.levin.manager.notificationManager;

import com.mojang.blaze3d.vertex.PoseStack;
import ru.levin.util.render.RenderUtil;

import java.awt.*;

public enum NotificationType {
    INFO("info.png"),
    SUCCESS("add.png"),
    REMOVED("remove.png");

    private final String texture;

    NotificationType(String texture) {
        this.texture = texture;
    }

    public int renderIcon(PoseStack matrixStack, float x, float y, int color) {
        RenderUtil.drawTexture(matrixStack,
                "images/state/" + texture,
                x, y,
                16, 16,
                8,
                color
        );
        return 0;
    }

    public String getTexture() {
        return texture;
    }
}
