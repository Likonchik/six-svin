package ru.levin.modules.player;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.mixin.iface.MinecraftClientAccessor;
import ru.levin.mixin.iface.MixinLivingEntityAccessor;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;

@FunctionAnnotation(name = "NoDelay" ,desc  = "Убирает задержку предметам", type = Type.Player)
public class NoDelay extends Function {

    private final BooleanSetting jump = new BooleanSetting("Прыжок",true,"Убирает задержку прыжка");
    private final BooleanSetting xp = new BooleanSetting("Пузырёк опыта",true,"Без задержки на опыт");
    private final BooleanSetting crystal = new BooleanSetting("Кристаллы",true,"Без задержки на кристаллы");
    private final BooleanSetting place = new BooleanSetting("ПКМ",false,"Без задержки установки блоков");

    public NoDelay() {
        addSettings(jump,xp,crystal,place);
    }


    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
            if (jump.get()) {
                ((MixinLivingEntityAccessor) mc.player).setLastJumpCooldown(0);
            }
            if (check(mc.player.getMainHandItem().getItem()))
                ((MinecraftClientAccessor) mc).setUseCooldown(0);
        }
    }
    private boolean check(Item item) {
        return (item instanceof BlockItem && place.get()) || (item == Items.END_CRYSTAL && crystal.get()) || (item == Items.EXPERIENCE_BOTTLE && xp.get());
    }
}
