package ru.levin.screens.unhook;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import ru.levin.manager.ClientManager;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.misc.UnHook;

public class UnHookScreen extends Screen implements IMinecraft {
    private final UnHook unHookFunction;

    public UnHookScreen() {
        super(Component.literal("UnHook"));
        unHookFunction = Manager.FUNCTION_MANAGER.unHook;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        handleTimers();
    }

    private void handleTimers() {
        ClientManager.legitMode = true;
        unHookFunction.onUnhook();
        mc.setScreen(null);
    }
}
