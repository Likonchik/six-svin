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
    private final BooleanSetting humanGcd = new BooleanSetting("Человечный GCD", true);
    private final BooleanSetting fovCircle = new BooleanSetting("Круг FOV", true);

    private LivingEntity target = null;
    private boolean aiming = false;
    private float curYaw, curPitch;

    public GunAimbot() {
        addSettings(targets, mode, point, fov, range, smooth, predictFactor, drop, visibleOnly, humanGcd, fovCircle);
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
    }

    @Override
    protected void onDisable() {
        aiming = false;
        target = null;
        super.onDisable();
    }

    // ===================== targeting =====================

    private LivingEntity findTarget(LocalPlayer player) {
        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        double maxFov = fov.get().doubleValue();

        for (Entity e : Manager.SYNC_MANAGER.getEntities()) {
            if (!(e instanceof LivingEntity le) || !isValidTarget(le)) continue;

            Vec3 aim = aimPoint(le);
            float[] need = anglesTo(player, aim);
            double angle = fovAngle(player, need);
            if (angle > maxFov) continue;
            if (visibleOnly.get() && !player.hasLineOfSight(le)) continue;

            if (angle < bestAngle) {
                bestAngle = angle;
                best = le;
            }
        }
        return best;
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

    // ===================== ballistics =====================

    private float[] computeAim(LocalPlayer player, LivingEntity t, double speed, double gravity, double friction) {
        Vec3 eye = bulletOrigin(player);
        Vec3 base = aimPoint(t);
        Vec3 aimAt = base;
        float[] rot = null;

        // per-tick target velocity (deadzone removes jitter on near-stationary mobs)
        double vx = t.getX() - t.xOld, vy = t.getY() - t.yOld, vz = t.getZ() - t.zOld;
        if (Math.abs(vx) < 1e-3) vx = 0;
        if (Math.abs(vy) < 1e-3) vy = 0;
        if (Math.abs(vz) < 1e-3) vz = 0;
        double f = predictFactor.get().doubleValue();

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

        float ny = curYaw + dYaw * factor;
        float np = curPitch + dPitch * factor;

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

        int color = ColorUtil.getColorStyle(90, 255);
        RenderUtil.drawCircleBorder(e.getMatrixStack(), cx, cy, radius * 2f, 1.5f, color);
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

    // returns {speedPerTick(blocks/tick), gravity(blocks/tick^2), friction(per tick)} or null
    private float[] gunBallistics() {
        try {
            ItemStack stack = mc.player.getMainHandItem();
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
            float speedPerTick = bullet.getSpeed() / 20f;
            float gravity = bullet.getGravity();
            float friction = bullet.getFriction();
            if (speedPerTick <= 0f) return null;
            return new float[]{speedPerTick, gravity, friction};
        } catch (Throwable ignored) {
            return null;
        }
    }
}
