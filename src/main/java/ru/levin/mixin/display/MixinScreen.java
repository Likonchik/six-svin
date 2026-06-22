package ru.levin.mixin.display;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.levin.modules.misc.AntiScreenshot;

// Антискринилка: наши экраны (ClickGUI, AltManager и т.п.) — это ванильные Screen, и флаг hiding их не
// прячет, т.к. они рисуются движком, а не через EventRender2D/3D. Поэтому при скрытии ОТМЕНЯЕМ их отрисовку:
// экран остаётся открытым и интерактивным, но в кадр (и на любой скриншот) не попадает. Мир под ним
// рендерится как обычно -> снимок чистый. После окончания окна скрытия экран снова рисуется сам.
@Mixin(Screen.class)
public class MixinScreen {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"), cancellable = true)
    private void onetap$hideOwnScreens(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // this — конкретный экземпляр Screen; наши экраны лежат в пакете ru.levin.screens
        if (AntiScreenshot.hiding && this.getClass().getName().startsWith("ru.levin.screens")) {
            ci.cancel();
        }
    }
}
