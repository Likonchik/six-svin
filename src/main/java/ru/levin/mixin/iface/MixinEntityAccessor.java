package ru.levin.mixin.iface;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface MixinEntityAccessor {
    @Invoker("calculateViewVector")
    Vec3 invokeGetRotationVector(float pitch, float yaw);
}
