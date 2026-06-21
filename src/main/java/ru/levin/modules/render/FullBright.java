package ru.levin.modules.render;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "FullBright",desc  = "Освещает местность", type = Type.Render)
public class FullBright extends Function {
    private final MobEffectInstance nightVisionEffect = new MobEffectInstance(
            MobEffects.NIGHT_VISION,
            -1,
            255,
            false,
            false,
            true
    );
    @Override
    public void onEvent(Event event) {
        if (event instanceof EventUpdate) {
          mc.player.addEffect(nightVisionEffect,mc.player);
        }
    }

    @Override
    public void onDisable() {
        mc.player.removeEffect(nightVisionEffect.getEffect());
        super.onDisable();
    }
}