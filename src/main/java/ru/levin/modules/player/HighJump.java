package ru.levin.modules.player;

import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.move.MoveUtil;

@FunctionAnnotation(name = "HighJump", type = Type.Move)
public class HighJump extends Function {

    private final SliderSetting sila = new SliderSetting("Сила", 2.0f, 0.0f, 5f, 0.1f).withDesc("Сила прыжка вверх");

    private boolean wasShulkerOpen = false;
    private long jumpStartTime = 0;

    private long guiOpenTime = 0;

    public HighJump() {
        addSettings(sila);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventMotion)) return;
        mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, sila.get().floatValue(), mc.player.getDeltaMovement().z);
        if (mc.options.keySprint.isDown()) {
            MoveUtil.setMotion(sila.get().floatValue());
        }
        long currentTime = System.currentTimeMillis();
        if (mc.screen instanceof ShulkerBoxScreen && guiOpenTime == 0) {
            wasShulkerOpen = true;
            guiOpenTime = currentTime;
        }

        if (guiOpenTime != 0 && currentTime - guiOpenTime >= 800) {
            mc.player.closeContainer();
            mc.player.closeContainer();
            guiOpenTime = 0;
        }

        if (wasShulkerOpen && mc.screen == null) {
            wasShulkerOpen = false;
            jumpStartTime = currentTime;

            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, sila.get().floatValue(), mc.player.getDeltaMovement().z);
            if (mc.options.keySprint.isDown()) {
                MoveUtil.setMotion(sila.get().floatValue());
            }
        }

        if (jumpStartTime != 0 && currentTime - jumpStartTime >= 3000) {
            jumpStartTime = 0;
        }
    }
}
