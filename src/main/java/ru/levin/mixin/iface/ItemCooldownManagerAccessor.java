package ru.levin.mixin.iface;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

// 1.21.1: ItemCooldowns is keyed by Item (the Identifier "cooldown group" model was a 1.21.2 change).
// Map values are the package-private ItemCooldowns$CooldownInstance — exposed as Object and read via ItemCooldownEntryAccessor.
@Mixin(ItemCooldowns.class)
public interface ItemCooldownManagerAccessor {
    @Accessor("cooldowns")
    Map<Item, Object> getCooldowns();

    @Accessor("tickCount")
    int getTickCount();
}
