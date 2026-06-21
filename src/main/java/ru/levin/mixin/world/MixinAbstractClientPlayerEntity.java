package ru.levin.mixin.world;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.levin.manager.ClientManager;
import ru.levin.manager.IMinecraft;
import ru.levin.manager.Manager;
import ru.levin.util.render.providers.ResourceProvider;

@Mixin(AbstractClientPlayer.class)
public class MixinAbstractClientPlayerEntity implements IMinecraft {
    private String cachedPlayerName;

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void injectGetSkinTextures(CallbackInfoReturnable<PlayerSkin> cir) {
        if (ClientManager.legitMode) {
            return;
        }
        if (Manager.FUNCTION_MANAGER.cape == null || !Manager.FUNCTION_MANAGER.cape.state) {
            return; // плащ отключён
        }
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        String playerName = player.getName().getString();
        if (cachedPlayerName == null) {
            cachedPlayerName = mc.player.getName().getString();
        }
        if (Manager.FRIEND_MANAGER.isFriend(playerName) || playerName.equalsIgnoreCase(cachedPlayerName)) {
            PlayerSkin original = cir.getReturnValue();
            PlayerSkin newTextures = new PlayerSkin(original.texture(),
                    original.textureUrl(),
                    ResourceProvider.CUSTOM_CAPE,
                    ResourceProvider.CUSTOM_ELYTRA,
                    original.model(),
                    original.secure()
            );
            cir.setReturnValue(newTextures);
        }
    }
}