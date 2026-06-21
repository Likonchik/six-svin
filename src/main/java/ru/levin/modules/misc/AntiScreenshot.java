package ru.levin.modules.misc;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import ru.levin.events.Event;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Антискринилка. Скриншот в MC качает текстуру фреймбуфера = ПОСЛЕДНИЙ уже отрисованный кадр (со читом),
// поэтому «спрятать в момент захвата» не успевает — на снимок попадает грязный кадр. Решение надёжнее:
// ОТКЛАДЫВАЕМ сам захват. Перехватываем Screenshot.grab, гасим все модули, ждём пару реально отрисованных
// (уже чистых) кадров и только потом делаем настоящий grab — он снимает заведомо чистый кадр. После
// захвата модули включаются обратно.
//
// Гасим модули прямо по полю state (не setState/toggle), чтобы НЕ дёргать onEnable/onDisable: без звуков,
// сброса таймеров, флаша Blink и конфликтов между модулями. Покрывает F2 и моды-скриналки, дёргающие
// ванильный Screenshot.grab. ВНЕШНИЙ захват экрана (OBS, PrintScreen ОС, демонстрация в Discord) — это
// композит ОС, клиентский мод его перехватить не может.
@FunctionAnnotation(name = "AntiScreenshot", keywords = {"Антискрин", "Скриншот", "Screenshot"}, desc = "Прячет чит на скриншотах (F2 и моды-скриналки)", type = Type.Misc)
public class AntiScreenshot extends Function {

    // гасит не-модульные оверлеи (GPS/вейпойнты) в точках диспатча на время отложенного захвата
    public static volatile boolean hiding = false;
    // true пока мы сами повторно зовём grab — чтобы не перехватить собственный вызов
    public static volatile boolean capturing = false;

    private static int delay = 0;                       // кадров до настоящего захвата
    private static final List<Function> disabledModules = new ArrayList<>();

    // отложенный запрос grab
    private static File reqDir;
    private static String reqName;
    private static RenderTarget reqFb;
    private static Consumer<Component> reqMsg;

    @Override
    public void onEvent(Event event) {
    }

    // вызывается из MixinScreenshot при перехвате grab; true -> оригинальный (немедленный) grab отменить
    public static boolean interceptGrab(File dir, String name, RenderTarget fb, Consumer<Component> msg) {
        if (capturing) return false; // это наш собственный отложенный вызов — пропустить как есть
        AntiScreenshot self = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.antiScreenshot : null;
        if (self == null || !self.state) return false; // выключено — обычный скриншот
        if (delay > 0) return true; // отложенный захват уже идёт — глушим лишние запросы

        reqDir = dir;
        reqName = name;
        reqFb = fb;
        reqMsg = msg;

        // гасим все включённые модули (кроме самой антискринилки) напрямую по полю state
        disabledModules.clear();
        for (Function f : Manager.FUNCTION_MANAGER.getFunctions()) {
            if (f != self && f.state) {
                f.state = false;
                disabledModules.add(f);
            }
        }
        hiding = true;
        delay = 2; // подождать пару отрисованных (чистых) кадров, потом снять
        return true; // отменяем немедленный захват грязного кадра
    }

    // вызывается в конце каждого кадра (GameRenderer.render TAIL)
    public static void onRenderEnd() {
        if (delay <= 0) return;
        delay--;
        if (delay > 0) return; // ещё не дождались чистого кадра

        try {
            capturing = true;
            // фреймбуфер сейчас держит только что отрисованный ЧИСТЫЙ кадр -> снимок выйдет без чита
            if (reqName != null) Screenshot.grab(reqDir, reqName, reqFb, reqMsg);
            else Screenshot.grab(reqDir, reqFb, reqMsg);
        } catch (Throwable t) {
            System.out.println("[AntiScreenshot] deferred grab failed: " + t);
        } finally {
            capturing = false;
            hiding = false;
            for (Function f : disabledModules) f.state = true; // включаем модули обратно
            disabledModules.clear();
            reqDir = null;
            reqName = null;
            reqFb = null;
            reqMsg = null;
        }
    }
}
