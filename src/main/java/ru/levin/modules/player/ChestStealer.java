package ru.levin.modules.player;

import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.player.TimerUtil;

import java.util.List;

@FunctionAnnotation(name = "ChestStealer", desc = "", type = Type.Player)
public class ChestStealer extends Function {

    private final ModeSetting mode = new ModeSetting("Тип", "Обычный", "Обычный", "Умный");
    private final SliderSetting stealDelay = new SliderSetting("Задержка", 120f, 0f, 1000f, 1f);

    private final TimerUtil timer = new TimerUtil();

    private static final List<String> BLOCKED_TITLES = List.of(
            "Аукцион", "Warp", "Варпы", "Меню", "Выбор набора", "Кейсы", "Магазин"
    );

    public ChestStealer() {
        addSettings(mode, stealDelay);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate) || !(mc.screen instanceof ContainerScreen container)) return;

        String title = container.getTitle().getString().toLowerCase();
        for (String blocked : BLOCKED_TITLES) {
            if (title.contains(blocked.toLowerCase())) return;
        }

        var handler = container.getMenu();
        int chestSize = handler.getRowCount() * 9;
        boolean instant = stealDelay.get().floatValue() == 0;

        for (int i = 0; i < chestSize; i++) {
            var stack = handler.getSlot(i).getItem();
            if (stack.isEmpty() || stack.getItem() == Items.AIR) continue;

            if (mode.is("Умный") && !Manager.CHESTSTEALER_MANAGER.isAllowed(stack.getItem())) continue;

            if (instant || timer.hasTimeElapsed(stealDelay.get().longValue())) {
                click(handler.containerId, i);
                if (!instant) timer.reset();
                if (!instant) break;
            }
        }
    }

    private void click(int id, int slot) {
        mc.gameMode.handleInventoryMouseClick(id, slot, 0, ClickType.QUICK_MOVE, mc.player);
    }
}
