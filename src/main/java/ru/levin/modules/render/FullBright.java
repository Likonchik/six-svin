package ru.levin.modules.render;

import net.minecraft.client.OptionInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.mixin.iface.OptionInstanceAccessor;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

// FullBright: гамма (можно в МИНУС — темнее ванили — и выше 1) + ночное зрение.
//   - Гамма: слайдер -1..15 пишется НАПРЯМУЮ в поле OptionInstance.value через OptionInstanceAccessor,
//     минуя set()'s валидацию 0..1. Реальная гамма юзера сохраняется и восстанавливается при выключении.
//   - Ночное зрение: клиентский эффект NIGHT_VISION как дополнительная яркость.
@FunctionAnnotation(name = "FullBright", desc = "Освещает/затемняет местность (гамма + ночное зрение)", type = Type.Render)
public class FullBright extends Function {

    private final BooleanSetting useGamma = new BooleanSetting("Гамма", true, "Управлять яркостью гаммой");
    private final SliderSetting gamma = new SliderSetting("Яркость гаммы", 1.0f, -1.0f, 15.0f, 0.05f, () -> useGamma.get())
            .withDesc("<0 = темнее ванили, 1 = фуллбрайт, >1 = ярче");
    private final BooleanSetting nightVision = new BooleanSetting("Ночное зрение", true, "Доп. эффект ночного зрения");

    private final MobEffectInstance nightVisionEffect = new MobEffectInstance(
            MobEffects.NIGHT_VISION, -1, 255, false, false, true);

    private Double savedGamma = null; // реальная гамма юзера — чтобы вернуть при выключении

    public FullBright() {
        addSettings(useGamma, gamma, nightVision);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc.player == null || mc.options == null) return;

        OptionInstance<Double> opt = mc.options.gamma();
        if (useGamma.get()) {
            if (savedGamma == null) savedGamma = opt.get();
            double target = gamma.get().doubleValue();
            if (Math.abs(opt.get() - target) > 1e-4) {
                ((OptionInstanceAccessor) (Object) opt).setValueRaw(Double.valueOf(target)); // минуя clamp 0..1
            }
        } else if (savedGamma != null) {
            opt.set(savedGamma);
            savedGamma = null;
        }

        if (nightVision.get()) {
            mc.player.addEffect(nightVisionEffect, mc.player);
        } else if (mc.player.hasEffect(nightVisionEffect.getEffect())) {
            mc.player.removeEffect(nightVisionEffect.getEffect());
        }
    }

    @Override
    public void onDisable() {
        if (savedGamma != null && mc.options != null) {
            mc.options.gamma().set(savedGamma); // savedGamma — валидное 0..1, set() ок
            savedGamma = null;
        }
        if (mc.player != null) mc.player.removeEffect(nightVisionEffect.getEffect());
        super.onDisable();
    }
}
