package ru.levin.mixin.iface;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftClientAccessor {
    @Accessor("user")
    @Mutable
    void setSession(User session);

    @Accessor("rightClickDelay")
    int getUseCooldown();

    @Accessor("rightClickDelay")
    void setUseCooldown(int val);
}
