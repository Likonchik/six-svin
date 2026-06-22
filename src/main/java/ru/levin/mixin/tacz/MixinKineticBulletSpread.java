package ru.levin.mixin.tacz;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.joml.Vector2d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.levin.manager.Manager;

// Изм.1/4: зануляет случайный разброс (Vector2d) пули TACZ для выстрелов локального игрока, когда активен
// "Пинпоинт" (GunNoSpread) или "Свой разброс=0" (GunAimbot). shoot() строит направление из
// Vector3d(vec2.x, vec2.y, 8); vec2=(0,0) => пуля строго по (pitch,yaw). Метод исполняется только на
// сервере (на клиенте пуля приходит готовым spawn-пакетом), скоуп по UUID владельца. remap=false: TACZ не
// Mojmap. required=false/defaultRequire=0 => мягкий no-op на удалённом сервере, где миксин не грузится.
@Mixin(value = EntityKineticBullet.class, remap = false)
public class MixinKineticBulletSpread {

    @ModifyVariable(
            method = "shoot(DDFLorg/joml/Vector2d;)V",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false)
    private Vector2d onapixShootSpread(Vector2d original) {
        try {
            if (Manager.FUNCTION_MANAGER == null) return original;
            boolean want =
                    (Manager.FUNCTION_MANAGER.gunNoSpread != null
                            && Manager.FUNCTION_MANAGER.gunNoSpread.forcePinpoint())
                 || (Manager.FUNCTION_MANAGER.gunAimbot != null
                            && Manager.FUNCTION_MANAGER.gunAimbot.wantsZeroSpread());
            if (!want) return original;
            // SERVER-ONLY: зануляем разброс ТОЛЬКО у серверной (авторитетной) пули — той, что и есть
            // реальный hitreg, который проверяет анти-чит. Клиентскую пулю (если TACZ когда-нибудь начнёт
            // её предсказывать) НЕ трогаем, чтобы клиентский визуал оставался естественным. Сейчас в TACZ
            // shoot() и так вызывается лишь на логическом сервере — это явная страховка «только для сервера».
            if (((Entity) (Object) this).level().isClientSide) return original;
            Entity owner = ((Projectile) (Object) this).getOwner();
            LocalPlayer self = Minecraft.getInstance().player;
            if (owner != null && self != null && owner.getUUID().equals(self.getUUID())) {
                return new Vector2d(0.0, 0.0);
            }
        } catch (Throwable ignored) {}
        return original;
    }
}
