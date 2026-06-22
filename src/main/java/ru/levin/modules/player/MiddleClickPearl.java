package ru.levin.modules.player;

import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import ru.levin.events.Event;
import ru.levin.events.impl.input.EventKey;
import ru.levin.events.impl.input.EventMouse;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BindSetting;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.util.player.InventoryUtil;

@FunctionAnnotation(name = "MiddleClickPearl",keywords = {"MCP"}, desc = "Откидывание пёрла по колёсику мыши", type = Type.Player)
public class MiddleClickPearl extends Function {
    private final ModeSetting mode = new ModeSetting("Тип","Обычный","Обычный","По бинду").withDesc("Способ кидания пёрла");
    private final BindSetting bind = new BindSetting("Кнопка кидания",0,() -> mode.is("По бинду")).withDesc("Клавиша кидания пёрла");
    private final BooleanSetting inventoryUse = new BooleanSetting("Использовать из инвентаря",true,"Не используйте на HollyWorld (баниться)");

    public MiddleClickPearl() {
        addSettings(mode,bind,inventoryUse);
    }

    @Override
    public void onEvent(Event event) {
        if (mode.is("Обычный")) {
            if (event instanceof EventMouse mouseTick) {
                if (mouseTick.getButton() == 2) {
                    handleMouseTickEvent();
                }
            }
        }
        if (mode.is("По бинду")) {
            if (event instanceof EventKey e) {
                if (e.key == bind.getKey()) {
                    handleMouseTickEvent();
                }
            }
        }
    }

    private void handleMouseTickEvent() {
        if (!mc.player.getCooldowns().isOnCooldown(Items.ENDER_PEARL)) {
            if (Manager.FUNCTION_MANAGER.attackAura.target != null) {
                mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(mc.player.getYRot(), mc.player.getXRot(), mc.player.onGround()));
            }
            InventoryUtil.inventorySwapClick2(Items.ENDER_PEARL, inventoryUse.get(), true);
        }
    }
}
