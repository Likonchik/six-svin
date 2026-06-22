package ru.levin.mixin.iface;

import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Обходит OptionInstance.set()'s ValueSet.validateValue() (для гаммы UnitDouble зажимает в 0..1).
// Пишем package-private поле `T value;` напрямую -> FullBright может выкрутить гамму в минус / выше 1.
@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor {
    @Accessor("value")
    void setValueRaw(Object value);
}
