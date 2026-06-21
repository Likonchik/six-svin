package ru.levin.manager;


import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.vertex.Tesselator;

@SuppressWarnings("All")
public interface IMinecraft {
    Minecraft mc = Minecraft.getInstance();

    static DeltaTracker tickCounter() {
        return Lazy.tickCounter;
    }
    static Tesselator tessellator() {
        return Lazy.tessellator;
    }
    static Minecraft getMc() {
        return Lazy.minecraftClient;
    }
    class Lazy {
        private static final Minecraft minecraftClient = Minecraft.getInstance();
        private static final DeltaTracker tickCounter = mc.getTimer();
        private static final Tesselator tessellator = Tesselator.getInstance();
    }
}
