package ru.levin.events.impl.render;

import net.minecraft.world.entity.LivingEntity;
import ru.levin.events.Event;

public class EventPlayerRender extends Event {
    private final LivingEntity livingEntity;

    private float prevYaw;
    private float yaw;
    private float prevPitch;
    private float pitch;
    private float prevBodyYaw;
    private float bodyYaw;

    public EventPlayerRender(LivingEntity entity) {
        this.livingEntity = entity;
        this.yaw = entity.yHeadRot;
        this.prevYaw = entity.yHeadRotO;
        this.pitch = entity.getXRot();
        this.prevPitch = entity.xRotO;
        this.bodyYaw = entity.yBodyRot;
        this.prevBodyYaw = entity.yBodyRotO;
    }

    public LivingEntity getLivingEntity() { return livingEntity; }

    public float getPrevYaw() { return prevYaw; }
    public void setPrevYaw(float prevYaw) { this.prevYaw = prevYaw; }

    public float getYaw() { return yaw; }
    public void setYaw(float yaw) { this.yaw = yaw; }

    public float getPrevPitch() { return prevPitch; }
    public void setPrevPitch(float prevPitch) { this.prevPitch = prevPitch; }

    public float getPitch() { return pitch; }
    public void setPitch(float pitch) { this.pitch = pitch; }

    public float getPrevBodyYaw() { return prevBodyYaw; }
    public void setPrevBodyYaw(float prevBodyYaw) { this.prevBodyYaw = prevBodyYaw; }

    public float getBodyYaw() { return bodyYaw; }
    public void setBodyYaw(float bodyYaw) { this.bodyYaw = bodyYaw; }
}
