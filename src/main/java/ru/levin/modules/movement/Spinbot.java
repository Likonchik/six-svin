package ru.levin.modules.movement;

import net.minecraft.util.Mth;
import ru.levin.events.Event;
import ru.levin.events.impl.input.EventKeyBoard;
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
import ru.levin.util.move.MoveUtil;

// Серверный (silent) спинбот: крутит серверные углы через EventMotion. Твоя камера стоит на месте.
@FunctionAnnotation(name = "Spinbot", keywords = {"Spin", "Вращение", "Крутилка"}, desc = "Серверное вращение yaw/pitch (камера на месте)", type = Type.Move)
public class Spinbot extends Function {

    public final ModeSetting yawMode = new ModeSetting("Режим Yaw", "Вращение", "Вращение", "Джиттер", "Статичный", "Случайный");
    public final SliderSetting speed = new SliderSetting("Скорость Yaw, °/тик", 45, 1, 180, 1, () -> yawMode.is("Вращение"));
    public final ModeSetting direction = new ModeSetting(() -> yawMode.is("Вращение"), "Направление", "Вправо", "Вправо", "Влево");
    public final SliderSetting yawOffset = new SliderSetting("Смещение Yaw, °", 180, -180, 180, 1, () -> yawMode.is("Статичный") || yawMode.is("Джиттер"));
    public final SliderSetting jitterRange = new SliderSetting("Диапазон джиттера, °", 30, 1, 180, 1, () -> yawMode.is("Джиттер"));

    public final ModeSetting pitchMode = new ModeSetting("Режим Pitch", "Нет", "Нет", "Вниз", "Вверх", "Ноль", "Статичный", "Вращение", "Джиттер");
    public final SliderSetting pitchSpeed = new SliderSetting("Скорость Pitch, °/тик", 15, 1, 90, 1, () -> pitchMode.is("Вращение"));
    public final SliderSetting pitchOffset = new SliderSetting("Смещение Pitch, °", 0, -90, 90, 1, () -> pitchMode.is("Статичный"));

    public final BooleanSetting movementCorrection = new BooleanSetting("Исправление движения", true);
    public final BooleanSetting visualRender = new BooleanSetting("Визуальное вращение", true);
    public final BooleanSetting yieldToAim = new BooleanSetting("Уступать аиму", true);

    private float spinYaw;
    private float spinPitch;
    private boolean pitchDirection = true;

    public Spinbot() {
        addSettings(
            yawMode, speed, direction, yawOffset, jitterRange,
            pitchMode, pitchSpeed, pitchOffset,
            movementCorrection, visualRender, yieldToAim
        );
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventKeyBoard keyboard) {
            if (movementCorrection.get() && mc.player != null) {
                if (!(yieldToAim.get() && combatRotating())) {
                    MoveUtil.fixMovement(keyboard, spinYaw);
                }
            }
        }

        if (event instanceof EventMotion motion) {
            if (mc.player == null) return;
            if (yieldToAim.get() && combatRotating()) return;

            float cameraYaw = mc.player.getYRot();

            // Yaw logic
            switch (yawMode.get()) {
                case "Вращение" -> {
                    float step = speed.get().floatValue();
                    spinYaw = Mth.wrapDegrees(spinYaw + (direction.is("Вправо") ? step : -step));
                }
                case "Джиттер" -> {
                    float base = cameraYaw + yawOffset.get().floatValue();
                    spinYaw = Mth.wrapDegrees(base + (mc.player.tickCount % 2 == 0 ? jitterRange.get().floatValue() : -jitterRange.get().floatValue()));
                }
                case "Статичный" -> {
                    spinYaw = Mth.wrapDegrees(cameraYaw + yawOffset.get().floatValue());
                }
                case "Случайный" -> {
                    spinYaw = (float) (Math.random() * 360.0 - 180.0);
                }
            }

            // Pitch logic
            float cameraPitch = mc.player.getXRot();
            switch (pitchMode.get()) {
                case "Нет" -> {
                    spinPitch = cameraPitch;
                }
                case "Вниз" -> {
                    spinPitch = 90.0F;
                }
                case "Вверх" -> {
                    spinPitch = -90.0F;
                }
                case "Ноль" -> {
                    spinPitch = 0.0F;
                }
                case "Статичный" -> {
                    spinPitch = pitchOffset.get().floatValue();
                }
                case "Вращение" -> {
                    float pStep = pitchSpeed.get().floatValue();
                    spinPitch += pitchDirection ? pStep : -pStep;
                    if (spinPitch >= 89.9F) {
                        spinPitch = 89.9F;
                        pitchDirection = false;
                    } else if (spinPitch <= -89.9F) {
                        spinPitch = -89.9F;
                        pitchDirection = true;
                    }
                }
                case "Джиттер" -> {
                    spinPitch = mc.player.tickCount % 2 == 0 ? 89.9F : -89.9F;
                }
            }

            motion.setYaw(spinYaw);
            motion.setPitch(spinPitch);
        }
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
        spinPitch = mc.player != null ? mc.player.getXRot() : 0f;
        pitchDirection = true;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        spinYaw = 0f;
        spinPitch = 0f;
        super.onDisable();
    }
}
