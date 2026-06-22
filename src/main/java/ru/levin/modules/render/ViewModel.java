package ru.levin.modules.render;


import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

// Позиция/масштаб/поворот вида от первого лица. РАЗДЕЛЬНО:
//   - «Рука» (right_*/left_*) — ванильные предметы и пустая рука (MixinHeldItemRenderer + SwingAnimations).
//   - «Оружие» (gun_*) — TACZ-стволы (MixinApplyFirstPersonGunTransform), отдельный набор.
// Каждую группу можно включить/выключить своим тумблером и настроить независимо.
@FunctionAnnotation(name = "ViewModel", desc = "Позиция рук и оружия (раздельно)", type = Type.Render)
public class ViewModel extends Function {

    // ===== РУКА (ванильные предметы) =====
    public final BooleanSetting handEnabled = new BooleanSetting("Рука", true, "Применять к руке/предметам");
    public final SliderSetting right_x = new SliderSetting("RightX", 0.6F, -5f, 5f, 0.1F, () -> this.handEnabled.get()).withDesc("Правая рука по X");
    public final SliderSetting right_y = new SliderSetting("RightY", -0.6F, -5f, 5f, 0.1F, () -> this.handEnabled.get()).withDesc("Правая рука по Y");
    public final SliderSetting right_z = new SliderSetting("RightZ", -0.8F, -5f, 5f, 0.1F, () -> this.handEnabled.get()).withDesc("Правая рука по Z");
    public final SliderSetting left_x = new SliderSetting("LeftX", 0.0F, -5f, 5f, 0.1F, () -> this.handEnabled.get()).withDesc("Левая рука по X");
    public final SliderSetting left_y = new SliderSetting("LeftY", 0.0F, -5f, 5f, 0.1F, () -> this.handEnabled.get()).withDesc("Левая рука по Y");
    public final SliderSetting left_z = new SliderSetting("LeftZ", 0.0F, -5f, 5f, 0.1F, () -> this.handEnabled.get()).withDesc("Левая рука по Z");
    public final SliderSetting right_scale = new SliderSetting("RightScale", 1.0F, 0.1f, 3f, 0.05F, () -> this.handEnabled.get()).withDesc("Масштаб правой руки");
    public final SliderSetting left_scale = new SliderSetting("LeftScale", 1.0F, 0.1f, 3f, 0.05F, () -> this.handEnabled.get()).withDesc("Масштаб левой руки");
    public final SliderSetting right_rot_x = new SliderSetting("RightRotX", 0.0F, -180f, 180f, 1F, () -> this.handEnabled.get()).withDesc("Поворот правой руки по X");
    public final SliderSetting right_rot_y = new SliderSetting("RightRotY", 0.0F, -180f, 180f, 1F, () -> this.handEnabled.get()).withDesc("Поворот правой руки по Y");
    public final SliderSetting right_rot_z = new SliderSetting("RightRotZ", 0.0F, -180f, 180f, 1F, () -> this.handEnabled.get()).withDesc("Поворот правой руки по Z");
    public final SliderSetting left_rot_x = new SliderSetting("LeftRotX", 0.0F, -180f, 180f, 1F, () -> this.handEnabled.get()).withDesc("Поворот левой руки по X");
    public final SliderSetting left_rot_y = new SliderSetting("LeftRotY", 0.0F, -180f, 180f, 1F, () -> this.handEnabled.get()).withDesc("Поворот левой руки по Y");
    public final SliderSetting left_rot_z = new SliderSetting("LeftRotZ", 0.0F, -180f, 180f, 1F, () -> this.handEnabled.get()).withDesc("Поворот левой руки по Z");

    // ===== ОРУЖИЕ (TACZ-ствол) =====
    public final BooleanSetting weaponEnabled = new BooleanSetting("Оружие", true, "Применять к оружию (TACZ)");
    public final SliderSetting gun_x = new SliderSetting("Оружие X", 0.6F, -5f, 5f, 0.1F, () -> this.weaponEnabled.get()).withDesc("Оружие по X");
    public final SliderSetting gun_y = new SliderSetting("Оружие Y", -0.6F, -5f, 5f, 0.1F, () -> this.weaponEnabled.get()).withDesc("Оружие по Y");
    public final SliderSetting gun_z = new SliderSetting("Оружие Z", -0.8F, -5f, 5f, 0.1F, () -> this.weaponEnabled.get()).withDesc("Оружие по Z");
    public final SliderSetting gun_scale = new SliderSetting("Оружие Масштаб", 1.0F, 0.1f, 3f, 0.05F, () -> this.weaponEnabled.get()).withDesc("Масштаб оружия");
    public final SliderSetting gun_rot_x = new SliderSetting("Оружие RotX", 0.0F, -180f, 180f, 1F, () -> this.weaponEnabled.get()).withDesc("Поворот оружия по X");
    public final SliderSetting gun_rot_y = new SliderSetting("Оружие RotY", 0.0F, -180f, 180f, 1F, () -> this.weaponEnabled.get()).withDesc("Поворот оружия по Y");
    public final SliderSetting gun_rot_z = new SliderSetting("Оружие RotZ", 0.0F, -180f, 180f, 1F, () -> this.weaponEnabled.get()).withDesc("Поворот оружия по Z");

    public ViewModel() {
        addSettings(handEnabled, right_x, right_y, right_z, left_x, left_y, left_z,
                right_scale, left_scale, right_rot_x, right_rot_y, right_rot_z,
                left_rot_x, left_rot_y, left_rot_z,
                weaponEnabled, gun_x, gun_y, gun_z, gun_scale, gun_rot_x, gun_rot_y, gun_rot_z);
    }

    // включён модуль и группа активна
    public boolean handOn() { return state && handEnabled.get(); }
    public boolean weaponOn() { return state && weaponEnabled.get(); }

    @Override
    public void onEvent(Event event) {
    }
}
