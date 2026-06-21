package ru.levin.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.ChatFormatting;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.ClientManager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.player.TimerUtil;

@SuppressWarnings("All")
@FunctionAnnotation(name = "GodMode", type = Type.Player, desc = "ГодМод а катлаван ебаная паста")
public class GodMode extends Function {
    TimerUtil timerUtil = new TimerUtil();
    private long lastClickTime = 0;
    private boolean firstClick = true;

    public GodMode() {
        addSettings();
    }

    public void onEvent(Event event) {
        if (event instanceof EventPacket eventPacket) {
            if (eventPacket.getPacket() instanceof ClientboundSystemChatPacket packet) {
                String message = packet.content().getString();

                if (message.contains("Вы не можете телепортироваться в PVP режиме") || message.contains("Вы успешно телепортированы на варп farm!")) {
                    eventPacket.setCancel(true);
                }
            }
        }
        if (event instanceof EventUpdate) {
            if (timerUtil.hasTimeElapsed(8000)) {
                ClientManager.message(ChatFormatting.WHITE + "При включенном GodMode" + ChatFormatting.RED + " НЕЛЬЗЯ " + ChatFormatting.WHITE + "использовать любые свапы");
            }
            timerUtil.reset();
            if (mc.screen instanceof DeathScreen) {
                toggle();
            }
        }

        if (event instanceof EventUpdate) {
            if (ClientManager.playerIsPVP()) {
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastClickTime >= 35) {
                    lastClickTime = currentTime;

                    Minecraft.getInstance().execute(() -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 1, 1, ClickType.PICKUP, mc.player));
                }

            }
        }
    }

    @Override
    public void onEnable() {
        firstClick = true;
        lastClickTime = System.currentTimeMillis();
        if (!ClientManager.playerIsPVP()) {
            mc.player.connection.sendCommand("warp");

            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    Minecraft.getInstance().execute(() -> mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, 21, 1, ClickType.PICKUP, mc.player));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();

            new Thread(() -> {
                try {
                    Thread.sleep(700);
                    Minecraft.getInstance().execute(() -> {
                        mc.screen = null;
                        mc.mouseHandler.grabMouse();
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}