package ru.levin.mixin.iface;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface ClientPlayerEntityAccessor {
    @Accessor("yRotLast")
    float getLastYaw();

    @Accessor("xRotLast")
    float getLastPitch();

    @Invoker("isMoving")
    boolean invokeIsWalking();

    @Invoker("canStartSprinting")
    boolean invokeCanSprint();

    @Accessor("wasSprinting")
    boolean getLastSprinting();
}
