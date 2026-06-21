package ru.levin.modules.player;

import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(
        name = "ItemFixSwap",
        keywords = {"NoSlotChange", "NoServerDesync", "СлотФиксер"},
        desc = "Убирает переключение слота от античита",
        type = Type.Player
)
public class ItemFixSwap extends Function {
    public ItemFixSwap() {
        addSettings();
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventPacket e) {
            if (e.isReceivePacket()) {
               if (e.getPacket() instanceof ServerboundSetCarriedItemPacket packetHeldItemChange) {
                   event.setCancel(true);
               }
            }
        }
    }
}
