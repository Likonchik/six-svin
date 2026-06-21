package ru.levin.mixin.player;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.ClientManager;
import ru.levin.manager.Manager;
import ru.levin.modules.player.CustomCoolDown;

@Mixin(Item.class)
public class MixinItem {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void use(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (!(user instanceof LocalPlayer)) return;
        CustomCoolDown coolDown = Manager.FUNCTION_MANAGER.customCoolDown;
        if (!coolDown.state) return;

        if (coolDown.PVPonly.get() && !ClientManager.playerIsPVP()) return;

        ItemStack stack = user.getItemInHand(hand);
        if (coolDown.isItemEnabled(stack.getItem()) && coolDown.lastUseMap.containsKey(stack.getItem())) {
            cir.setReturnValue(InteractionResultHolder.fail(stack));
            cir.cancel();
        }
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    public void finishUsing(ItemStack stack, Level world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
        if (!(user instanceof LocalPlayer player)) return;
        CustomCoolDown coolDown = Manager.FUNCTION_MANAGER.customCoolDown;
        if (!coolDown.state) return;

        if (coolDown.PVPonly.get() && !ClientManager.playerIsPVP()) return;

        if (coolDown.isItemEnabled(stack.getItem())) {
            coolDown.setCooldown(stack.getItem());
            int cooldownTicks = (int)(coolDown.getCooldownForItem(stack.getItem()) * 20);
            player.getCooldowns().addCooldown(stack.getItem(), cooldownTicks);
        }
    }

    @Inject(method = "getUseDuration", at = @At("HEAD"), cancellable = true)
    public void getMaxUseTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
        if (!(user instanceof LocalPlayer)) return;
        CustomCoolDown coolDown = Manager.FUNCTION_MANAGER.customCoolDown;
        if (!coolDown.state) return;

        if (coolDown.PVPonly.get() && !ClientManager.playerIsPVP()) return;

        if (coolDown.isItemEnabled(stack.getItem()) && coolDown.lastUseMap.containsKey(stack.getItem())) {
            cir.setReturnValue(0);
            cir.cancel();
        }
    }

    @Inject(method = "useOnRelease", at = @At("HEAD"), cancellable = true)
    public void isUsedOnRelease(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        CustomCoolDown coolDown = Manager.FUNCTION_MANAGER.customCoolDown;
        if (!coolDown.state) return;

        if (coolDown.PVPonly.get() && !ClientManager.playerIsPVP()) return;

        if (coolDown.isItemEnabled(stack.getItem()) && coolDown.lastUseMap.containsKey(stack.getItem())) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
