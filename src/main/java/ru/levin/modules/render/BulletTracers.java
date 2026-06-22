package ru.levin.modules.render;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.move.EventEntitySpawn;
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.manager.Manager;
import ru.levin.util.render.Render3DUtil;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Трассеры пуль TACZ: показывают траекторию полёта пули. Каждый тик накапливаем позиции всех летящих
// пуль (EntityKineticBullet) и в EventRender3D рисуем по ним ломаную — видно дугу с учётом дропа.
// Исход (попал/мискнул/хедшот) детектим САМИ на клиенте (TACZ считает коллизии только на сервере):
// каждый тик трассируем новый сегмент пули по блокам и сущностям, обрезаем трассу в точке удара
// и красим её цветом состояния. Пулю детектим по имени класса, владельца — через ванильный
// Projectile.getOwner(): без жёстких ссылок на TACZ, модуль безвреден без мода.
@SuppressWarnings("All")
@FunctionAnnotation(name = "BulletTracers", keywords = {"Трассеры", "Tracers", "Пули"}, desc = "Показывает траекторию полёта пуль TACZ", type = Type.Render)
public class BulletTracers extends Function {

    private final SliderSetting width = new SliderSetting("Толщина", 2f, 0.5f, 5f, 0.1f);
    private final ModeSetting source = new ModeSetting("Источник", "Все", "Все", "Только мои");
    private final BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", true);
    private final BooleanSetting fade = new BooleanSetting("Затухание хвоста", true);

    // трасса своих пуль стартует от дула (с учётом ViewModel-оружия), а не от точки спавна пули
    private final BooleanSetting fromMuzzle = new BooleanSetting("Из дула (свои)", false, "Трасса своих пуль из дула с учётом ViewModel");
    private final SliderSetting muzzleForward = new SliderSetting("Вынос дула", 1.0f, 0f, 3f, 0.05f, () -> this.fromMuzzle.get()).withDesc("Длина выноса точки дула вперёд");

    private final BooleanSetting marker = new BooleanSetting("Маркер удара", false);
    private final SliderSetting markerSize = new SliderSetting("Размер маркера", 0.25f, 0.1f, 1f, 0.05f, () -> this.marker.get());

    private final SliderSetting hitR = new SliderSetting("Попадание R", 0f, 0f, 255f, 1f);
    private final SliderSetting hitG = new SliderSetting("Попадание G", 255f, 0f, 255f, 1f);
    private final SliderSetting hitB = new SliderSetting("Попадание B", 0f, 0f, 255f, 1f);

    private final SliderSetting missR = new SliderSetting("Промах R", 200f, 0f, 255f, 1f);
    private final SliderSetting missG = new SliderSetting("Промах G", 200f, 0f, 255f, 1f);
    private final SliderSetting missB = new SliderSetting("Промах B", 200f, 0f, 255f, 1f);

    private final BooleanSetting headshot = new BooleanSetting("Хедшот", false);
    private final SliderSetting hsR = new SliderSetting("Хедшот R", 255f, 0f, 255f, 1f, () -> this.headshot.get());
    private final SliderSetting hsG = new SliderSetting("Хедшот G", 0f, 0f, 255f, 1f, () -> this.headshot.get());
    private final SliderSetting hsB = new SliderSetting("Хедшот B", 0f, 0f, 255f, 1f, () -> this.headshot.get());

    private final SliderSetting lingerHit = new SliderSetting("Держать попавшие, мс", 2000f, 0f, 5000f, 100f);
    private final SliderSetting lingerMiss = new SliderSetting("Держать промахи, мс", 800f, 0f, 5000f, 100f);

    private static final int MAX_POINTS = 80;

    private final Map<Integer, Trail> trails = new HashMap<>();

    public BulletTracers() {
        addSettings(width, source, throughWalls, fade, fromMuzzle, muzzleForward, marker, markerSize,
                hitR, hitG, hitB, missR, missG, missB,
                headshot, hsR, hsG, hsB, lingerHit, lingerMiss);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;

        if (event instanceof EventEntitySpawn ees) {
            Entity e = ees.getEntity();
            if (isBullet(e) && (!source.is("Только мои") || ownerIsLocal(e))) {
                processBullet(e);
            }
        }

        if (event instanceof EventUpdate) {
            Set<Integer> alive = new HashSet<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!isBullet(e)) continue;
                if (source.is("Только мои") && !ownerIsLocal(e)) continue;
                alive.add(e.getId());
                processBullet(e);
            }

            long now = System.currentTimeMillis();
            Iterator<Map.Entry<Integer, Trail>> it = trails.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Trail> en = it.next();
                Trail tr = en.getValue();
                boolean live = alive.contains(en.getKey());
                if (!live && tr.state == State.FLYING) {
                    if (tr.predictedImpact != null) {
                        tr.state = tr.predictedState;
                        tr.impact = tr.predictedImpact;
                        if (tr.points.isEmpty() || tr.points.getLast().distanceToSqr(tr.impact) > 1.0e-9) {
                            tr.points.addLast(tr.impact);
                        }
                    } else {
                        tr.state = State.MISS;
                        if (!tr.points.isEmpty()) tr.impact = tr.points.getLast();
                    }
                }
                long keep = (long) ((tr.state == State.HIT || tr.state == State.HEADSHOT)
                        ? lingerHit.get().floatValue() : lingerMiss.get().floatValue());
                if (!live && now - tr.last > keep) it.remove();
            }
        }

        if (event instanceof EventRender3D e3d) {
            boolean depth = !throughWalls.get();
            float w = width.get().floatValue();
            float partialTicks = e3d.getDeltatick().getGameTimeDeltaPartialTick(true);
            
            for (Map.Entry<Integer, Trail> entry : trails.entrySet()) {
                Trail tr = entry.getValue();
                if (tr.points.size() < 2 && tr.state != State.FLYING) continue;
                
                boolean isFlying = (tr.state == State.FLYING);
                Entity bullet = isFlying ? mc.level.getEntity(entry.getKey()) : null;
                Vec3 interp = null;
                if (bullet != null) {
                    interp = new Vec3(
                        Mth.lerp(partialTicks, bullet.xo, bullet.getX()),
                        Mth.lerp(partialTicks, bullet.yo, bullet.getY()),
                        Mth.lerp(partialTicks, bullet.zo, bullet.getZ())
                    );
                }

                Vec3 prev = null;
                int i = 0, n = tr.points.size();
                Iterator<Vec3> it = tr.points.iterator();
                while (it.hasNext()) {
                    Vec3 p = it.next();
                    boolean isLast = !it.hasNext();
                    
                    if (isLast && interp != null) {
                        p = interp;
                    }
                    
                    if (prev != null && prev.distanceToSqr(p) > 1.0e-9) {
                        int c1 = colorFor(tr.state, i - 1, n);
                        int c2 = colorFor(tr.state, i, n);
                        Render3DUtil.drawLine(null, prev, p, c1, c2, w, depth);
                    }
                    prev = p;
                    i++;
                }
                
                if (marker.get() && tr.impact != null) {
                    double s = markerSize.get().floatValue() / 2.0;
                    AABB box = new AABB(
                            tr.impact.x - s, tr.impact.y - s, tr.impact.z - s,
                            tr.impact.x + s, tr.impact.y + s, tr.impact.z + s);
                    Render3DUtil.drawBox(box, solidColor(tr.state), w, true, true, depth);
                }
            }
        }
    }



    // одиночный raytrace отрезка: ближайший удар по сущности (HIT/HEADSHOT) ближе блока, иначе блок (MISS),
    // либо null если отрезок чист. Воспроизводит серверную коллизию TACZ на клиенте (ванильные API).
    private Hit traceSegment(Vec3 from, Vec3 to, Entity bullet) {
        if (from.distanceToSqr(to) < 1.0e-9) return null;
        // 1) блоки
        BlockHitResult bhr = mc.level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet));
        double bestDist = (bhr.getType() != HitResult.Type.MISS) ? from.distanceToSqr(bhr.getLocation()) : Double.MAX_VALUE;

        // 2) сущности вдоль отрезка, но не дальше блочного удара
        LivingEntity bestEntity = null;
        Vec3 bestHit = null;
        AABB segBox = new AABB(from, to).inflate(0.5);
        for (Entity ent : mc.level.getEntities(bullet, segBox)) {
            if (!(ent instanceof LivingEntity le)) continue;
            if (le == mc.player && ownerIsLocal(bullet)) continue; // не цепляем стрелка-себя
            Optional<Vec3> hit = le.getBoundingBox().inflate(0.3).clip(from, to);
            if (hit.isEmpty()) continue;
            double d = from.distanceToSqr(hit.get());
            if (d < bestDist) {
                bestDist = d;
                bestEntity = le;
                bestHit = hit.get();
            }
        }

        if (bestEntity != null) {
            boolean head = bestHit.y > bestEntity.getY() + bestEntity.getBbHeight() * 0.85;
            return new Hit(bestHit, head ? State.HEADSHOT : State.HIT);
        }
        if (bhr.getType() != HitResult.Type.MISS) {
            return new Hit(bhr.getLocation(), State.MISS);
        }
        return null; // чистый сегмент — остаёмся FLYING
    }



    // заменить последнюю точку трассы (обрезка ровно в точке удара)


    // ARGB цвета точки трассы: RGB по состоянию (FLYING рисуем как промах), alpha вдоль хвоста по fade
    private int colorFor(State state, int i, int n) {
        int r, g, b;
        switch (state) {
            case HIT:
                r = hitR.get().intValue(); g = hitG.get().intValue(); b = hitB.get().intValue();
                break;
            case HEADSHOT:
                if (headshot.get()) { r = hsR.get().intValue(); g = hsG.get().intValue(); b = hsB.get().intValue(); }
                else { r = hitR.get().intValue(); g = hitG.get().intValue(); b = hitB.get().intValue(); }
                break;
            default: // MISS и FLYING
                r = missR.get().intValue(); g = missG.get().intValue(); b = missB.get().intValue();
        }
        float progress = Math.max(0f, Math.min(1f, (float) i / Math.max(1, n - 1)));
        int a = fade.get() ? (int) (255f * progress) : 255;
        return (a << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    // цвет состояния без затухания (для маркера удара)
    private int solidColor(State state) {
        return 0xFF000000 | (colorFor(state, 1, 2) & 0x00FFFFFF);
    }

    // детект пули TACZ по имени класса — без жёсткой ссылки (graceful без мода)
    private boolean isBullet(Entity e) {
        return e != null && e.getClass().getName().contains("KineticBullet");
    }

    // владелец пули == локальный игрок (ванильный Projectile API, без ссылки на TACZ)
    private boolean ownerIsLocal(Entity e) {
        return e instanceof Projectile p && p.getOwner() == mc.player;
    }

    // мировая точка дула от первого лица: глаз + смещение оружия (ViewModel gun_*) в видовой системе + вынос вперёд.
    // Приближение (видовое смещение -> мир): -z = вперёд, +x = вправо, +y = вверх; точную длину дула задаёт «Вынос дула».
    private Vec3 computeMuzzle() {
        if (mc.player == null) return null;
        Vec3 eye = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getViewVector(1f).normalize();
        Vec3 up = mc.player.getUpVector(1f).normalize();
        Vec3 rightv = look.cross(up).normalize();
        double gx = 0, gy = 0, gz = 0;
        ViewModel vm = Manager.FUNCTION_MANAGER != null ? Manager.FUNCTION_MANAGER.viewModel : null;
        if (vm != null && vm.weaponOn()) {
            gx = vm.gun_x.get().doubleValue();
            gy = vm.gun_y.get().doubleValue();
            gz = vm.gun_z.get().doubleValue();
        }
        return eye.add(rightv.scale(gx)).add(up.scale(gy)).add(look.scale(-gz))
                .add(look.scale(muzzleForward.get().doubleValue()));
    }

    private void processBullet(Entity e) {
        Vec3 cur = new Vec3(e.getX(), e.getY(), e.getZ());
        Trail tr = trails.get(e.getId());
        if (tr == null) {
            tr = new Trail();
            trails.put(e.getId(), tr);
            // старт трассы: для своих пуль от первого лица — от дула (с учётом ViewModel-оружия), иначе от прошлой позиции
            Vec3 muzzle = null;
            if (fromMuzzle.get() && ownerIsLocal(e) && mc.options.getCameraType().isFirstPerson()) {
                muzzle = computeMuzzle();
            }
            if (muzzle != null) {
                tr.points.addLast(muzzle);
            } else {
                Vec3 prev = new Vec3(e.xOld, e.yOld, e.zOld);
                if (prev.distanceToSqr(cur) > 1.0e-9 && prev.distanceToSqr(cur) < 10000) {
                    tr.points.addLast(prev);
                }
            }
        }
        
        tr.predictedState = State.FLYING;
        tr.predictedImpact = null;

        if (tr.state == State.FLYING) {
            if (tr.points.isEmpty() || tr.points.getLast().distanceToSqr(cur) > 1.0e-9) {
                tr.points.addLast(cur);
            }
            while (tr.points.size() > MAX_POINTS) tr.points.removeFirst();
            
            Hit h = traceSegment(cur, cur.add(e.getDeltaMovement()), e);
            if (h != null) {
                tr.predictedState = h.state();
                tr.predictedImpact = h.pos();
            }
        }
        tr.last = System.currentTimeMillis();
    }

    @Override
    protected void onDisable() {
        trails.clear();
        super.onDisable();
    }

    private enum State { FLYING, MISS, HIT, HEADSHOT }

    // результат raytrace одного отрезка: точка удара + его тип
    private record Hit(Vec3 pos, State state) {}

    private static final class Trail {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        long last;
        State state = State.FLYING;
        Vec3 impact = null;
        State predictedState = State.FLYING;
        Vec3 predictedImpact = null;
    }
}
