package ru.levin.modules.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;

// TACZ NoSpread. The server picks the shoot inaccuracy purely server-side in
// InaccuracyType.getInaccuracyType(LivingEntity): it returns AIM (~0.15, ~33x tighter than STAND's
// 5.0) iff getSynAimingProgress()==1.0f, which is driven by a raw isAiming boolean the server sets
// UNCONDITIONALLY from the ClientMessagePlayerAim packet (no gun/ADS/look validation).
//
//  - "Инстант": MixinInaccuracyType forces getInaccuracyType -> AIM for us. Because we never actually
//    set isAiming, there is NO movement slowdown and NO FOV/scope zoom — exactly "убрать разброс без
//    замедления". Effective on singleplayer / integrated / self-hosted (the mixin runs in our JVM).
//  - "Легит": we send the real ClientMessagePlayerAim(true) so the server's genuine aimingProgress
//    ramps to 1.0 -> AIM spread. Works on remote dedicated servers too, BUT the aiming movement
//    slowdown there is server-authoritative and cannot be removed by a client-only mod.
//
// All TACZ access is wrapped in try/catch so the module loads and stays inert without TACZ.
@SuppressWarnings("All")
@FunctionAnnotation(name = "GunNoSpread", keywords = {"NoSpread", "Разброс", "Стволы"}, desc = "Убирает разброс оружия TACZ", type = Type.Combat)
public class GunNoSpread extends Function {

    private final ModeSetting mode = new ModeSetting("Режим", "Инстант", "Инстант", "Легит");
    private final BooleanSetting hiddenAds = new BooleanSetting("Скрытый ADS", true, () -> mode.is("Легит"));
    private final BooleanSetting compSlow = new BooleanSetting("Компенсация замедления", false, () -> mode.is("Легит"));

    // TACZ's aiming MOVEMENT_SPEED penalty modifier id — added server-side but SYNCED to the client
    // attribute (MOVEMENT_SPEED is syncable), so stripping it client-side each tick lets the client
    // predict full walk speed. Works on singleplayer/vanilla (fixed move-check threshold, host skip);
    // strict re-simulating anticheats (Grim) flag it — hence the setting defaults OFF.
    private static final ResourceLocation TACZ_AIM_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath("tacz", "extra_speed_modifier");

    private boolean latched = false;
    private int heartbeat = 0;
    private net.minecraft.world.item.Item lastGun = null;

    public GunNoSpread() {
        addSettings(mode, hiddenAds, compSlow);
    }

    // read by MixinInaccuracyType: force AIM inaccuracy without ever aiming (no slow / no FOV)
    public boolean forceAimSpread() {
        return state && mode.is("Инстант");
    }

    // read by MixinMouseHandler: in "Легит" we force the server aiming state, which TACZ's MouseHandler
    // mixin reads (the SYNCED progress) to cut sensitivity; suppress that so aim feels like hip-fire.
    public boolean suppressAimSens() {
        return state && mode.is("Легит");
    }

    @Override
    public void onEvent(Event event) {
        if (!(event instanceof EventUpdate)) return;
        if (mc.player == null) return;

        // compensate the aiming walk-slowdown by stripping the synced MOVEMENT_SPEED modifier each
        // tick (only "Легит"; Инстант never aims so never slows). Must re-strip every tick — the server
        // re-syncs the modifier. EventUpdate runs before aiStep reads the attribute, so we predict full.
        if (compSlow.get() && mode.is("Легит") && hasGunInHand()) {
            stripAimSlow();
        }

        // "Легит": latch the real aim state on the server. Re-send on gun change AND on a periodic
        // heartbeat — switching weapons (or a server-side reset) clears the server's aiming state, so a
        // pure send-once-on-change would silently stop working until the module is toggled off/on.
        if (mode.is("Легит") && hasGunInHand()) {
            net.minecraft.world.item.Item gun = mc.player.getMainHandItem().getItem();
            boolean changed = gun != lastGun;
            lastGun = gun;
            if (!latched || changed || (heartbeat++ % 20 == 0)) {
                sendAim(true);
                latched = true;
            }
        } else if (latched) {
            sendAim(false);
            latched = false;
            lastGun = null;
            heartbeat = 0;
        }
    }

    @Override
    protected void onDisable() {
        if (latched) {
            sendAim(false);
            latched = false;
        }
        lastGun = null;
        heartbeat = 0;
        super.onDisable();
    }

    // remove TACZ's aim MOVEMENT_SPEED penalty from the client attribute (no-op if not present)
    private void stripAimSlow() {
        try {
            if (mc.player == null) return;
            AttributeInstance ms = mc.player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (ms != null) ms.removeModifier(TACZ_AIM_SPEED_ID);
        } catch (Throwable ignored) {}
    }

    private void sendAim(boolean aim) {
        try {
            if (hiddenAds.get()) {
                // raw packet -> server boolean only, no client FOV/scope visuals
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.tacz.guns.network.message.ClientMessagePlayerAim(aim));
            } else {
                // operator path -> also drives the legit ADS visuals (FOV zoom / scope)
                com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator
                        .fromLocalPlayer(mc.player).aim(aim);
            }
        } catch (Throwable ignored) {}
    }

    private boolean hasGunInHand() {
        try {
            ItemStack stack = mc.player.getMainHandItem();
            return !stack.isEmpty() && com.tacz.guns.api.item.IGun.getIGunOrNull(stack) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
