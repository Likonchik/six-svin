package ru.levin.modules.render;

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
import ru.levin.events.impl.render.EventRender3D;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;
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
        addSettings(width, source, throughWalls, fade, marker, markerSize,
                hitR, hitG, hitB, missR, missG, missB,
                headshot, hsR, hsG, hsB, lingerHit, lingerMiss);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;

        if (event instanceof EventUpdate) {
            Set<Integer> alive = new HashSet<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!isBullet(e)) continue;
                if (source.is("Только мои") && !ownerIsLocal(e)) continue;
                alive.add(e.getId());
                Vec3 cur = new Vec3(e.getX(), e.getY(), e.getZ());
                Trail tr = trails.get(e.getId());
                if (tr == null) {
                    // первая встреча: засеваем позицией прошлого тика (xOld), чтобы даже пуля, прожившая
                    // 1 тик, дала видимый отрезок, а трасса начиналась у дула, а не на тик позже.
                    tr = new Trail();
                    trails.put(e.getId(), tr);
                    Vec3 prev = new Vec3(e.xOld, e.yOld, e.zOld);
                    if (prev.distanceToSqr(cur) > 1.0e-9) tr.points.addLast(prev);
                }
                if (tr.state == State.FLYING) {
                    // не дублируем совпадающие точки — иначе нулевой сегмент даёт NaN-нормаль в drawLine
                    if (tr.points.isEmpty() || tr.points.getLast().distanceToSqr(cur) > 1.0e-9) {
                        tr.points.addLast(cur);
                    }
                    while (tr.points.size() > MAX_POINTS) tr.points.removeFirst();
                    if (tr.points.size() >= 2) {
                        Vec3 from = nthFromLast(tr, 1); // предпоследняя точка
                        resolveSegment(tr, from, cur, e);
                    }
                }
                tr.last = System.currentTimeMillis();
            }
            // пули, которых уже нет: незавершённые считаем промахом в воздух, затем чистим по linger состояния
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<Integer, Trail>> it = trails.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Trail> en = it.next();
                Trail tr = en.getValue();
                boolean live = alive.contains(en.getKey());
                if (!live && tr.state == State.FLYING) {
                    tr.state = State.MISS;
                    if (!tr.points.isEmpty()) tr.impact = tr.points.getLast();
                }
                long keep = (long) ((tr.state == State.HIT || tr.state == State.HEADSHOT)
                        ? lingerHit.get().floatValue() : lingerMiss.get().floatValue());
                if (!live && now - tr.last > keep) it.remove();
            }
        }

        if (event instanceof EventRender3D) {
            boolean depth = !throughWalls.get();
            float w = width.get().floatValue();
            for (Trail tr : trails.values()) {
                if (tr.points.size() < 2) continue;
                Vec3 prev = null;
                int i = 0, n = tr.points.size();
                for (Vec3 p : tr.points) {
                    if (prev != null && prev.distanceToSqr(p) > 1.0e-9)
                        Render3DUtil.drawLine(prev, p, colorFor(tr.state, i, n), w, depth);
                    prev = p;
                    i++;
                }
                // маркер удара подключается в Task 3
            }
        }
    }

    // получить точку трассы, отстоящую на k от конца (k=0 — последняя, k=1 — предпоследняя)
    private Vec3 nthFromLast(Trail tr, int k) {
        int target = tr.points.size() - 1 - k;
        int i = 0;
        for (Vec3 p : tr.points) {
            if (i == target) return p;
            i++;
        }
        return tr.points.getLast();
    }

    // воспроизводим серверную коллизию на клиенте: блок-клип + перебор сущностей вдоль отрезка.
    // ближайший удар (сущность ближе блока -> HIT/HEADSHOT, иначе блок -> MISS) обрезает трассу.
    private void resolveSegment(Trail tr, Vec3 from, Vec3 to, Entity bullet) {
        // 1) блоки
        BlockHitResult bhr = mc.level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bullet));
        double dBlock = (bhr.getType() != HitResult.Type.MISS) ? from.distanceToSqr(bhr.getLocation()) : Double.MAX_VALUE;

        // 2) сущности вдоль отрезка, но не дальше блочного удара
        LivingEntity bestEntity = null;
        Vec3 bestHit = null;
        double bestDist = dBlock;
        AABB segBox = new AABB(from, to).inflate(0.5);
        List<Entity> near = mc.level.getEntities(bullet, segBox);
        for (Entity ent : near) {
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
            tr.impact = bestHit;
            boolean head = bestHit.y > bestEntity.getY() + bestEntity.getBbHeight() * 0.85;
            tr.state = head ? State.HEADSHOT : State.HIT;
            replaceLast(tr, bestHit);
        } else if (dBlock != Double.MAX_VALUE) {
            tr.impact = bhr.getLocation();
            tr.state = State.MISS;
            replaceLast(tr, bhr.getLocation());
        }
        // иначе чистый сегмент — остаёмся FLYING
    }

    // заменить последнюю точку трассы (обрезка ровно в точке удара)
    private void replaceLast(Trail tr, Vec3 p) {
        if (!tr.points.isEmpty()) tr.points.removeLast();
        tr.points.addLast(p);
    }

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
        int a = fade.get() ? (int) (255f * ((float) i / Math.max(1, n - 1))) : 255;
        return (a << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    // цвет состояния без затухания (для маркера) — используется в Task 3
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

    @Override
    protected void onDisable() {
        trails.clear();
        super.onDisable();
    }

    private enum State { FLYING, MISS, HIT, HEADSHOT }

    private static final class Trail {
        final ArrayDeque<Vec3> points = new ArrayDeque<>();
        long last;
        State state = State.FLYING;
        Vec3 impact = null;
    }
}
