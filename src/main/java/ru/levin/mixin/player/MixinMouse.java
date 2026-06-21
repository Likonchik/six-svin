package ru.levin.mixin.player;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.ExosWare;
import ru.levin.events.Event;
import ru.levin.events.impl.input.EventMouse;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;

@Mixin(MouseHandler.class)
public class MixinMouse implements IMinecraft {
    @Inject(method = "onPress", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;player:Lnet/minecraft/client/player/LocalPlayer;", ordinal = 0))
    private void beforeSpectatorCheck(long window, int button, int action, int mods, CallbackInfo ci) {
        boolean bl = action == 1;
        if (!bl) return;
        ExosWare main = ExosWare.getInstance();
        main.keyPress(-100 + button);

        Event.call(new EventMouse(button));
    }
    @Inject(method = "onPress", at = @At("HEAD"))
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (mc.screen instanceof ChatScreen) {
            Manager.DRAG_MANAGER.draggables.values().forEach(dragging -> {
                if (dragging.getModule() != null && dragging.getModule().state) {
                    dragging.onRelease(button);
                }
            });
        }

    }
}