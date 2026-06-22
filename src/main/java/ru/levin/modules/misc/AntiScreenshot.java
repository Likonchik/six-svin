package ru.levin.modules.misc;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import ru.levin.events.Event;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Антискринилка. Покрывает три вектора захвата экрана и во всех гасит модули:
//   1) F2 / моды через ванильный Screenshot.grab — ОТКЛАДЫВАЕМ захват: гасим все модули, ждём пару реально
//      отрисованных (уже чистых) кадров и только потом делаем настоящий grab — он снимет заведомо чистый
//      кадр. Юзер всё равно получает свой скриншот, просто без чита.
//   2) Прямой Screenshot.takeScreenshot(framebuffer) в обход grab (серверные «анти-читы», напр. superbwarfare
//      TargetSignalMessage/RadarSignalDataMessage) — перехватывается в MixinScreenshot. Фреймбуфер уже держит
//      грязный кадр, а синхронный вызов отложить нельзя, поэтому захват глушится насовсем (возврат null).
//   3) Снимок ВСЕГО рабочего стола через java.awt.Robot (superbwarfare TargetImaging) — это JVM-уровень,
//      ванильным хуком его не поймать; точечный MixinTargetSignal отменяет сам обработчик пакета. panic()
//      дополнительно гасит модули на окно кадров, чтобы окно игры было чистым, даже если пакет не пойман.
//
// Гасим модули прямо по полю state (не setState/toggle), чтобы НЕ дёргать onEnable/onDisable: без звуков,
// сброса таймеров, флаша Blink и конфликтов между модулями. ВНЕШНИЙ композит ОС (OBS, PrintScreen ОС,
// демонстрация в Discord) клиентский мод перехватить не может.
@FunctionAnnotation(name = "AntiScreenshot", keywords = {"Антискрин", "Скриншот", "Screenshot"}, desc = "Прячет чит на скриншотах (F2, моды-скриналки, серверные анти-читы)", type = Type.Misc)
public class AntiScreenshot extends Function {

    // сколько кадров держать модули погашенными при «глухом» (неотменяемом) захвате
    private static final int HIDE_FRAMES = 10;

    // гасит не-модульные оверлеи (GPS/вейпойнты) в точках диспатча на время скрытия
    public static volatile boolean hiding = false;
    // true пока мы сами повторно зовём grab — чтобы не перехватить собственный (чистый) вызов
    public static volatile boolean capturing = false;

    private static int framesLeft = 0;                  // кадров до завершения сессии скрытия
    private static boolean grabPending = false;         // в конце сессии нужно переснять отложенный grab
    private static final List<Function> disabledModules = new ArrayList<>();

    // отложенный запрос grab
    private static File reqDir;
    private static String reqName;
    private static RenderTarget reqFb;
    private static Consumer<Component> reqMsg;

    @Override
    public void onEvent(Event event) {
    }

    // включена ли антискринилка прямо сейчас
    public static boolean isActive() {
        AntiScreenshot self = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.antiScreenshot : null;
        return self != null && self.state;
    }

    // начать (или продлить) сессию скрытия на frames кадров.
    // hiding=true сам по себе коротит ВСЕ EventRender2D/3D -> оверлеи (ESP/HUD/трассеры) исчезают мгновенно
    // без выключения модулей. Дополнительно гасим модули, КРОМЕ категории Move: модули движения
    // (Flight/Elytra/Speed/NoFall...) на скриншоте не видны, а их выключение на лету уронит/убьёт игрока.
    // Остальные (Render/Misc/...) гасим по state, чтобы выключить и те визуалы, что меняют рендер мира через
    // собственные миксины и флагом hiding не покрываются (Xray, FullBright, NoRender, чамсы и т.п.).
    private static void beginHide(int frames) {
        if (disabledModules.isEmpty()) {
            for (Function f : Manager.FUNCTION_MANAGER.getFunctions()) {
                if (f.state && !(f instanceof AntiScreenshot) && f.getCategory() != Type.Move) {
                    f.state = false;
                    disabledModules.add(f);
                }
            }
        }
        hiding = true;
        if (frames > framesLeft) framesLeft = frames;
    }

    // паника: внешний/неотменяемый захват (прямой takeScreenshot, Robot-скриншот десктопа) — гасим модули
    // на окно кадров, чтобы окно игры было чистым. Без переснятия — снимок и так заглушён/отменён.
    public static void panic(int frames) {
        if (!isActive()) return;
        beginHide(frames);
    }

    public static void panic() {
        panic(HIDE_FRAMES);
    }

    // вызывается из MixinScreenshot при перехвате grab; true -> оригинальный (немедленный) grab отменить
    public static boolean interceptGrab(File dir, String name, RenderTarget fb, Consumer<Component> msg) {
        if (capturing) return false;        // это наш собственный отложенный (чистый) вызов — пропустить
        if (!isActive()) return false;      // выключено — обычный скриншот

        reqDir = dir;
        reqName = name;
        reqFb = fb;
        reqMsg = msg;
        grabPending = true;

        beginHide(2);                        // подождать пару чистых кадров, потом переснять
        return true;                         // отменяем немедленный захват грязного кадра
    }

    // вызывается в конце каждого кадра (GameRenderer.render TAIL)
    public static void onRenderEnd() {
        if (framesLeft <= 0) return;
        framesLeft--;
        if (framesLeft > 0) return;          // ещё не дождались окончания окна скрытия

        try {
            if (grabPending) {
                capturing = true;
                // фреймбуфер сейчас держит только что отрисованный ЧИСТЫЙ кадр -> снимок выйдет без чита
                if (reqName != null) Screenshot.grab(reqDir, reqName, reqFb, reqMsg);
                else Screenshot.grab(reqDir, reqFb, reqMsg);
            }
        } catch (Throwable t) {
            System.out.println("[AntiScreenshot] deferred grab failed: " + t);
        } finally {
            capturing = false;
            hiding = false;
            grabPending = false;
            for (Function f : disabledModules) f.state = true; // включаем модули обратно
            disabledModules.clear();
            reqDir = null;
            reqName = null;
            reqFb = null;
            reqMsg = null;
        }
    }

    // ===== Generic-блокировщик утечки кадра по сети =====
    // Любая серверная скриналка обязана отправить байты картинки. Перехватываем исходящие custom-payload
    // (вызывается из MixinClientConnection.send) и дропаем пакет, если внутри изображение — независимо от
    // того, как назван мод и класс. Картинка режется на чанки, заголовок (магия) есть только у первого, но
    // обнаружив его, мы временно заносим весь канал в чёрный список и глушим и остальные чанки потока.

    // channelId -> момент истечения блокировки (System.currentTimeMillis). Окно обновляется на каждом чанке.
    private static final Map<String, Long> blockedChannels = new ConcurrentHashMap<>();
    private static final long BLOCK_WINDOW_MS = 3000L;

    // true -> пакет нужно отбросить (это утечка изображения)
    public static boolean shouldBlockOutgoing(Packet<?> packet) {
        if (!isActive()) return false;
        if (!(packet instanceof ServerboundCustomPayloadPacket cpp)) return false;

        CustomPacketPayload payload = cpp.payload();
        if (payload == null) return false;

        String channel;
        try {
            channel = payload.type().id().toString();
        } catch (Throwable t) {
            return false;
        }

        long now = System.currentTimeMillis();

        // канал уже уличён в передаче картинки — глушим весь поток, продлевая окно
        Long until = blockedChannels.get(channel);
        if (until != null) {
            if (now < until) {
                blockedChannels.put(channel, now + BLOCK_WINDOW_MS);
                return true;
            }
            blockedChannels.remove(channel);
        }

        // ищем в полях пакета byte[], похожий на изображение (первый чанк несёт магию формата)
        try {
            for (Field f : payload.getClass().getDeclaredFields()) {
                if (f.getType() != byte[].class) continue;
                f.setAccessible(true);
                if (looksLikeImage((byte[]) f.get(payload))) {
                    blockedChannels.put(channel, now + BLOCK_WINDOW_MS);
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // недоступная рефлексия и т.п. — не блокируем, пусть решают другие слои защиты
        }
        return false;
    }

    // сигнатуры начала файла изображения: JPEG / PNG / GIF
    private static boolean looksLikeImage(byte[] a) {
        if (a == null || a.length < 4) return false;
        // JPEG: FF D8 FF
        if ((a[0] & 0xFF) == 0xFF && (a[1] & 0xFF) == 0xD8 && (a[2] & 0xFF) == 0xFF) return true;
        // PNG: 89 50 4E 47
        if ((a[0] & 0xFF) == 0x89 && a[1] == 'P' && a[2] == 'N' && a[3] == 'G') return true;
        // GIF: 47 49 46
        if (a[0] == 'G' && a[1] == 'I' && a[2] == 'F') return true;
        return false;
    }
}
