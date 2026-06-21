package ru.levin.modules.movement;

import net.minecraft.util.Mth;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.combat.AttackAura;
import ru.levin.modules.combat.GunAimbot;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;

// Серверный (silent) спинбот: крутит ТОЛЬКО серверный yaw через EventMotion. Твоя камера стоит на месте,
// а на сервер каждый тик уходит новый угол (вращение видят сервер/другие игроки) — нагрузка на
// rotation/aim-проверки античита. ВАЖНО: на 20 тиках/с быстрый silent-спин для наблюдателей всегда
// немного ступенчатый (тело догоняет голову) — это предел протокола, поэтому дефолт умеренный.
// Камеру специально НЕ крутим (это была прошлая «видимая» версия — откатили по просьбе).
@FunctionAnnotation(name = "Spinbot", keywords = {"Spin", "Вращение", "Крутилка"}, desc = "Серверное вращение yaw (камера на месте)", type = Type.Move)
public class Spinbot extends Function {

    // умеренный дефолт: 30 был «медленно», 120 — «криво»/рывками. Выше = быстрее, но для других ступенчатее.
    private final SliderSetting speed = new SliderSetting("Скорость, °/тик", 45, 1, 120, 1);
    private final ModeSetting direction = new ModeSetting("Направление", "Вправо", "Вправо", "Влево");
    private final BooleanSetting yieldToAim = new BooleanSetting("Уступать аиму", true);

    private float spinYaw;

    public Spinbot() {
        addSettings(speed, direction, yieldToAim);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventMotion motion)) return;
        if (mc.player == null) return;
        // не воевать за ротацию с боевыми модулями (они тоже пишут EventMotion.setYaw)
        if (yieldToAim.get() && combatRotating()) return;

        float step = speed.get().floatValue();
        spinYaw = Mth.wrapDegrees(spinYaw + (direction.is("Вправо") ? step : -step));
        motion.setYaw(spinYaw); // только серверный угол; визуал восстанавливается миксином -> камера стоит
    }

    private boolean combatRotating() {
        GunAimbot ga = Manager.FUNCTION_MANAGER.gunAimbot;
        if (ga != null && ga.state && ga.isLocked()) return true;
        AttackAura aa = Manager.FUNCTION_MANAGER.attackAura;
        return aa != null && aa.state && aa.target != null;
    }

    @Override
    protected void onEnable() {
        spinYaw = mc.player != null ? mc.player.getYRot() : 0f;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        spinYaw = 0f;
        super.onDisable();
    }
}
