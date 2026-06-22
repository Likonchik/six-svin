package ru.levin.modules.player;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.util.player.TimerUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@FunctionAnnotation(name = "GuiWalk",keywords = {"InventoryMove","GuiMove"}, type = Type.Player)
public class GuiWalk extends Function {
    public final ModeSetting bypass = new ModeSetting("Тип","Обычный","Обычный","FunTime").withDesc("Тип обхода античита");

    private final Queue<ServerboundContainerClickPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private boolean wasInventoryOpen = false;
    private final TimerUtil timer = new TimerUtil();

    public GuiWalk() {
        addSettings(bypass);
    }

    @Override
    public void onEvent(Event event) {
        List<KeyMapping> keyBindings = new ArrayList<>(Arrays.asList(
                mc.options.keyUp,
                mc.options.keyDown,
                mc.options.keyLeft,
                mc.options.keyRight,
                mc.options.keyJump
        ));

        if (mc.screen instanceof ChatScreen || mc.screen instanceof SignEditScreen) {
            for (KeyMapping keyBinding : keyBindings) {
                keyBinding.setDown(false);
            }
            return;
        }
        if (bypass.is("FunTime") && !packetQueue.isEmpty()) {
            if (!timer.hasTimeElapsed(100)) {
                for (KeyMapping keyBinding : keyBindings) {
                    keyBinding.setDown(false);
                }
                return;
            }
        }

        keyBindings.forEach(this::updateKeyBinding);

        boolean isInventoryOpen = mc.screen instanceof InventoryScreen;

        if (bypass.is("FunTime")) {
            if (isInventoryOpen) {
                wasInventoryOpen = true;
            } else if (wasInventoryOpen) {
                sendQueuedPackets();
                wasInventoryOpen = false;
                timer.reset();
            }
        } else {
            packetQueue.clear();
        }
    }

    private void updateKeyBinding(KeyMapping keyBinding) {
        long handle = mc.getWindow().getWindow();
        int code = keyBinding.getDefaultKey().getValue();
        keyBinding.setDown(InputConstants.isKeyDown(handle, code));
    }

    public void queuePacket(ServerboundContainerClickPacket packet) {
        if (bypass.is("FunTime") ) {
            packetQueue.add(packet);
        } else if (mc.getConnection() != null) {
            mc.getConnection().send(packet);
        }
    }

    private void sendQueuedPackets() {
        new Thread(() -> {
            try {
                Thread.sleep(80);
                while (!packetQueue.isEmpty()) {
                    ServerboundContainerClickPacket packet = packetQueue.poll();
                    if (packet != null && mc.getConnection() != null) {
                        mc.getConnection().send(packet);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }
}
