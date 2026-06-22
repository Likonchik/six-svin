package ru.levin.modules.movement;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;

// Bullet Dodge: ищет чужую пулю TACZ (EntityKineticBullet с владельцем != ты), чья прямолинейная траектория
// пройдёт близко к твоему хитбоксу в ближайшие N тиков, и даёт боковой импульс перпендикулярно её горизонтальной
// скорости (опц. прыжок). Импульс добавляется к deltaMovement в tick HEAD -> входит в движение этого тика.
// Пулю детектим по имени класса + ванильный Projectile.getOwner() (без жёсткой зависимости от TACZ, как
// BulletTracers). ЧЕСТНО: эффективно только на средней/дальней дистанции — на близи пуля долетает за 1-2 тика
// и среагировать не успеть; траектория аппроксимируется прямой (дроп/трение за N тиков малы).
@FunctionAnnotation(name = "BulletDodge", keywords = {"Уклонение", "Додж", "Пули", "Dodge"},
        desc = "Уклоняется от летящих в тебя пуль TACZ", type = Type.Move)
public class BulletDodge extends Function {

    private final SliderSetting strength = new SliderSetting("Сила уклона", 0.5f, 0.05f, 1.2f, 0.05f);
    private final SliderSetting radius = new SliderSetting("Радиус реакции, бл", 1.2f, 0.3f, 3f, 0.1f);
    private final SliderSetting lookahead = new SliderSetting("Упреждение, тики", 10f, 1f, 20f, 1f);
    private final BooleanSetting jump = new BooleanSetting("Прыжок", false);

    public BulletDodge() {
        addSettings(strength, radius, lookahead, jump);
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc.player == null || mc.level == null) return;

        Vec3 me = new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getBbHeight() * 0.5, mc.player.getZ());
        double hit = radius.get().doubleValue() + mc.player.getBbWidth() * 0.5;
        double hitSq = hit * hit;
        double look = lookahead.get().doubleValue();

        Vec3 dodge = null;
        double bestT = Double.MAX_VALUE;

        for (Entity e : mc.level.entitiesForRendering()) {
            if (!isBullet(e) || ownerIsLocalOrNull(e)) continue;
            Vec3 bp = e.position();
            Vec3 bv = e.getDeltaMovement();
            double bvLenSq = bv.lengthSqr();
            if (bvLenSq < 1.0e-6) continue;

            // время ближайшего сближения прямой пули (bp + bv*t) с моей позицией; t в тиках вперёд.
            // считаем по СКОРОСТИ пули (без нашей) — иначе уклон сам себе сдвигает прогноз и дёргается
            double t = me.subtract(bp).dot(bv) / bvLenSq;
            if (t < 0 || t > look) continue;                 // уже пролетела / ещё слишком далеко по времени
            Vec3 closest = bp.add(bv.scale(t));
            Vec3 miss = me.subtract(closest);                // вектор промаха = перпендикуляр траектории до меня
            if (miss.lengthSqr() > hitSq) continue;          // не заденет
            if (t >= bestT) continue;                         // реагируем на ближайшую по времени угрозу
            bestT = t;

            // escape-направление = горизонтальная часть вектора промаха (быстрее всего растит дистанцию от линии);
            // при лобовом выстреле (miss≈0) уходим перпендикуляром к горизонтали пули
            Vec3 esc = new Vec3(miss.x, 0, miss.z);
            if (esc.lengthSqr() < 1.0e-4) {
                Vec3 flat = new Vec3(bv.x, 0, bv.z);
                if (flat.lengthSqr() < 1.0e-6) flat = new Vec3(1, 0, 0);
                esc = new Vec3(-flat.z, 0, flat.x);
            }
            dodge = esc.normalize();
        }

        if (dodge == null) return;

        // чем меньше тиков до сближения, тем сильнее рывок (до 2x базовой) — иначе на близи не успеть выйти из-под пули
        double amp = strength.get().doubleValue();
        amp *= 1.0 + Math.max(0.0, (look - bestT) / look);
        Vec3 dm = mc.player.getDeltaMovement();
        double ny = (jump.get() && mc.player.onGround()) ? 0.42 : dm.y;
        mc.player.setDeltaMovement(dm.x + dodge.x * amp, ny, dm.z + dodge.z * amp);
    }

    private boolean isBullet(Entity e) {
        return e != null && e.getClass().getName().contains("KineticBullet");
    }

    // снаряд без владельца / со своим владельцем не трогаем (не уклоняемся от собственных пуль)
    private boolean ownerIsLocalOrNull(Entity e) {
        if (!(e instanceof Projectile p)) return true;
        Entity owner = p.getOwner();
        return owner == null || owner == mc.player;
    }
}
