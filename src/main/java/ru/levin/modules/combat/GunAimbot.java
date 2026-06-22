package ru.levin.modules.combat;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
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

    // не выбирать/не стрелять по игрокам своей scoreboard-команды (союзникам)
    private final BooleanSetting teamCheck = new BooleanSetting("Не бить команду", true);

    private final ModeSetting mode = new ModeSetting("Активация", "Авто", "Авто", "В прицеле");
    private final ModeSetting point = new ModeSetting("Точка", "Голова", "Голова", "Шея", "Тело");

    private final SliderSetting fov = new SliderSetting("FOV", 30f, 1f, 180f, 1f);
    private final SliderSetting range = new SliderSetting("Дистанция", 60f, 5f, 150f, 1f);
    private final SliderSetting smooth = new SliderSetting("Скорость наведения", 1.0f, 0.1f, 1.0f, 0.05f);
    private final SliderSetting predictFactor = new SliderSetting("Сила упреждения", 1.0f, 0f, 2f, 0.05f);

    private final BooleanSetting drop = new BooleanSetting("Компенсация дропа", true);
    // компенсация наследуемой пулей скорости стрелка (solve) — фикс сноса при стрейфе/беге 🟢
    private final BooleanSetting moveComp = new BooleanSetting("Компенсация движения", true);
    // занулить разброс собственных пуль аима (MixinKineticBulletSpread) — pinpoint без NoSpread 🔵
    private final BooleanSetting zeroSpread = new BooleanSetting("Свой разброс = 0", true);
    private final BooleanSetting visibleOnly = new BooleanSetting("Только видимых", false);
    // когда голова за блоком — целиться в ближайшую видимую точку хитбокса. ВЫКЛ по умолчанию: целимся
    // строго в голову (стабильно и точно). Включай, если нужно добивать по торчащему телу из-за угла.
    private final BooleanSetting visiblePart = new BooleanSetting("Стрелять по видимой части", false);
    // --- улучшения прицеливания ---
    private final ModeSetting sort = new ModeSetting("Сортировка", "Прицел", "Прицел", "Дистанция", "HP");
    private final BooleanSetting sticky = new BooleanSetting("Залипание на цели", true);
    private final SliderSetting switchFov = new SliderSetting("Порог смены цели", 8f, 0f, 30f, 0.5f, () -> sticky.get());
    private final BooleanSetting hitchance = new BooleanSetting("Хитчанс (умный огонь)", true, () -> this.autoFire.get());
    // минимальный шанс попасть (Monte-Carlo по конусу разброса), ниже которого не стреляем
    private final SliderSetting hitChanceMin = new SliderSetting("Мин. хитчанс, %", 60f, 0f, 100f, 1f, () -> this.autoFire.get() && this.hitchance.get());
    private final BooleanSetting humanize = new BooleanSetting("Человечность", false);
    private final BooleanSetting backtrack = new BooleanSetting("Бэктрек", false);
    // "Ручной" — фиксированное число тиков отмотки; "По пингу" — выводим из СВОЕГО пинга по формуле TACZ
    // HitboxHelper (ping = floor(latency/1000*20+0.5)), чтобы целиться ровно в ту историческую позицию, в
    // которую сервер откатывает хитбокс жертвы (лаг-компенсация). Пара к модулю FakeLag (раздув пинга).
    private final ModeSetting backtrackMode = new ModeSetting(() -> backtrack.get(), "Бэктрек-режим", "Ручной", "Ручной", "По пингу");
    private final SliderSetting backtrackDelay = new SliderSetting("Бэктрек, тики", 3f, 1f, 20f, 1f, () -> backtrack.get() && backtrackMode.is("Ручной"));
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
    // TACZ блокит выстрел во время спринта: LocalPlayerShoot.shoot() -> IS_SPRINTING, пока getSynSprintTime()>0.
    // Серверный sprintTimeS (макс = gunData.sprintTime, дефолт 0.2с) копится пока спринтишь и убывает в реальном
    // времени, когда НЕ спринтишь -> на агро-пике автоогонь молчит. Гасим спринт, пока автоогонь наведён, чтобы
    // таймер успел убыть к выстрелу. Образец — AttackAura (STOP_SPRINTING + setSprinting(false)).
    private final BooleanSetting noSprintFire = new BooleanSetting("Гасить спринт для огня", true, () -> this.autoFire.get());

    private LivingEntity target = null;
    private boolean aiming = false;
    private float curYaw, curPitch;
    private float[] lastRot;            // last solved {yaw,pitch} — trigger alignment compares curYaw/curPitch to this
    private boolean firedThisHold = false; // SEMI release gate (mirrors TACZ ShootKey.lastTimeShootSuccess)
    private long alignedSinceMs = 0L;   // for the optional fire delay
    // выстрел откладывается до flushPendingShot() (вызывается ПОСЛЕ отправки move-пакета с наведённой
    // ротацией). Иначе shoot-пакет уходит из tick() HEAD раньше move-пакета и сервер берёт ротацию прошлого
    // тика -> первый выстрел (особенно SEMI/DMR) летит в перекрестие, а не в цель. См. flushPendingShot().
    private boolean pendingShot = false;
    // баллистика, посчитанная на tick HEAD; финальный solve откладывается до EventMotion (после движения),
    // где состояние игрока (bulletOrigin/getDeltaMovement) совпадает с серверной точкой рождения пули.
    private float[] pendingBallistics = null;
    private Vec3 lastAimAt = null;      // мировая точка, куда реально наведён аим (с упреждением) — для хитчанса
    private final java.util.ArrayDeque<Vec3> backHistory = new java.util.ArrayDeque<>(); // бэктрек: позиции цели
    private int historyEntityId = -1;
    private final java.util.Random rng = new java.util.Random(); // человечность: микро-джиттер

    public GunAimbot() {
        addSettings(targets, teamCheck, mode, point, sort, sticky, switchFov, fov, range, smooth, predictFactor, moveComp, drop, zeroSpread,
                visibleOnly, visiblePart, backtrack, backtrackMode, backtrackDelay, humanize, humanGcd,
                autoFire, fireMode, hitchance, hitChanceMin, triggerFov, triggerVisibleOnly, triggerDelay, noSprintFire,
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
            pendingBallistics = null;
            return;
        }

        if (event instanceof EventUpdate) {
            tick(player);
        } else if (event instanceof EventMotion motion) {
            // Финальный solve выполняется ЗДЕСЬ — на HEAD sendPosition, т.е. ПОСЛЕ super.tick()/движения этого
            // тика. На этот момент bulletOrigin() и getDeltaMovement() уже соответствуют текущему тику — ровно
            // тому состоянию игрока, из которого сервер родит EntityKineticBullet (half-step origin + наследование
            // скорости стрелка). Раньше solve шёл на tick HEAD (ДО движения) -> точка вылета отставала на один тик
            // скорости стрелка, давая боковой снос при стрейфе/пике (мисс в атаке, точность в позиционке).
            aimOnMotion(player, motion);
        }
    }

    // tick HEAD (EventUpdate): выбор цели, история бэктрека, гейты состояния и баллистика. Сам геометрический
    // solve + сглаживание + триггер отложены до EventMotion (aimOnMotion), где состояние игрока уже пост-движение
    // и совпадает с точкой рождения пули на сервере. pendingBallistics != null = «цель готова, целимся в EventMotion».
    private void tick(LocalPlayer player) {
        pendingBallistics = null;
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
        pendingBallistics = ballistics;
    }

    // Финальное наведение — считается на EventMotion (после движения этого тика). На этот момент состояние игрока
    // (bulletOrigin/getDeltaMovement) идентично тому, из которого сервер заспавнит EntityKineticBullet, поэтому
    // решённый угол точен и при движении (стрейф/пик), а не только в покое. aiming выставляется ДО того, как
    // Spinbot (Movement, обрабатывает тот же EventMotion позже по списку) прочитает isLocked() и уступит аиму.
    private void aimOnMotion(LocalPlayer player, EventMotion motion) {
        if (pendingBallistics == null || target == null) {
            aiming = false;
            return;
        }
        float[] rot = computeAim(player, target, pendingBallistics[0], pendingBallistics[1], pendingBallistics[2]);
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
        motion.setYaw(curYaw);
        motion.setPitch(curPitch);
        // режим "По цели": гасим спринт, пока наведены, чтобы серверный sprintTimeS убыл к моменту выстрела
        // ("Всегда" гасит в fireAlways — там цели/сходимости нет).
        if (autoFire.get() && !fireMode.is("Всегда")) killSprintForFire(player);
        tryAutoFire(player);
    }

    // Гасит спринт, пока автоогонь хочет стрелять. TACZ shoot() отклоняет выстрел (IS_SPRINTING), пока серверный
    // getSynSprintTime()>0; этот таймер убывает только когда игрок НЕ спринтит, поэтому шлём STOP_SPRINTING заранее
    // и каждый тик наведения (после первого тика isSprinting=false -> спам прекращается сам). Восстановление спринта
    // не форсим: как только перестаём целиться, спринт вернёт сам игрок / AutoSprint.
    private void killSprintForFire(LocalPlayer player) {
        if (!noSprintFire.get() || !player.isSprinting()) return;
        player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        player.setSprinting(false);
    }

    @Override
    protected void onDisable() {
        aiming = false;
        target = null;
        lastRot = null;
        lastAimAt = null;
        firedThisHold = false;
        alignedSinceMs = 0L;
        pendingShot = false;
        pendingBallistics = null;
        super.onDisable();
    }

    // читается Spinbot: true, когда аим активно крутит серверную ротацию (чтобы не воевать за yaw)
    public boolean isLocked() {
        return aiming;
    }

    // читается MixinKineticBulletSpread: занулить разброс собственных пуль, пока аим активно целится
    public boolean wantsZeroSpread() {
        return state && aiming && zeroSpread.get();
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
        while (backHistory.size() > 21) backHistory.removeLast(); // до 20 тиков отмотки (TACZ SAVE_TICK=20)
    }

    // позиция цели N тиков назад (front=текущая); null если истории не хватает
    private Vec3 backtrackPoint(LivingEntity t) {
        if (t.getId() != historyEntityId) return null;
        int n = backtrackTicks();
        if (n < 1 || backHistory.size() <= n) return null;
        int i = 0;
        for (Vec3 v : backHistory) {
            if (i++ == n) return v;
        }
        return null;
    }

    // число тиков отмотки: ручное значение или выведенное из СВОЕГО пинга по формуле TACZ HitboxHelper
    // (ping = floor(latency/1000*20 + 0.5)), клампим под серверный cap SAVE_TICK=20.
    private int backtrackTicks() {
        if (backtrackMode.is("пингу")) {
            return Mth.clamp(pingTicks(), 1, 20);
        }
        return (int) backtrackDelay.get().floatValue();
    }

    private int pingTicks() {
        try {
            net.minecraft.client.multiplayer.PlayerInfo info =
                    mc.getConnection().getPlayerInfo(mc.player.getUUID());
            int latency = info != null ? info.getLatency() : 0;
            return Mth.floor(latency / 1000.0 * 20.0 + 0.5);
        } catch (Throwable ignored) {
            return (int) backtrackDelay.get().floatValue();
        }
    }

    // сглаженная per-tick скорость цели по backHistory (фронт=текущая); fallback на одну тиковую дельту.
    // backHistory заполняется recordHistory() каждый тик ДО computeAim(), элемент [k] = позиция k тиков назад.
    private Vec3 smoothVelocity(LivingEntity t) {
        if (t.getId() == historyEntityId && backHistory.size() >= 2) {
            int k = Math.min(3, backHistory.size() - 1);
            Vec3 now = null, past = null;
            int i = 0;
            for (Vec3 v : backHistory) {
                if (i == 0) now = v;
                if (i == k) { past = v; break; }
                i++;
            }
            if (now != null && past != null && k > 0) {
                return new Vec3((now.x - past.x) / k, (now.y - past.y) / k, (now.z - past.z) / k);
            }
        }
        return new Vec3(t.getX() - t.xOld, t.getY() - t.yOld, t.getZ() - t.zOld);
    }

    // готово ли упреждение к автоогню: окно сглаживания скорости заполнено (>=4 тика истории) ИЛИ цель
    // практически стоит (лид ~0 при любом шуме). До этого по ДВИЖУЩЕЙСЯ цели автоогонь ждёт — иначе
    // первые выстрелы уходят с лидом от шумной одно-тиковой интерполяционной скорости (промахи на старте
    // по бегущей цели). Стоячую цель не задерживаем — по ней лида нет, первый выстрел и так точен.
    private boolean predictionReady(LivingEntity t) {
        if (backHistory.size() >= 4) return true;
        double dx, dz;
        int span;
        if (t.getId() == historyEntityId && backHistory.size() >= 2) {
            Vec3 now = backHistory.getFirst(), past = backHistory.getLast();
            dx = now.x - past.x;
            dz = now.z - past.z;
            span = backHistory.size() - 1;
        } else {
            dx = t.getX() - t.xOld;
            dz = t.getZ() - t.zOld;
            span = 1;
        }
        double perTickSq = (dx * dx + dz * dz) / (double) (span * span);
        return perTickSq < 2.5e-3; // < ~0.05 бл/тик -> считаем стоячей, стреляем сразу
    }

    private boolean isValidTarget(LivingEntity e) {
        if (e == null || e == mc.player || e.isDeadOrDying() || !e.isAlive()) return false;
        if (e instanceof ArmorStand) return false;
        if (AuraUtil.getDistance(e) > range.get().doubleValue()) return false;
        if (Manager.FUNCTION_MANAGER.antiBot.check(e)) return false;
        // scoreboard teams: не бить игроков своей команды (союзников)
        if (teamCheck.get() && mc.player != null && mc.player.isAlliedTo(e)) return false;

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

    // видимая точка прицеливания: голова если видна, иначе ближайшая к голове видимая точка тела. Плотная
    // сетка ПО ВСЕМ X/Z (3×3, ВКЛЮЧАЯ УГЛЫ) × тонкий вертикальный проход — ловит и узкий слой над укрытием,
    // и УГЛОВОЕ выглядывание (старая «крест»-схема теряла углы хитбокса -> аим не видел торчащий угол).
    // Найденную кромочную точку сдвигаем чуть ВНУТРЬ тела (запас под спред/квантование), если она видима.
    // null — если цель полностью перекрыта блоками: тогда аим не «втыкается» в стену.
    private Vec3 visibleAimPoint(LivingEntity t) {
        Vec3 head = aimPoint(t);
        if (canSee(head)) return head;
        net.minecraft.world.phys.AABB box = t.getBoundingBox().deflate(0.03); // лёгкий инсет: точка внутри хитбокса, но тонкую кромку не теряем
        double cx = (box.minX + box.maxX) * 0.5;
        double cz = (box.minZ + box.maxZ) * 0.5;
        double[] xs = {box.minX, cx, box.maxX}; // полная 3×3 сетка — углы хитбокса теперь сэмплируются
        double[] zs = {box.minZ, cz, box.maxZ};
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        final int steps = 14; // тонкий вертикальный шаг — ловит узкий видимый слой тела над укрытием
        for (int i = 0; i <= steps; i++) {
            double y = box.maxY - (box.maxY - box.minY) * ((double) i / steps); // сверху (голова) вниз (ноги)
            for (double xx : xs) {
                for (double zz : zs) {
                    Vec3 p = new Vec3(xx, y, zz);
                    if (!canSee(p)) continue;
                    double d = p.distanceToSqr(head); // ближе к голове = больше урона / естественнее
                    if (d < bestDist) {
                        bestDist = d;
                        best = p;
                    }
                }
            }
        }
        if (best == null) return null;
        // запас под спред/квантование угла: тянем точку чуть ВНУТРЬ хитбокса (к центру), чтобы пуля била в
        // тело, а не клипала кромку. Сдвинутую берём только если она тоже видима — иначе остаёмся на кромке.
        Vec3 toCenter = box.getCenter().subtract(best);
        if (toCenter.lengthSqr() > 1.0e-6) {
            // 0.2 (было 0.13): глубже уводим точку от кромки силуэта = больше запас от угла укрытия под
            // дроп/квантование. Берём только если inset тоже видим строгим canSee, иначе остаёмся на кромке.
            Vec3 inset = best.add(toCenter.normalize().scale(0.2));
            if (canSee(inset)) return inset;
        }
        return best;
    }

    // есть ли прямая видимость (без блоков) от ТОЧКИ ВЫЛЕТА ПУЛИ до точки. Origin = bulletOrigin, а не глаз:
    // occlusion обязан совпадать с реальной траекторией пули (ствол чуть смещён от камеры), иначе аим может
    // считать точку видимой, хотя пулю по дороге перекрывает блок (или наоборот).
    private boolean canSee(Vec3 point) {
        if (mc.player == null || mc.level == null) return false;
        Vec3 eye = bulletOrigin(mc.player);
        net.minecraft.world.phys.HitResult hr = mc.level.clip(new net.minecraft.world.level.ClipContext(
                eye, point,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        // блок на пути ДО точки = НЕ видно. Допуск 0.0025 (0.05 бл) только под deflate(0.03)/float-погрешность
        // и точки, лежащие на поверхности (нога на полу, бок у стены — пуля там всё равно бьёт цель).
        // Раньше допуск был 0.1 (0.316 бл): луч мог упереться в УГОЛ укрытия за ~0.1–0.3 бл до кромочной точки,
        // но это считалось «видно» -> аим выбирал кромку силуэта на углу блока -> пуля клипала внутренний угол.
        return hr.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hr.getLocation().distanceToSqr(point) < 0.0025;
    }

    // хитчанс: чиста ли траектория пули (от точки вылета до РЕАЛЬНО наведённой точки, с упреждением/видимой
    // частью) от блоков. Используем lastAimAt, а не голову — иначе при visiblePart/упреждении гейт врёт.
    private boolean bulletPathClear(LocalPlayer player) {
        if (target == null) return false;
        Vec3 to = lastAimAt != null ? lastAimAt : aimPoint(target);
        return pathClear(bulletOrigin(player), to);
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

    // ===================== hitchance (Monte-Carlo) =====================

    // вероятность попасть по цели текущим выстрелом, % [0..100]. Симулируем N выстрелов с РЕАЛЬНЫМ конусом
    // разброса TACZ: дефолтные пушки используют ванильную Projectile.shoot -> triangle(±inacc·π/180) по осям
    // нормализованного направления. Пускаем лучи от точки вылета и считаем долю, попавшую в хитбокс цели.
    // Наш пинпоинт зануляет разброс -> лазер -> 100%.
    private double hitChance(LocalPlayer player) {
        if (target == null) return 0.0;
        Vec3 origin = bulletOrigin(player);
        Vec3 aimPt = lastAimAt != null ? lastAimAt : aimPoint(target);
        Vec3 aim = aimPt.subtract(origin);
        if (aim.lengthSqr() < 1.0e-6) return 0.0;
        aim = aim.normalize();

        double inacc = effectiveInaccuracyDeg(player); // градусы; 0 при пинпоинте
        // тестируем по ПРЕДСКАЗАННОМУ хитбоксу: aim наведён на lastAimAt (с упреждением/бэктреком), поэтому
        // двигаем текущий бокс на тот же вектор (lastAimAt − базовая точка цели), иначе по движущейся цели
        // лучи вокруг упреждённого направления мажут мимо ТЕКУЩЕГО бокса -> хитчанс всегда ~0.
        net.minecraft.world.phys.AABB box = target.getBoundingBox().move(aimPt.subtract(aimPoint(target)));
        double range = origin.distanceTo(box.getCenter()) + 4.0;

        // лазер (разброс ~0): попадание определяется одной центральной трассой
        if (inacc <= 1.0e-4) {
            Vec3 end = origin.add(aim.scale(range));
            return box.clip(origin, end).isPresent() ? 100.0 : 0.0;
        }

        final int n = 100;
        final double sigma = 0.0172275 * inacc; // (π/180)·inacc — как в Projectile.shoot
        int hits = 0;
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(aim.x + tri(sigma), aim.y + tri(sigma), aim.z + tri(sigma));
            if (d.lengthSqr() < 1.0e-6) continue;
            Vec3 end = origin.add(d.normalize().scale(range));
            if (box.clip(origin, end).isPresent()) hits++;
        }
        return 100.0 * hits / n;
    }

    // треугольное распределение ±s (как RandomSource.triangle(0, s) в ваниле): s·(rand − rand)
    private double tri(double s) {
        return s * (rng.nextDouble() - rng.nextDouble());
    }

    // эффективный разброс пули в ГРАДУСАХ с учётом наших модулей: наш пинпоинт (свой zeroSpread или GunNoSpread
    // «Пинпоинт») -> 0; «Инстант» форсит тип AIM. Значение — из gun INACCURACY-карты по текущему InaccuracyType
    // (как у TACZ на сервере), фоллбэк на дефолты типа (STAND=5, MOVE=5.75, SNEAK=3.5, LIE=2.5, AIM=0.15).
    private double effectiveInaccuracyDeg(LocalPlayer player) {
        try {
            boolean pinpoint = wantsZeroSpread()
                    || (Manager.FUNCTION_MANAGER.gunNoSpread != null
                        && Manager.FUNCTION_MANAGER.gunNoSpread.forcePinpoint());
            if (pinpoint) return 0.0;

            boolean forceAim = Manager.FUNCTION_MANAGER.gunNoSpread != null
                    && Manager.FUNCTION_MANAGER.gunNoSpread.forceAimSpread();
            com.tacz.guns.resource.pojo.data.gun.InaccuracyType type = forceAim
                    ? com.tacz.guns.resource.pojo.data.gun.InaccuracyType.AIM
                    : com.tacz.guns.resource.pojo.data.gun.InaccuracyType.getInaccuracyType(player);

            Float v = null;
            try {
                com.tacz.guns.resource.modifier.AttachmentCacheProperty cache =
                        com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player).getCacheProperty();
                if (cache != null) {
                    java.util.Map<com.tacz.guns.resource.pojo.data.gun.InaccuracyType, Float> map =
                            cache.getCache(com.tacz.guns.api.GunProperties.INACCURACY);
                    if (map != null) v = map.get(type);
                }
            } catch (Throwable ignored) {}
            if (v == null) {
                java.util.Map<com.tacz.guns.resource.pojo.data.gun.InaccuracyType, Float> def =
                        com.tacz.guns.resource.pojo.data.gun.InaccuracyType.getDefaultInaccuracy();
                if (def != null) v = def.get(type);
            }
            return v != null ? Math.max(0.0, v) : 5.0;
        } catch (Throwable ignored) {
            return 5.0;
        }
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

        // сглаженная per-tick target velocity (deadzone removes jitter on near-stationary mobs)
        Vec3 tvel = smoothVelocity(t);
        double vx = tvel.x, vy = tvel.y, vz = tvel.z;
        if (Math.abs(vx) < 1e-3) vx = 0;
        if (Math.abs(vy) < 1e-3) vy = 0;
        if (Math.abs(vz) < 1e-3) vz = 0;
        double f = bt ? 0.0 : predictFactor.get().doubleValue();

        // скорость стрелка, наследуемая пулей (shootFromRotation): горизонталь всегда, вертикаль только
        // в воздухе. Её вклад в смещение за n тиков затухает с трением -> множитель S(n), НЕ tof.
        boolean comp = moveComp.get();
        Vec3 sv = player.getDeltaMovement();
        double svx = sv.x, svz = sv.z;
        double svy = player.onGround() ? 0.0 : sv.y;

        // Prediction is always computed and is distance-driven: tof = horiz / horizontalSpeed,
        // so lead grows automatically with distance. Passes converge the lead point.
        for (int i = 0; i < 5; i++) {
            rot = solve(eye, aimAt, speed, gravity, friction);
            lastAimAt = aimAt; // точка, относительно которой решён rot — реально наведённая (для хитчанса)

            double vh = speed * Math.cos(Math.toRadians(rot[1]));   // real horizontal speed of the shot
            if (vh < 1e-4) vh = 1e-4;
            double dx = aimAt.x - eye.x, dz = aimAt.z - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            double tof = tof(horiz, vh, friction);

            // лид цели: цель движется ~линейно tof тиков (множитель tof)
            aimAt = base.add(vx * tof * f, vy * tof * f, vz * tof * f);
            // компенсация инерции пули от скорости стрелка (множитель S(n))
            if (comp) {
                double s = sFactor(friction, tof);
                aimAt = aimAt.subtract(svx * s, svy * s, svz * s);
            }
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

    // множитель пройденного пути за n тиков при трении f: S(n) = (1-(1-f)^n)/f (прямой, обратный к tof)
    private double sFactor(double friction, double n) {
        if (friction > 1e-6) return (1.0 - Math.pow(1.0 - friction, n)) / friction;
        return n;
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
        // прогрев упреждения: не стрелять по движущейся цели, пока окно сглаживания скорости не набралось
        // (иначе первые выстрелы уходят с лидом от шумной одно-тиковой скорости). Стоячую не задерживаем.
        if (target != null && !predictionReady(target)) return;
        // хитчанс: симулируем конус разброса и не стреляем, если шанс попасть ниже порога. Блок на
        // центральном пути = разом 0 (быстрый гейт до симуляции).
        if (hitchance.get()) {
            if (!bulletPathClear(player)) return;
            if (hitChance(player) < hitChanceMin.get().doubleValue()) return;
        }

        long delay = (long) triggerDelay.get().floatValue();
        if (delay > 0L) {
            long now = System.currentTimeMillis();
            if (alignedSinceMs == 0L) alignedSinceMs = now;
            if (now - alignedSinceMs < delay) return;
        }

        // ставим выстрел в очередь каждый тик, пока наведены: реальную скорострельность гейтит сам TACZ
        // (кулдаун по RPM). Раньше для SEMI стоял гейт «один выстрел на наведение» -> DMR/пистолеты делали
        // ровно один выстрел и замолкали. Сам выстрел уходит из flushPendingShot() ПОСЛЕ move-пакета.
        pendingShot = true;
    }

    // "Всегда"-огонь: стреляем, пока в руке оружие и есть патроны; цель/сходимость/LoS не нужны.
    // triggerDelay = минимальный интервал между выстрелами (alignedSinceMs = время посл. выстрела);
    // при 0 — каждый тик, реальную скорострельность гейтит сам TACZ (cooldown). SEMI при этом идёт как
    // быстрый полуавтомат — ровно то, что ожидается от AutoFire.
    private void fireAlways(LocalPlayer player) {
        if (!hasAmmo()) return;
        killSprintForFire(player); // режим "Всегда": гасим спринт, пока есть патроны (см. killSprintForFire)
        long delay = (long) triggerDelay.get().floatValue();
        long now = System.currentTimeMillis();
        if (delay > 0L && now - alignedSinceMs < delay) return;
        pendingShot = true;
        alignedSinceMs = now;
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

    // Вызывается из MixinClientPlayerEntity ПОСЛЕ отправки move-пакета (afterSendMovementPackets): к этому
    // моменту наведённая ротация (curYaw/curPitch) уже ушла на сервер ЭТИМ ЖЕ тиком через EventMotion,
    // поэтому сервер разрешит shoot-пакет относительно НАВЕДЁННОЙ ротации, а не прошлого тика. Это чинит
    // «аим наводится, но пуля летит в перекрестие» — особенно первый выстрел SEMI/DMR.
    public void flushPendingShot() {
        if (!pendingShot) return;
        pendingShot = false;
        if (!state || mc.player == null) return;
        fireGun();
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
