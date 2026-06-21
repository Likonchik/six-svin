package ru.levin.mixin.player;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.ExosWare;
import ru.levin.manager.IMinecraft;

@Mixin(KeyboardHandler.class)
public class MixinKeyBoard implements IMinecraft {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == 1 && !(mc.screen instanceof Screen)) {
            ExosWare main = ExosWare.getInstance();
            main.keyPress(key);
        }
    }
}