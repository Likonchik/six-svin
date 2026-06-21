package ru.levin.mixin.display;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.util.player.TimerUtil;

@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreen<T extends AbstractContainerMenu> extends Screen implements MenuAccess<T>,IMinecraft{

    @Unique
    private final TimerUtil timerUtil = new TimerUtil();

    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    protected MixinHandledScreen(Component title) {
        super(title);
    }

    @Shadow
    protected abstract void slotClicked(Slot slot, int slotId, int button, ClickType actionType);

    @Inject(method = "renderTooltip", at = @At("HEAD"))
    private void onDrawMouseoverTooltip(GuiGraphics context, int x, int y, CallbackInfo ci) {
        if (this.hoveredSlot == null || !this.hoveredSlot.hasItem()) return;

        long windowHandle = mc.getWindow().getWindow();

        boolean leftMousePressed = GLFW.glfwGetMouseButton(windowHandle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean shiftPressed = InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(windowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (Manager.FUNCTION_MANAGER.itemScroller != null && Manager.FUNCTION_MANAGER.itemScroller.state && leftMousePressed && shiftPressed && mc.screen != null) {
            if (timerUtil.hasTimeElapsed(Manager.FUNCTION_MANAGER.itemScroller.scroll.get().longValue()) && this.hoveredSlot.hasItem()) {
                this.slotClicked(this.hoveredSlot, this.hoveredSlot.index, 0, ClickType.QUICK_MOVE);
                timerUtil.reset();
            }
        }
    }
}