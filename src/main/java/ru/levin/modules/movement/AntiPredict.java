package ru.levin.modules.movement;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.move.MoveUtil;

import java.util.Random;

// Анти-упреждение: добавляет нелинейность в горизонтальное движение (осциллирующий боковой рывок со случайным
// периодом), чтобы ломать ЛИНЕЙНОЕ упреждение чужих стрелков/аимботов — они экстраполируют pos + velocity*tof,
// а дёрганая velocity делает упреждённую точку мимо. Боковой импульс добавляется к deltaMovement в tick HEAD
// (ДО travel): moveRelative прибавит input, move() сдвинет на (dm+side+input) -> рывок полностью входит в
// позицию этого тика и уходит на сервер. Эффективен на средней/дальней дистанции (большое время полёта пули
// TACZ); на близи пуля долетает за 1-2 тика и увернуться нельзя. Сила мала по умолчанию, чтобы не сбивать
// собственное перемещение к цели.
@FunctionAnnotation(name = "AntiPredict", keywords = {"АнтиАим", "Джиттер", "Анти-упреждение", "Уклонение"},
        desc = "Ломает упреждение чужих стрелков нелинейным движением", type = Type.Move)
public class AntiPredict extends Function {

    private final SliderSetting strength = new SliderSetting("Сила рывка", 0.12f, 0.02f, 0.5f, 0.01f);
    private final SliderSetting periodMin = new SliderSetting("Период мин, тики", 2f, 1f, 10f, 1f);
    private final SliderSetting periodMax = new SliderSetting("Период макс, тики", 5f, 1f, 20f, 1f);
    private final BooleanSetting onlyMoving = new BooleanSetting("Только при движении", true);
    private final BooleanSetting onlyCombat = new BooleanSetting("Только при враге рядом", true);
    private final SliderSetting combatRange = new SliderSetting("Радиус врага, бл", 40f, 5f, 80f, 1f, () -> this.onlyCombat.get());

    private final Random rng = new Random();
    private int sign = 1;
    private int ticksLeft = 0;
    private double applied = 0; // текущая сглаженная боковая скорость (стабильность собственного движения)

    public AntiPredict() {
        addSettings(strength, periodMin, periodMax, onlyMoving, onlyCombat, combatRange);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc.player == null || mc.level == null) return;
        if (onlyMoving.get() && !MoveUtil.isMoving()) { reset(); return; }
        if (onlyCombat.get() && !enemyNear()) { reset(); return; }

        // меняем сторону каждые rand(min..max) тиков — случайный период, чтобы под него нельзя было подстроиться
        if (ticksLeft <= 0) {
            sign = -sign;
            int lo = (int) periodMin.get().floatValue();
            int hi = Math.max(lo, (int) periodMax.get().floatValue());
            ticksLeft = lo + (hi > lo ? rng.nextInt(hi - lo + 1) : 0);
        }
        ticksLeft--;

        // боковой вектор — перпендикуляр НАПРАВЛЕНИЮ ДВИЖЕНИЯ (а не взгляда): так рывок реально качает вектор
        // скорости, ломая линейное упреждение даже при страйфе. При стоянии берём перпендикуляр взгляда.
        Vec3 dm = mc.player.getDeltaMovement();
        Vec3 dir = new Vec3(dm.x, 0, dm.z);
        if (dir.lengthSqr() < 1.0e-5) {
            double rad = Math.toRadians(mc.player.getYRot());
            dir = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        }
        dir = dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);

        // плавный подход к целевой боковой скорости вместо мгновенного скачка — наш путь стабилен,
        // но velocity всё равно непрерывно меняет сторону => упреждённая точка стрелка уходит мимо
        double target = strength.get().floatValue() * sign;
        applied += (target - applied) * 0.5;
        mc.player.setDeltaMovement(dm.x + side.x * applied, dm.y, dm.z + side.z * applied);
    }

    private void reset() {
        ticksLeft = 0;
        applied = 0;
    }

    private boolean enemyNear() {
        double rSq = combatRange.get().doubleValue() * combatRange.get().doubleValue();
        for (var e : mc.level.entitiesForRendering()) {
            if (e == mc.player) continue;
            if (e instanceof Player p && p.isAlive() && p.distanceToSqr(mc.player) <= rSq) return true;
        }
        return false;
    }

    @Override
    protected void onDisable() {
        reset();
        sign = 1;
        super.onDisable();
    }
}
