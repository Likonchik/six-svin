package ru.levin.modules.setting;


import java.util.function.Supplier;

public class Setting {

    protected String name;
    protected String desc;
    protected Supplier<Boolean> visible;

    public boolean isVisible() {
        return visible.get();
    }

    public void setVisible(Supplier<Boolean> visible) {
        this.visible = visible;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    // fluent description setter: new XSetting(...).withDesc("что делает") — shown as a hover tooltip.
    // (BooleanSetting keeps its own desc via its constructors; this serves Slider/Mode/Multi settings.)
    @SuppressWarnings("unchecked")
    public <T extends Setting> T withDesc(String description) {
        this.desc = description;
        return (T) this;
    }
}