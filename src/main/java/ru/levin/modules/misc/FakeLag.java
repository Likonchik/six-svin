package ru.levin.modules.misc;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import ru.levin.events.Event;
import ru.levin.events.impl.EventPacket;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.move.NetworkUtils;

// FakeLag — раздувает измеряемый сервером пинг, задерживая исходящий keep-alive (тест-вектор лаг-компенсации
// TACZ). Сервер меряет latency() по round-trip keep-alive; TACZ HitboxHelper (SERVER_HITBOX_LATENCY_FIX=true)
// отматывает хитбокс жертвы на ping = floor(latency/1000*20 + 0.5) тиков в её РЕАЛЬНОЕ прошлое (cap 20 тиков).
// Раздув latency -> сервер бьёт по позиции цели «в прошлом»; в паре с GunAimbot (бэктрек «По пингу») аим
// наводится ровно в эту историческую точку.
//
// Механика: keep-alive ответ уходит через connection.send -> EventPacket(SEND). Мы его придерживаем на
// delayMs и отправляем тихо (sendSilentPacket, без повторного EventPacket). Keep-alive редкий (~15 c), так
// что latency() ползёт к realRTT+delayMs по EWMA за несколько циклов — грубо, но cap отмотки и так 1 c.
// delayMs держим заметно ниже keep-alive timeout сервера (по умолчанию ~15-30 c), 1500 мс безопасно.
//
// Замечание для АЧ: инвариант — keep-alive должен отвечаться сразу; задержка ответа + раздутый/нестабильный
// пинг при ровном движении = сигнатура fake-lag.
@FunctionAnnotation(name = "FakeLag", keywords = {"FakeLag", "Пинг", "Лагсвитч"}, desc = "Раздувает пинг (задержка keep-alive) под лаг-компенсацию TACZ", type = Type.Misc)
public class FakeLag extends Function {

    private final SliderSetting delayMs = new SliderSetting("Задержка пинга, мс", 600, 100, 1500, 50);

    private Packet<?> heldKeepAlive = null;
    private long heldAt = 0L;

    public FakeLag() {
        addSettings(delayMs);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null) return;

        if (event instanceof EventPacket pe) {
            if (pe.isSendPacket() && pe.getPacket() instanceof ServerboundKeepAlivePacket) {
                // если предыдущий keep-alive ещё придержан — отпускаем его, затем держим новый
                flushHeld();
                heldKeepAlive = pe.getPacket();
                heldAt = System.currentTimeMillis();
                pe.setCancel(true);
            }
            return;
        }

        if (event instanceof EventUpdate) {
            if (heldKeepAlive != null
                    && System.currentTimeMillis() - heldAt >= (long) delayMs.get().floatValue()) {
                flushHeld();
            }
        }
    }

    private void flushHeld() {
        if (heldKeepAlive == null || mc.player == null) return;
        Packet<?> p = heldKeepAlive;
        heldKeepAlive = null;
        NetworkUtils.sendSilentPacket(p);
    }

    @Override
    protected void onDisable() {
        flushHeld();
        heldKeepAlive = null;
        super.onDisable();
    }
}
