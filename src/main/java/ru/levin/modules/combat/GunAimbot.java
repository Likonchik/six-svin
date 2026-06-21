package ru.levin.modules.combat;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.render.EventRender2D;
import ru.levin.manager.Manager;
import ru.levin.mixin.iface.GameRendererAccessor;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.util.color.ColorUtil;
import ru.levin.util.player.AuraUtil;
import ru.levin.util.player.GCDUtil;
import ru.levin.util.render.RenderUtil;

import java.util.Arrays;
import java.util.Optional;

// TACZ silent-aimbot. The server reads the shooter's getXRot()/getYRot() at shoot-handle time
// (ClientMessagePlayerShoot -> IGunOperator.shoot(entity::getXRot, entity::getYRot, ...)), so
// pointing the *outgoing movement packet* at the target makes the bullet fly true while the
// client view stays free. Direction is solved against TACZ's projectile ballistics — verified
// against the decompiled EntityKineticBullet: spawn origin = half-step interpolated pos + eyeHeight,
// per-tick integration move -> scale(1-friction) -> add(0,-gravity,0), speed/20 blocks/tick.
// Default headshot in TACZ (EntityUtil.getHitResult) is a pure Y-band test: a hit counts as a
// headshot iff hit-height-above-feet is in (getEyeHeight()-0.25, getEyeHeight()+0.25); X/Z are not
// checked. So aiming at getEyeY() (= getY()+getEyeHeight()) is dead-center of that band for players
// AND every default mob. All TACZ access is wrapped in try/catch so the module loads and stays inert
// without TACZ installed (NameTags pattern).
@SuppressWarnings("All")
@FunctionAnnotation(name = "GunAimbot", keywords = {"Аим", "Aimbot", "Стволы"}, desc = "Аимбот для оружия TACZ", type = Type.Combat)
public class GunAimbot extends Function {

    private final MultiSetting targets = new MultiSetting(
            "Цели",
            Arrays.asList("Игроки", "Мобы", "Монстры"),
            new String[]{"Игроки", "Друзья", "Мобы", "Монстры", "Жители"}
    );

    private final ModeSetting mode = new ModeSetting("Активация", "Авто", "Авто", "В прицеле");
    private final ModeSetting point = new ModeSetting("Точка", "Голова", "Голова", "Шея", "Тело");

    private final SliderSetting fov = new SliderSetting("FOV", 30f, 1f, 180f, 1f);
    private final SliderSetting range = new SliderSetting("Дистанция", 60f, 5f, 150f, 1f);
    private final SliderSetting smooth = new SliderSetting("Скорость наведения", 1.0f, 0.1f, 1.0f, 0.05f);
    private final SliderSetting predictFactor = new SliderSetting("Сила упреждения", 1.0f, 0f, 2f, 0.05f);

    private final BooleanSetting drop = new BooleanSetting("Компенсация дропа", true);
    private final BooleanSetting visibleOnly = new BooleanSetting("Только видимых", false);
    // когда голова за блоком — целиться в ближайшую видимую точку хитбокса. ВЫКЛ по умолчанию: целимся
    // строго в голову (стабильно и точно). Включай, если нужно добивать по торчащему телу из-за угла.
    private final BooleanSetting visiblePart = new BooleanSetting("Стрелять по видимой части", false);
    // --- улучшения прицеливания ---
    private final ModeSetting sort = new ModeSetting("Сортировка", "Прицел", "Прицел", "Дистанция", "HP");
    private final BooleanSetting sticky = new BooleanSetting("Залипание на цели", true);
    private final SliderSetting switchFov = new SliderSetting("Порог смены цели", 8f, 0f, 30f, 0.5f, () -> sticky.get());
    private final BooleanSetting hitchance = new BooleanSetting("Хитчанс (умный огонь)", true, () -> this.autoFire.get());
    private final BooleanSetting humanize = new BooleanSetting("Человечность", false);
    private final BooleanSetting backtrack = new BooleanSetting("Бэктрек", false);
    private final SliderSetting backtrackDelay = new SliderSetting("Бэктрек, тики", 3f, 1f, 10f, 1f, () -> backtrack.get());
    private final BooleanSetting humanGcd = new BooleanSetting("Человечный GCD", true);
    private final BooleanSetting fovCircle = new BooleanSetting("Круг FOV", true);

    // circle color: "Тема" follows the active theme / custom palette (ColorUtil.getColorStyle),
    // "Свой" is a user-picked HSB+alpha color.
    private final ModeSetting circleColor = new ModeSetting(() -> fovCircle.get(), "Цвет круга", "Тема", "Тема", "Свой");
    private final SliderSetting hue = new SliderSetting("Оттенок", 0f, 0f, 360f, 1f, () -> fovCircle.get() && circleColor.is("Свой"));
    private final SliderSetting saturation = new SliderSetting("Насыщенность", 100f, 0f, 100f, 1f, () -> fovCircle.get() && circleColor.is("Свой"));
    private final SliderSetting brightness = new SliderSetting("Яркость", 100f, 0f, 100f, 1f, () -> fovCircle.get() && circleColor.is("Свой"));
    private final SliderSetting alpha = new SliderSetting("Прозрачность", 255f, 0f, 255f, 1f, () -> fovCircle.get() && circleColor.is("Свой"));

    // triggerbot: auto-fire once the silent-aim rotation has converged on the target
    private final BooleanSetting autoFire = new BooleanSetting("Автоогонь", false);
    // "По цели" — огонь только при сходимости silent-аима на цели (нужен аим GunAimbot);
    // "Всегда" — держать огонь, пока в руке оружие и есть патроны (цель/сходимость/LoS не нужны).
    private final ModeSetting fireMode = new ModeSetting(() -> autoFire.get(), "Режим огня", "По цели", "По цели", "Всегда");
    private final SliderSetting triggerFov = new SliderSetting("Порог наведения", 3f, 0.5f, 30f, 0.5f, () -> autoFire.get() && fireMode.is("По цели"));
    private final BooleanSetting triggerVisibleOnly = new BooleanSetting("Только видимых (огонь)", true, () -> autoFire.get() && fireMode.is("По цели"));
    private final SliderSetting triggerDelay = new SliderSetting("Задержка огня", 0f, 0f, 300f, 10f, () -> autoFire.get());

    private LivingEntity target = null;
    private boolean aiming = false;
    private float curYaw, curPitch;
    private float[] lastRot;            // last solved {yaw,pitch} — trigger alignment compares curYaw/curPitch to this
    private boolean firedThisHold = false; // SEMI release gate (mirrors TACZ ShootKey.lastTimeShootSuccess)
    private long alignedSinceMs = 0L;   // for the optional fire delay
    private final java.util.ArrayDeque<Vec3> backHistory = new java.util.ArrayDeque<>(); // бэктрек: позиции цели
    private int historyEntityId = -1;
    private final java.util.Random rng = new java.util.Random(); // человечность: микро-джиттер

    public GunAimbot() {
        addSettings(targets, mode, point, sort, sticky, switchFov, fov, range, smooth, predictFactor, drop,
                visibleOnly, visiblePart, backtrack, backtrackDelay, humanize, humanGcd,
                autoFire, fireMode, hitchance, triggerFov, triggerVisibleOnly, triggerDelay,
                fovCircle, circleColor, hue, saturation, brightness, alpha);
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventRender2D r) {
            renderFovCircle(r);
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || player.isDeadOrDying()) {
            aiming = false;
            target = null;
            return;
        }

        if (event instanceof EventUpdate) {
            tick(player);
        } else if (event instanceof EventMotion motion) {
            if (aiming) {
                motion.setYaw(curYaw);
                motion.setPitch(curPitch);
            }
        }
    }

    private void tick(LocalPlayer player) {
        if (!hasGunInHand()) {
            aiming = false;
            target = null;
            return;
        }
        if (mode.is("В прицеле") && !isAiming()) {
            aiming = false;
            target = null;
            return;
        }

        // "Всегда": автоогонь не зависит от цели/баллистики — стреляем, пока есть патроны.
        if (autoFire.get() && fireMode.is("Всегда")) fireAlways(player);

        float[] ballistics = gunBallistics();
        if (ballistics == null) {
            aiming = false;
            target = null;
            return;
        }

        LivingEntity t = findTarget(player);
        if (t == null) {
            aiming = false;
            target = null;
            return;
        }
        target = t;
        recordHistory(t);

        float[] rot = computeAim(player, t, ballistics[0], ballistics[1], ballistics[2]);
        if (rot == null) {
            aiming = false;
            return;
        }

        if (!aiming) {
            curYaw = player.getYRot();
            curPitch = player.getXRot();
        }
        stepToward(rot[0], rot[1]);
        aiming = true;
        lastRot = rot;
        tryAutoFire(player);
    }

    @Override
    protected void onDisable() {
        aiming = false;
        target = null;
        lastRot = null;
        firedThisHold = false;
        alignedSinceMs = 0L;
        super.onDisable();
    }

    // читается Spinbot: true, когда аим активно крутит серверную ротацию (чтобы не воевать за yaw)
    public boolean isLocked() {
        return aiming;
    }

    // читается TargetESP: текущая цель аимбота (null, если цели нет)
    public LivingEntity getTarget() {
        return target;
    }

    // ===================== targeting =====================

    private LivingEntity findTarget(LocalPlayer player) {
        double maxFov = fov.get().doubleValue();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        double bestAngle = Double.MAX_VALUE;

        for (Entity e : Manager.SYNC_MANAGER.getEntities()) {
            if (!(e instanceof LivingEntity le) || !isValidTarget(le)) continue;

            float[] need = anglesTo(player, aimPoint(le));
            double angle = fovAngle(player, need);
            if (angle > maxFov) continue;
            if (visibleOnly.get()) {
                boolean vis = visiblePart.get() ? visibleAimPoint(le) != null : player.hasLineOfSight(le);
                if (!vis) continue;
            }

            double score = scoreOf(le, angle);
            if (score < bestScore) {
                bestScore = score;
                bestAngle = angle;
                best = le;
            }
        }

        // залипание: держим текущую цель, пока она валидна / в FOV / видима, и пока новая не станет лучше
        // по углу больше чем на "Порог смены цели" -> цель меняется только когда реально появилась лучше
        // (или текущая потеряна), а не дёргается между врагами
        if (sticky.get() && target != null && target != best && isValidTarget(target)) {
            double curAngle = fovAngle(player, anglesTo(player, aimPoint(target)));
            boolean curVisible = !visibleOnly.get()
                    || (visiblePart.get() ? visibleAimPoint(target) != null : player.hasLineOfSight(target));
            if (curAngle <= maxFov && curVisible
                    && (best == null || bestAngle >= curAngle - switchFov.get().doubleValue())) {
                return target;
            }
        }
        return best;
    }

    // метрика выбора цели: меньше = лучше
    private double scoreOf(LivingEntity le, double angle) {
        if (sort.is("Дистанция")) return AuraUtil.getDistance(le);
        if (sort.is("HP")) return le.getHealth() + le.getAbsorptionAmount();
        return angle; // "Прицел" — ближайшая к перекрестию
    }

    // ===================== backtrack history =====================

    private void recordHistory(LivingEntity t) {
        if (t.getId() != historyEntityId) {
            backHistory.clear();
            historyEntityId = t.getId();
        }
        backHistory.addFirst(aimPoint(t)); // front = текущая позиция
        while (backHistory.size() > 11) backHistory.removeLast();
    }

    // позиция цели N тиков назад (front=текущая); null если истории не хватает
    private Vec3 backtrackPoint(LivingEntity t) {
        if (t.getId() != historyEntityId) return null;
        int n = (int) backtrackDelay.get().floatValue();
        if (backHistory.size() <= n) return null;
        int i = 0;
        for (Vec3 v : backHistory) {
            if (i++ == n) return v;
        }
        return null;
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == null || e == mc.player || e.isDeadOrDying() || !e.isAlive()) return false;
        if (e instanceof ArmorStand) return false;
        if (AuraUtil.getDistance(e) > range.get().doubleValue()) return false;
        if (Manager.FUNCTION_MANAGER.antiBot.check(e)) return false;

        if (e instanceof Player) {
            if (!targets.get("Игроки")) return false;
            if (!targets.get("Друзья") && Manager.FRIEND_MANAGER.isFriend(e.getName().getString())) return false;
        } else if (e instanceof Villager) {
            if (!targets.get("Жители")) return false;
        } else if (e instanceof Monster) {
            if (!targets.get("Монстры")) return false;
        } else if (e instanceof Mob || e instanceof Animal) {
            if (!targets.get("Мобы")) return false;
        } else {
            return false;
        }
        return true;
    }

    // TACZ default headshot is a pure Y-band test on (getEyeHeight()-0.25, getEyeHeight()+0.25),
    // X/Z ignored -> aim at getEyeY() for a dead-center headshot on players AND every default mob.
    private Vec3 aimPoint(LivingEntity t) {
        Vec3 c = t.getBoundingBox().getCenter();
        double y;
        switch (point.get()) {
            case "Голова" -> y = t.getY() + t.getEyeHeight();
            case "Шея" -> y = t.getY() + t.getEyeHeight() - 0.20;
            default -> y = t.getY() + t.getBbHeight() * 0.50;
        }
        return new Vec3(c.x, y, c.z);
    }

    // видимая точка прицеливания: голова если видна, иначе ближайшая к голове видимая точка хитбокса;
    // null — если цель полностью перекрыта блоками. Так аим не «втыкается» в стену, когда видно тело.
    private Vec3 visibleAimPoint(LivingEntity t) {
        Vec3 head = aimPoint(t);
        if (canSee(head)) return head;
        net.minecraft.world.phys.AABB box = t.getBoundingBox().deflate(0.05);
        double[] xs = {box.minX, (box.minX + box.maxX) * 0.5, box.maxX};
        double[] zs = {box.minZ, (box.minZ + box.maxZ) * 0.5, box.maxZ};
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int yi = 0; yi <= 2; yi++) {
            double y = box.minY + (box.maxY - box.minY) * (yi * 0.5); // ноги / центр / верх
            for (double xx : xs) {
                for (double zz : zs) {
                    Vec3 p = new Vec3(xx, y, zz);
                    if (!canSee(p)) continue;
                    double d = p.distanceToSqr(head);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        return best;
    }

    // есть ли прямая видимость (без блоков) от глаз игрока до точки
    private boolean canSee(Vec3 point) {
        if (mc.player == null || mc.level == null) return false;
        Vec3 eye = mc.player.getEyePosition();
        net.minecraft.world.phys.HitResult hr = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eye, point,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hr.getLocation().distanceToSqr(point) < 0.1;
    }

    // хитчанс: чиста ли траектория пули (от точки вылета до точки прицеливания) от блоков
    private boolean bulletPathClear(LocalPlayer player) {
        if (target == null) return false;
        return pathClear(bulletOrigin(player), aimPoint(target));
    }

    private boolean pathClear(Vec3 from, Vec3 to) {
        if (mc.level == null) return true;
        net.minecraft.world.phys.HitResult hr = mc.level.clip(new net.minecraft.world.level.ClipContext(
                from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hr.getLocation().distanceToSqr(to) < 1.0;
    }

    // ===================== ballistics =====================

    private float[] computeAim(LocalPlayer player, LivingEntity t, double speed, double gravity, double friction) {
        Vec3 eye = bulletOrigin(player);
        // по умолчанию целимся в голову (стабильно); при "Стрелять по видимой части" — в ближайшую
        // видимую точку хитбокса, если голова за блоком
        Vec3 vp = visiblePart.get() ? visibleAimPoint(t) : null;
        Vec3 base = vp != null ? vp : aimPoint(t);

        // бэктрек: целимся в позицию цели N тиков назад (для серверов с лаг-компенсацией). Упреждение
        // при этом отключаем — оно ведёт ВПЕРЁД, а бэктрек НАЗАД (иначе друг друга гасят).
        boolean bt = false;
        if (backtrack.get()) {
            Vec3 past = backtrackPoint(t);
            if (past != null) {
                base = past;
                bt = true;
            }
        }

        Vec3 aimAt = base;
        float[] rot = null;

        // per-tick target velocity (deadzone removes jitter on near-stationary mobs)
        double vx = t.getX() - t.xOld, vy = t.getY() - t.yOld, vz = t.getZ() - t.zOld;
        if (Math.abs(vx) < 1e-3) vx = 0;
        if (Math.abs(vy) < 1e-3) vy = 0;
        if (Math.abs(vz) < 1e-3) vz = 0;
        double f = bt ? 0.0 : predictFactor.get().doubleValue();

        // Prediction is always computed and is distance-driven: tof = horiz / horizontalSpeed,
        // so lead grows automatically with distance. Three passes converge the lead point.
        for (int i = 0; i < 3; i++) {
            rot = solve(eye, aimAt, speed, gravity, friction);

            double vh = speed * Math.cos(Math.toRadians(rot[1]));   // real horizontal speed of the shot
            if (vh < 1e-4) vh = 1e-4;
            double dx = aimAt.x - eye.x, dz = aimAt.z - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            double tof = tof(horiz, vh, friction);

            aimAt = base.add(vx * tof * f, vy * tof * f, vz * tof * f);
        }
        return rot;
    }

    // returns {yaw, pitch} pointing the bullet at `target`, compensating gravity drop (friction-exact)
    private float[] solve(Vec3 eye, Vec3 target, double speed, double gravity, double friction) {
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        double dy = target.y - eye.y;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        double elevation = Math.atan2(dy, horiz); // radians
        if (drop.get() && gravity > 1e-6 && horiz > 1e-3) {
            for (int i = 0; i < 6; i++) {
                double vh = speed * Math.cos(elevation);
                if (vh < 1e-6) break;
                double t = tof(horiz, vh, friction);
                double d = dropOverTicks(gravity, friction, t);
                elevation = Math.atan2(dy + d, horiz);
            }
        }
        float pitch = Mth.clamp((float) -Math.toDegrees(elevation), -89.9f, 89.9f);
        return new float[]{yaw, pitch};
    }

    // ticks to cover `horiz` blocks at horizontal speed `vh` with per-tick friction decay:
    // dist(n) = vh * (1-(1-f)^n)/f  ->  n = ln(1 - horiz*f/vh) / ln(1-f)
    private double tof(double horiz, double vh, double friction) {
        if (friction > 1e-6) {
            double ratio = 1.0 - (horiz * friction) / vh;
            if (ratio > 1e-6) {
                return Math.log(ratio) / Math.log(1.0 - friction);
            }
        }
        return horiz / vh;
    }

    // exact downward drop after `n` ticks given TACZ's order (scale(1-f) then add(-g) each tick):
    // drop(n) = (g/f) * ( n - (1-(1-f)^n)/f )   ->  reduces to 0.5*g*n^2 as f->0
    private double dropOverTicks(double gravity, double friction, double n) {
        if (friction > 1e-6) {
            return (gravity / friction) * (n - (1.0 - Math.pow(1.0 - friction, n)) / friction);
        }
        return 0.5 * gravity * n * n;
    }

    // matches EntityKineticBullet spawn: half-step interpolated horizontal midpoint + eyeHeight
    private Vec3 bulletOrigin(LocalPlayer p) {
        double ox = p.xOld + (p.getX() - p.xOld) / 2.0;
        double oy = p.yOld + (p.getY() - p.yOld) / 2.0 + p.getEyeHeight();
        double oz = p.zOld + (p.getZ() - p.zOld) / 2.0;
        return new Vec3(ox, oy, oz);
    }

    // ===================== rotation apply =====================

    private void stepToward(float targetYaw, float targetPitch) {
        float factor = smooth.get().floatValue();
        float dYaw = Mth.wrapDegrees(targetYaw - curYaw);
        float dPitch = targetPitch - curPitch;

        float ny, np;
        if (humanize.get()) {
            // ease-out: чем ближе к цели, тем мельче шаг (человечное торможение) + лёгкий микро-джиттер
            float dist = Math.abs(dYaw) + Math.abs(dPitch);
            float eased = factor * (0.6f + 0.4f * Math.min(1f, dist / 30f));
            ny = curYaw + dYaw * eased + (float) ((rng.nextDouble() - 0.5) * 0.6);
            np = curPitch + dPitch * eased + (float) ((rng.nextDouble() - 0.5) * 0.4);
        } else {
            ny = curYaw + dYaw * factor;
            np = curPitch + dPitch * factor;
        }

        if (humanGcd.get()) {
            float gcd = GCDUtil.getGCDValue();
            if (gcd > 0f) {
                // round (not floor) so sub-gcd residuals still tick over instead of stalling the aim
                ny = curYaw + Math.round((ny - curYaw) / gcd) * gcd;
                np = curPitch + Math.round((np - curPitch) / gcd) * gcd;
            }
        }
        curYaw = ny;
        curPitch = Mth.clamp(np, -89.9f, 89.9f);
    }

    private float[] anglesTo(LocalPlayer player, Vec3 point) {
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[]{yaw, pitch};
    }

    private double fovAngle(LocalPlayer player, float[] need) {
        float yawD = Mth.wrapDegrees(need[0] - player.getYRot());
        float pitchD = need[1] - player.getXRot();
        return Math.sqrt(yawD * yawD + pitchD * pitchD);
    }

    // ===================== FOV circle =====================

    private void renderFovCircle(EventRender2D e) {
        if (!fovCircle.get() || mc.player == null || mc.options.hideGui) return;
        Camera camera = mc.getEntityRenderDispatcher().camera;
        if (camera == null) return;

        float partial = e.getDeltatick().getGameTimeDeltaPartialTick(true);
        double gameFov = ((GameRendererAccessor) mc.gameRenderer).invokeGetFov(camera, partial, true);

        float cx = mc.getWindow().getGuiScaledWidth() / 2f;
        float cy = mc.getWindow().getGuiScaledHeight() / 2f;
        float halfH = mc.getWindow().getGuiScaledHeight() * 0.5f;

        // angle (half-cone, deg) -> pixel radius, mirroring VectorUtil's projection (vertical FOV)
        float factor = (float) (halfH / Math.tan(Math.toRadians(gameFov) * 0.5));
        double ang = Math.min(fov.get().doubleValue(), 89.0); // keep tan() finite/positive
        float radius = factor * (float) Math.tan(Math.toRadians(ang));

        RenderUtil.drawCircleBorder(e.getMatrixStack(), cx, cy, radius * 2f, 1.5f, circleColor());
    }

    // theme color (active theme / custom palette) or a user-picked HSB+alpha color
    private int circleColor() {
        if (circleColor.is("Свой")) {
            int rgb = java.awt.Color.HSBtoRGB(
                    hue.get().floatValue() / 360f,
                    saturation.get().floatValue() / 100f,
                    brightness.get().floatValue() / 100f);
            int a = (int) alpha.get().floatValue();
            return (a << 24) | (rgb & 0x00FFFFFF);
        }
        return ColorUtil.getColorStyle(90, 255);
    }

    // ===================== triggerbot =====================

    // Fires from tick() (LocalPlayer.tick HEAD) right after aiming=true, so the same-tick sendPosition
    // /EventMotion carries curYaw/curPitch onto the outgoing rotation packet — the server resolves the
    // bullet down the aimed angle. TACZ's shoot() self-gates cooldown/ammo/reload/draw/bolt/sprint, so
    // we only add the on-target/LoS/ammo gate + a SEMI release gate (else SEMI guns go full-auto).
    private void tryAutoFire(LocalPlayer player) {
        // в режиме "Всегда" огонь идёт через fireAlways() — здесь ничего не делаем
        if (!autoFire.get() || fireMode.is("Всегда")) {
            if (!autoFire.get()) {
                firedThisHold = false;
                alignedSinceMs = 0L;
            }
            return;
        }
        if (!isAligned() || !hasLosForTrigger(player) || !hasAmmo()) {
            firedThisHold = false;
            alignedSinceMs = 0L;
            return;
        }
        // хитчанс: не стрелять, если пулю по траектории перекрывает блок
        if (hitchance.get() && !bulletPathClear(player)) return;

        long delay = (long) triggerDelay.get().floatValue();
        if (delay > 0L) {
            long now = System.currentTimeMillis();
            if (alignedSinceMs == 0L) alignedSinceMs = now;
            if (now - alignedSinceMs < delay) return;
        }

        // стреляем каждый тик, пока наведены: реальную скорострельность гейтит сам TACZ (кулдаун по RPM).
        // Раньше для SEMI стоял гейт «один выстрел на наведение» -> DMR/пистолеты/полуавтоматы делали
        // ровно один выстрел и замолкали. Теперь они палят с максимальной для них скорострельностью.
        fireGun();
    }

    // "Всегда"-огонь: стреляем, пока в руке оружие и есть патроны; цель/сходимость/LoS не нужны.
    // triggerDelay = минимальный интервал между выстрелами (alignedSinceMs = время посл. выстрела);
    // при 0 — каждый тик, реальную скорострельность гейтит сам TACZ (cooldown). SEMI при этом идёт как
    // быстрый полуавтомат — ровно то, что ожидается от AutoFire.
    private void fireAlways(LocalPlayer player) {
        if (!hasAmmo()) return;
        long delay = (long) triggerDelay.get().floatValue();
        long now = System.currentTimeMillis();
        if (delay > 0L && now - alignedSinceMs < delay) return;
        if (fireGun()) alignedSinceMs = now;
    }

    // converged when the stepped silent-aim rotation (the bytes EventMotion sends) is within the
    // trigger threshold of the freshly-solved aim — a true on-target test incl. drop compensation
    private boolean isAligned() {
        if (target == null || !aiming || lastRot == null) return false;
        float dYaw = Mth.wrapDegrees(lastRot[0] - curYaw);
        float dPitch = lastRot[1] - curPitch;
        return Math.sqrt(dYaw * dYaw + dPitch * dPitch) <= triggerFov.get().doubleValue();
    }

    private boolean hasLosForTrigger(LocalPlayer player) {
        if (!triggerVisibleOnly.get()) return true;
        if (target == null) return false;
        return visiblePart.get() ? visibleAimPoint(target) != null : player.hasLineOfSight(target);
    }

    private boolean isAutoFireMode(LocalPlayer player) {
        try {
            ItemStack stack = player.getMainHandItem();
            com.tacz.guns.api.item.IGun iGun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if (iGun == null) return true;
            com.tacz.guns.api.item.gun.FireMode fm = iGun.getFireMode(stack);
            // BURST: let TACZ schedule the burst internally; treat like auto for our gate
            return fm == com.tacz.guns.api.item.gun.FireMode.AUTO
                    || fm == com.tacz.guns.api.item.gun.FireMode.BURST;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private boolean hasAmmo() {
        try {
            ItemStack stack = mc.player.getMainHandItem();
            com.tacz.guns.api.item.IGun iGun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            return iGun != null && iGun.getCurrentAmmoCount(stack) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // fires exactly one shot via TACZ's self-gating client operator; true iff accepted (SUCCESS)
    private boolean fireGun() {
        try {
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator op =
                    com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            return op.shoot() == com.tacz.guns.api.entity.ShootResult.SUCCESS;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ===================== TACZ bridge (graceful) =====================

    private boolean hasGunInHand() {
        try {
            ItemStack stack = mc.player.getMainHandItem();
            return !stack.isEmpty() && com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isAiming() {
        try {
            com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator op =
                    com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
            return op.getClientAimingProgress(partial) > 0.5f;
        } catch (Throwable ignored) {
            return false;
        }
    }

    // returns {speedPerTick(blocks/tick), gravity(blocks/tick^2), friction(per tick)} or null.
    // Effective muzzle speed must match what EntityKineticBullet actually uses, NOT the raw JSON:
    //   processedSpeed = cache(AMMO_SPEED) * GLOBAL_BULLET_SPEED_MODIFIER / 20   (blocks/tick)
    // cache(AMMO_SPEED) folds in fire-mode adjust + attachment modifiers; GLOBAL_BULLET_SPEED_MODIFIER
    // defaults to 2.0 (the dominant correction — using raw getSpeed()/20 launched the model at half the
    // real speed, doubling time-of-flight and over-compensating drop at range). Gravity/friction are
    // pass-through (no attachment modifier) so the raw BulletData values are exact.
    private float[] gunBallistics() {
        try {
            LocalPlayer player = mc.player;
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) return null;
            com.tacz.guns.api.item.IGun iGun = com.tacz.guns.api.item.IGun.getIGunOrNull(stack);
            if (iGun == null) return null;
            ResourceLocation gunId = iGun.getGunId(stack);
            Optional<com.tacz.guns.client.resource.index.ClientGunIndex> idx =
                    com.tacz.guns.api.TimelessAPI.getClientGunIndex(gunId);
            if (idx.isEmpty()) return null;
            com.tacz.guns.resource.pojo.data.gun.GunData gunData = idx.get().getGunData();
            if (gunData == null) return null;
            com.tacz.guns.resource.pojo.data.gun.BulletData bullet = gunData.getBulletData();
            if (bullet == null) return null;

            // base muzzle speed = attachment/fire-mode-modified ammo speed (falls back to raw JSON speed)
            float baseSpeed = bullet.getSpeed();
            try {
                com.tacz.guns.resource.modifier.AttachmentCacheProperty cache =
                        com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getCacheProperty();
                if (cache != null) {
                    Float cached = cache.getCache(com.tacz.guns.api.GunProperties.AMMO_SPEED);
                    if (cached != null && cached > 0f) baseSpeed = cached;
                }
            } catch (Throwable ignored) {}

            double globalMul;
            try {
                globalMul = com.tacz.guns.config.common.AmmoConfig.GLOBAL_BULLET_SPEED_MODIFIER.get();
            } catch (Throwable ignored) {
                globalMul = 2.0; // TACZ default
            }

            float speedPerTick = (float) (baseSpeed * globalMul / 20.0);
            float gravity = bullet.getGravity();
            float friction = bullet.getFriction();
            if (speedPerTick <= 0f) return null;
            return new float[]{speedPerTick, gravity, friction};
        } catch (Throwable ignored) {
            return null;
        }
    }
}
