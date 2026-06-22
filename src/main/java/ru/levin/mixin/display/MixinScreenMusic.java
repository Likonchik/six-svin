package ru.levin.mixin.display;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.modules.render.MediaPlayer;

// Рисует плавающую панель MediaPlayer, когда открыт ЛЮБОЙ экран (чат/ClickGUI/инвентарь/титульник/
// мультиплеер/подключение) — т.е. вне мира и когда in-game HUD не рисуется. In-world без экрана панель
// рисует сам модуль из EventRender2D (mc.screen == null), поэтому двойного рендера нет. Бан-сейф TAIL-инжект.
@Mixin(Screen.class)
public class MixinScreenMusic implements IMinecraft {

    @Inject(method = "render", at = @At("TAIL"))
    private void onetap$musicPanel(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        try {
            if (Manager.FUNCTION_MANAGER == null) return;
            if (mc.screen instanceof ru.levin.screens.musicplayer.MediaPlayerScreen) return; // меню само себя рисует
            MediaPlayer mp = Manager.FUNCTION_MANAGER.mediaPlayer;
            if (mp == null || !mp.hudVisible()) return;
            mp.tickAlways();
            mp.renderPanel(graphics);
        } catch (Throwable ignored) {
        }
    }
}
