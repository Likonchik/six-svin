package ru.levin.modules.combat;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import ru.levin.mixin.iface.ClientWorldAccessor;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.MultiSetting;
import ru.levin.events.Event;
import ru.levin.events.impl.move.EventMotion;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.util.player.TimerUtil;

import java.util.Arrays;
import java.util.Optional;

@SuppressWarnings("All")
@FunctionAnnotation(name = "AutoPotion",keywords = "AutoBuff", type = Type.Combat,desc = "Автоматически кидает бафы под себя")
public class AutoPotion extends Function {
    private final BooleanSetting autoOff = new BooleanSetting("Авто отключение", false);

    public MultiSetting potions = new MultiSetting(
            "Бросать",
            Arrays.asList("Силу", "Скорость", "Огнестойкость"),
            new String[]{"Силу", "Скорость", "Огнестойкость"}
    );
    public final TimerUtil timer = new TimerUtil();
    private boolean spoofed = false;
    public boolean isActivePotion;
    private float rotprev;
    private int selectedSlot = -1;
    private final float pose = 90;

    public AutoPotion() {
        addSettings(potions,autoOff);
    }
    private boolean isEatingFood() {
        return mc.player.isUsingItem() && !mc.player.getUseItem().is(Items.SHIELD)
                && !mc.player.getUseItem().is(Items.BOW)
                && !mc.player.getUseItem().is(Items.TRIDENT);
    }

    private enum PotionType {
        STRENGTH(5, MobEffects.DAMAGE_BOOST, "Силу"),
        SPEED(1, MobEffects.MOVEMENT_SPEED, "Скорость"),
        FIRE_RESISTANCE(12, MobEffects.FIRE_RESISTANCE, "Огнестойкость");

        final int id;
        final Holder<MobEffect> effect;
        final String settingName;

        PotionType(int id, Holder<MobEffect> effect, String settingName) {
            this.id = id;
            this.effect = effect;
            this.settingName = settingName;
        }

        public boolean isEnabled(AutoPotion module) {
            return module.potions.get(this.settingName);
        }
    }

    private int findPotionSlot(PotionType type) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);

            if (stack.getItem() == Items.SPLASH_POTION) {
                Optional<PotionContents> potionComponent = Optional.ofNullable(stack.getComponents().get(DataComponents.POTION_CONTENTS));
                if (potionComponent.isPresent()) {
                    Iterable<MobEffectInstance> effects = potionComponent.get().getAllEffects();

                    for (MobEffectInstance effect : effects) {
                        if (effect.getEffect() == type.effect) {
                            return i;
                        }
                    }
                }
            }
        }
        return -1;
    }

    private boolean hasEffect(Holder<MobEffect> effect) {
        return mc.player.hasEffect(effect);
    }

    private boolean canBuff(PotionType type) {
        if (hasEffect(type.effect)) return false;
        return type.isEnabled(this) && findPotionSlot(type) != -1;
    }

    private boolean canBuff() {
        return !isEatingFood() && (canBuff(PotionType.STRENGTH) ||
                canBuff(PotionType.SPEED) ||
                canBuff(PotionType.FIRE_RESISTANCE)) &&
                mc.player.onGround() &&
                timer.hasTimeElapsed(500);
    }

    private boolean shouldUsePotion() {
        return true;
    }

    private boolean isActive() {
        return isActivePotion || (canBuff(PotionType.STRENGTH) || (canBuff(PotionType.SPEED) || (canBuff(PotionType.FIRE_RESISTANCE))));
    }

    @Override
    public void onEvent(Event event) {
        if (event instanceof EventMotion eventAfterRotate) {
            if (shouldThrow()) {
                rotprev = mc.player.getXRot();
                eventAfterRotate.setPitch(pose);
                spoofed = true;
                isActivePotion = true;
            }
        }

        if (event instanceof EventUpdate) {
            if (isActivePotion && !shouldThrow()) {
                isActivePotion = false;
                if (autoOff.get()) this.toggle();
            }

            if (shouldThrow() && spoofed) {
                throwPotion(PotionType.STRENGTH);
                throwPotion(PotionType.SPEED);
                throwPotion(PotionType.FIRE_RESISTANCE);

                mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().selected));

                mc.player.setXRot(rotprev);
                timer.reset();
                spoofed = false;
                isActivePotion = false;

                if (autoOff.get()) this.toggle();
            }
        }
    }

    private boolean shouldThrow() {
        return isActive() &&
                canBuff() &&
                mc.level.getBlockState(mc.player.blockPosition().below()).getBlock() != Blocks.AIR;
    }

    private void throwPotion(PotionType type) {
        if (!type.isEnabled(this) || hasEffect(type.effect)) return;

        int slot = findPotionSlot(type);
        if (slot == -1) return;

        selectedSlot = mc.player.getInventory().selected;
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));

        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();

        sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, yaw, pitch));
    }

    private void sendSequencedPacket(PredictiveAction packetCreator) {
        if (mc.player.connection == null || mc.level == null) return;
        try (BlockStatePredictionHandler pendingUpdateManager = ((ClientWorldAccessor) mc.level).getPendingUpdateManager().startPredicting()) {
            int sequence = pendingUpdateManager.currentSequence();
            mc.player.connection.send(packetCreator.predict(sequence));
        }
    }
}
