package ru.levin.mixin.iface;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 1.21.1: ItemCooldowns$CooldownInstance(int startTime, int endTime) — replaces the 1.21.2 "Entry.endTick".
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface ItemCooldownEntryAccessor {
    @Accessor("endTime")
    int getEndTime();
}
