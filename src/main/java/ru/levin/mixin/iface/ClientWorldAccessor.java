package ru.levin.mixin.iface;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientLevel.class)
public interface ClientWorldAccessor {
    @Accessor("blockStatePredictionHandler")
    BlockStatePredictionHandler getPendingUpdateManager();
}
