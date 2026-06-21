package ru.levin.modules.render.littlePet;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.Level;

public class GhostWolfEntity extends Wolf {
    public GhostWolfEntity(EntityType<? extends Wolf> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return EntityDimensions.fixed(0.0F, 0.0F);
    }


    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
