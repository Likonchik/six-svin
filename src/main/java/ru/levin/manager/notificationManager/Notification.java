package ru.levin.manager.notificationManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import ru.levin.manager.IMinecraft;
import ru.levin.util.animations.Animation;
import ru.levin.util.animations.impl.DecelerateAnimation;
import ru.levin.util.color.ColorUtil;
import ru.levin.manager.fontManager.FontUtils;
import ru.levin.util.render.RenderUtil;

import java.awt.*;

public class Notification implements IMinecraft {
    private float x, y;

    private String name;

    private String desc;

    private NotificationType type;

    private long time = System.currentTimeMillis();

    public Animation animation = new DecelerateAnimation(500, 1, Direction.AxisDirection.POSITIVE);
    public Animation animationy = new DecelerateAnimation(500, 1, Direction.AxisDirection.POSITIVE);

    float alpha;
    int times;

    private float width;

    public Notification(NotificationType type, String name, String desc, int time) {
        this.type = type;
        this.name = name;
        this.desc = desc;
        this.times = time;
    }

    public float draw(GuiGraphics context) {
        float widthName = FontUtils.durman[13].getWidth(name);
        float widthDesc = FontUtils.durman[12].getWidth(desc);
        width = Math.max(widthName, widthDesc) + 40;

        RenderUtil.drawRoundedRect(context.pose(), x, y, width, 24, 4, ColorUtil.reAlphaInt(ColorUtil.interpolateColor(new Color(17, 15, 28, 255).getRGB(), new Color(17, 15, 28, 255).getRGB(), 0.05F), (int) (170 * alpha)));
        RenderUtil.drawRoundedRect(context.pose(), x + 22, y + 3, 1, 18, 0, ColorUtil.reAlphaInt(ColorUtil.interpolateColor(new Color(255, 255, 255, 255).getRGB(), new Color(255, 255, 255, 255).getRGB(), 0.05F), (int) (170 * alpha)));

        type.renderIcon(context.pose(), x + 3.5f, y + 4, ColorUtil.rgba(255, 255, 255, (int) (240 * alpha)));

        FontUtils.durman[13].drawLeftAligned(context.pose(), name, x + 28, y + 3, ColorUtil.rgba(255, 255, 255, (int) (240 * alpha)));
        FontUtils.durman[12].drawLeftAligned(context.pose(), desc, x + 28, y + 13, ColorUtil.rgba(210, 210, 210, (int) (240 * alpha)));

        return 24;
    }


    public float getWidth() {
        return width;
    }

    public float getX() { return x; }
    public void setX(float x) { this.x = x; }

    public float getY() { return y; }
    public void setY(float y) { this.y = y; }

    public String getName() { return name; }

    public String getDesc() { return desc; }

    public NotificationType getType() { return type; }

    public long getTime() { return time; }
}
