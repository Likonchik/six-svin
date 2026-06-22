package ru.levin.modules.movement;

import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.move.MoveUtil;
import ru.levin.util.player.InventoryUtil;
import ru.levin.util.player.TimerUtil;

@FunctionAnnotation(name = "Flight", desc = "", type = Type.Move)
public class Flight extends Function {
    private final ModeSetting mode = new ModeSetting("Тип", "Motion", "Motion","ElytraRWOld").withDesc("Режим полёта");
    private final SliderSetting xspeed = new SliderSetting("X - Скорость", 1f, 0.0f, 5f, 0.1f).withDesc("Горизонтальная скорость");
    private final SliderSetting yspeed = new SliderSetting("Y - Скорость", 1f, 0.0f, 5f, 0.1f).withDesc("Вертикальная скорость");

    public Flight() {
        addSettings(mode, xspeed, yspeed);
    }
    private final TimerUtil timerUtil = new TimerUtil(), swapTimer = new TimerUtil();
    int item = -1;

    @Override
    public void onEvent(Event event) {
        if (mode.is("Motion")) {
            if (event instanceof EventMotion) {
                double y = 0.0;
                if (mc.options.keyJump.isDown()) {
                    y = yspeed.get().floatValue();
                } else if (mc.options.keyShift.isDown()) {
                    y = -yspeed.get().floatValue();
                }
                mc.player.setDeltaMovement(0, y, 0);
                if (mc.options.keySprint.isDown()) {
                    MoveUtil.setMotion(xspeed.get().floatValue());
                }
            }
        }
        if (mode.is("ElytraRWOld")) {
            if (event instanceof EventUpdate) {
                for (int i = 0; i < 9; ++i) {
                    if (mc.player.getInventory().getItem(i).is(Items.ELYTRA) && !mc.player.onGround() && !mc.player.isUnderWater() && !mc.player.isInLava() && !mc.player.isFallFlying()) {
                        int swapDelay = 520;
                        if (timerUtil.hasTimeElapsed(swapDelay)) {
                            swapTimer.reset();
                            InventoryUtil.swapSlotsUniversal(6, i, false, false);
                            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                            mc.player.startFallFlying();
                            InventoryUtil.swapSlotsUniversal(6, i, false, false);
                            item = i;
                            timerUtil.reset();
                        }

                        if (mc.player.isFallFlying()) {
                            InventoryUtil.inventorySwapClick2(Items.FIREWORK_ROCKET,true, false);
                        }
                    }
                }
            }
        }
    }
}