package ru.levin.modules.movement;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.EventUpdate;
import ru.levin.mixin.iface.MixinLivingEntityAccessor;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.SliderSetting;
import ru.levin.events.Event;
import ru.levin.util.player.InventoryUtil;

@SuppressWarnings("All")
@FunctionAnnotation(name = "ElytraRecast", desc = "Автоматический ререк кидание элитры", type = Type.Move)
public class ElytraRecast extends Function {
    public BooleanSetting changePitch = new BooleanSetting("ChangePitch", true);
    public SliderSetting pitchValue = new SliderSetting("PitchValue", 55f, -90f, 90f, 1,() -> changePitch.get());
    public BooleanSetting autoJump = new BooleanSetting("AutoJump", true);

    public ElytraRecast() {
        addSettings(changePitch, pitchValue, autoJump);
    }

    @Override
    public void onEvent(Event event) {
        if (mc.player == null || mc.level == null) return;
        if (event instanceof EventMotion em) {
            if (changePitch.get() && !mc.player.isFallFlying() && checkElytra()) {
                em.setPitch(pitchValue.get().floatValue());
            }

        }
        if (event instanceof EventUpdate) {
            onUpdate();
        }
    }

    @Override
    protected void onDisable() {
        if (!mc.options.keyUp.isDown()) {
            mc.options.keyUp.setDown(false);
        }
        if (!mc.options.keyJump.isDown()) {
            mc.options.keyJump.setDown(false);
        }
    }

    private void onUpdate() {
        if (!mc.player.isFallFlying() && checkElytra()) {
            if (autoJump.get()) {
                if (mc.player.onGround()) {
                    mc.player.jumpFromGround();
                }
            }
        }

        if (!mc.player.isFallFlying() && mc.player.fallDistance > 0 && checkElytra()) {
            castElytra();
        }

        ((MixinLivingEntityAccessor)mc.player).setLastJumpCooldown(0);
    }

    public boolean castElytra() {
        if (checkElytra() && check()) {
            InventoryUtil.startFly();
            return true;
        }
        return false;
    }

    private boolean checkElytra() {
        ItemStack chestStack = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        return chestStack.getItem() == Items.ELYTRA && isUsable(chestStack) && !mc.player.getAbilities().flying && mc.player.getVehicle() == null && !mc.player.onClimbable();
    }

    public static boolean isUsable(ItemStack stack) {
        if (stack == null) return false;
        if (stack.isEmpty()) return false;
        if (stack.getItem() != Items.ELYTRA) return false;

        int maxDamage = stack.getMaxDamage();
        int damage = stack.getDamageValue();
        return damage < maxDamage - 1;
    }

    private boolean check() {
        return !mc.player.isCreative() && !mc.player.isSpectator() && !mc.player.hasEffect(MobEffects.LEVITATION);
    }
}