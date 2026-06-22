package ru.levin.mixin.player;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.ExosWare;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;

@Mixin(KeyboardHandler.class)
public class MixinKeyBoard implements IMinecraft {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action == 1 && !(mc.screen instanceof Screen)) {
            ExosWare main = ExosWare.getInstance();
            main.keyPress(key);
        } else if (action == 1 && mc.level == null) {
            // вне мира (титульник/мультиплеер/подключение) обычный keyPress не зовётся (открыт экран).
            // Роутим ТОЛЬКО transport-бинды MediaPlayer, чтобы можно было рулить музыкой из меню.
            try {
                if (Manager.FUNCTION_MANAGER != null && Manager.FUNCTION_MANAGER.mediaPlayer != null) {
                    Manager.FUNCTION_MANAGER.mediaPlayer.onTransportKey(key);
                }
            } catch (Throwable ignored) {}
        }
    }
}