package ru.levin.mixin.world;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventEntitySpawn;

@Mixin(ClientLevel.class)
public class MixinClientWorld {
    @Inject(method = "addEntity", at = @At("TAIL"))
    private void onAddEntity(Entity entity, CallbackInfo ci) {
        Event.call(new EventEntitySpawn(entity));
    }
}