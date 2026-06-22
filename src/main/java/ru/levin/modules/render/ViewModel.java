package ru.levin.modules.render;


import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

@FunctionAnnotation(name = "ViewModel",desc  = "Изменение позиции рук", type = Type.Render)
public class ViewModel extends Function {
    public final SliderSetting right_x = new SliderSetting("RightX", 0.6F, -5f, 5f, 0.1F).withDesc("Правая рука по X");
    public final SliderSetting right_y = new SliderSetting("RightY", -0.6F, -5f, 5f, 0.1F).withDesc("Правая рука по Y");
    public final SliderSetting right_z = new SliderSetting("RightZ", -0.8F, -5f, 5f, 0.1F).withDesc("Правая рука по Z");
    public final SliderSetting left_x = new SliderSetting("LeftX", 0.0F, -5f, 5f, 0.1F).withDesc("Левая рука по X");
    public final SliderSetting left_y = new SliderSetting("LeftY", 0.0F, -5f, 5f, 0.1F).withDesc("Левая рука по Y");
    public final SliderSetting left_z = new SliderSetting("LeftZ", 0.0F, -5f, 5f, 0.1F).withDesc("Левая рука по Z");

    // --- масштаб (1.0 = без изменений) ---
    public final SliderSetting right_scale = new SliderSetting("RightScale", 1.0F, 0.1f, 3f, 0.05F).withDesc("Масштаб правой руки");
    public final SliderSetting left_scale = new SliderSetting("LeftScale", 1.0F, 0.1f, 3f, 0.05F).withDesc("Масштаб левой руки");
    // --- поворот в градусах (0 = без изменений) ---
    public final SliderSetting right_rot_x = new SliderSetting("RightRotX", 0.0F, -180f, 180f, 1F).withDesc("Поворот правой руки по X");
    public final SliderSetting right_rot_y = new SliderSetting("RightRotY", 0.0F, -180f, 180f, 1F).withDesc("Поворот правой руки по Y");
    public final SliderSetting right_rot_z = new SliderSetting("RightRotZ", 0.0F, -180f, 180f, 1F).withDesc("Поворот правой руки по Z");
    public final SliderSetting left_rot_x = new SliderSetting("LeftRotX", 0.0F, -180f, 180f, 1F).withDesc("Поворот левой руки по X");
    public final SliderSetting left_rot_y = new SliderSetting("LeftRotY", 0.0F, -180f, 180f, 1F).withDesc("Поворот левой руки по Y");
    public final SliderSetting left_rot_z = new SliderSetting("LeftRotZ", 0.0F, -180f, 180f, 1F).withDesc("Поворот левой руки по Z");

    public ViewModel() {
        addSettings(right_x, right_y, right_z, left_x, left_y, left_z,
                right_scale, left_scale,
                right_rot_x, right_rot_y, right_rot_z,
                left_rot_x, left_rot_y, left_rot_z);
    }

    @Override
    public void onEvent(Event event) {

    }
}