package ru.levin.manager.proxyManager;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.apache.commons.lang3.StringUtils;
import ru.levin.manager.Manager;

public class GuiProxy extends Screen {
    private boolean isSocks4;
    private EditBox ipPort, username, password;
    private Checkbox enabledCheck;
    private final Screen parentScreen;
    private String msg = "";
    private int[] positionY;
    private int positionX;

    public GuiProxy(Screen parentScreen) {
        super(Component.literal("proxy"));
        this.parentScreen = parentScreen;
    }

    private static boolean isValidIpPort(String ipP) {
        String[] split = ipP.split(":");
        if (split.length != 2 || !StringUtils.isNumeric(split[1])) return false;
        int port = Integer.parseInt(split[1]);
        return port >= 0 && port <= 0xFFFF;
    }

    private boolean checkProxy() {
        if (!isValidIpPort(ipPort.getValue())) {
            msg = ChatFormatting.RED + "Неверный порт";
            ipPort.setFocused(true);
            return false;
        }
        return true;
    }

    private void centerButtons(int amount, int buttonLength, int gap) {
        positionX = (this.width - buttonLength) / 2;
        positionY = new int[amount];
        int startY = (this.height - (amount - 1) * gap) / 2;
        for (int i = 0; i < amount; i++) positionY[i] = startY + gap * i;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        msg = "";
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float partialTicks) {
        super.render(context, mouseX, mouseY, partialTicks);

        if (enabledCheck.selected() && !isValidIpPort(ipPort.getValue())) enabledCheck.onPress();

        context.drawString(font, "Тип прокси", positionX - 65, positionY[1] + 5, 0xA08080);
        context.drawCenteredString(font, "Авторизация", this.width / 2, positionY[3] + 8, ChatFormatting.WHITE.getColor());
        context.drawString(font, "IP:PORT", positionX - 55, positionY[2] + 5, 0xA08080);

        ipPort.render(context, mouseX, mouseY, partialTicks);
        username.render(context, mouseX, mouseY, partialTicks);
        if (!isSocks4) password.render(context, mouseX, mouseY, partialTicks);
    }

    @Override
    public void init() {
        ProxyManager pm = Manager.PROXY_MANAGER;
        centerButtons(10, 160, 26);
        isSocks4 = pm.proxy.type == Proxy.ProxyType.SOCKS4;

        this.addRenderableWidget(Button.builder(Component.literal(isSocks4 ? "Socks 4" : "Socks 5"), b -> {
            isSocks4 = !isSocks4;
            b.setMessage(Component.literal(isSocks4 ? "Socks 4" : "Socks 5"));
        }).bounds(positionX, positionY[1], 160, 20).build());

        ipPort = new EditBox(font, positionX, positionY[2], 160, 20, Component.literal(""));
        ipPort.setValue(pm.proxy.ipPort);
        ipPort.setMaxLength(1024);
        ipPort.setFocused(true);
        addWidget(ipPort);

        username = new EditBox(font, positionX, positionY[4], 160, 20, Component.literal(""));
        username.setValue(pm.proxy.username);
        username.setMaxLength(255);
        addWidget(username);

        password = new EditBox(font, positionX, positionY[5], 160, 20, Component.literal(""));
        password.setValue(pm.proxy.password);
        password.setMaxLength(255);
        addWidget(password);

        int posXButtons = (width - 160) / 2;
        addRenderableWidget(Button.builder(Component.translatable("Готово"), b -> {
            if (!checkProxy()) return;
            pm.proxy = new Proxy(isSocks4, ipPort.getValue(), username.getValue(), password.getValue());
            pm.proxyEnabled = enabledCheck.selected();
            pm.setDefaultProxy(pm.proxy);
            pm.saveConfig();
            minecraft.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
        }).bounds(posXButtons, positionY[8], 77, 20).build());

        enabledCheck = Checkbox.builder(Component.translatable("Включить"), font)
                .pos((width - 15 - font.width(Component.translatable("Включить"))) / 2, positionY[7])
                .selected(pm.proxyEnabled)
                .build();
        addRenderableWidget(enabledCheck);

        addRenderableWidget(Button.builder(Component.translatable("Назад"), b -> minecraft.setScreen(parentScreen))
                .bounds(posXButtons + 160 / 2 + 3, positionY[8], 77, 20).build());
    }

    @Override
    public void onClose() {
        super.onClose();
        msg = "";
    }
}
