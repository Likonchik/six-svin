package ru.levin.modules.combat;

import net.minecraft.world.item.ItemStack;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

// Авто-перезарядка оружия TACZ. Перезарядка серверно-валидируется самим TACZ (op.reload()), мы лишь
// дёргаем её, когда патронов в магазине <= порога, оружие сейчас не перезаряжается и (опц.) в инвентаре
// есть боеприпасы. Весь доступ к TACZ обёрнут в try/catch(Throwable): без мода модуль инертен.
@SuppressWarnings("All")
@FunctionAnnotation(name = "AutoReload", keywords = {"Перезарядка", "Reload", "Стволы"}, desc = "Авто-перезарядка оружия TACZ", type = Type.Combat)
public class AutoReload extends Function {

    private final SliderSetting threshold = new SliderSetting("Порог патронов", 0, 0, 60, 1);
    private final BooleanSetting needInvAmmo = new BooleanSetting("Только при наличии патронов", true);
    private final SliderSetting delay = new SliderSetting("Задержка, мс", 150, 0, 2000, 50);

    private long lastReload = 0L;

    public AutoReload() {
        addSettings(threshold, needInvAmmo, delay);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc.player == null) return;

        try {
            ItemStack stack = mc.player.getMainHandItem();
            com.tacz.guns.api.item.IGun gun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if (gun == null) return;

            // не дёргать, пока идёт перезарядка
            com.tacz.guns.api.entity.IGunOperator op =
                    com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(mc.player);
            if (op.getSynReloadState().getStateType().isReloading()) return;

            if (gun.getCurrentAmmoCount(stack) > threshold.get().intValue()) return;

            if (needInvAmmo.get()) {
                try {
                    if (!gun.hasInventoryAmmo(mc.player, stack, gun.useInventoryAmmo(stack))) return;
                } catch (Throwable ignored) {
                    // сигнатура отличается на каком-то билде TACZ — не блокируем перезарядку
                }
            }

            long now = System.currentTimeMillis();
            if (now - lastReload < (long) delay.get().floatValue()) return;

            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).reload();
            lastReload = now;
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDisable() {
        lastReload = 0L;
        super.onDisable();
    }
}
