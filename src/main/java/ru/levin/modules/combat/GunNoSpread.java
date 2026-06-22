package ru.levin.modules.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import ru.levin.events.Event;
import ru.levin.events.impl.EventUpdate;
import ru.levin.manager.Manager;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;
import ru.levin.modules.setting.ModeSetting;
import ru.levin.modules.setting.SliderSetting;

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

    // "Пинпоинт" 🔵 — разброс пули в 0 (MixinKineticBulletSpread, свой хост); "Инстант" 🔵 — форс типа AIM
    // (MixinInaccuracyType, свой хост); "Легит" 🟢 — реальный aim-пакет (работает и на удалённом сервере).
    private final ModeSetting mode = new ModeSetting("Режим", "Инстант", "Пинпоинт", "Инстант", "Легит");
    private final BooleanSetting hiddenAds = new BooleanSetting("Скрытый ADS", true, () -> mode.is("Легит"));
    private final BooleanSetting compSlow = new BooleanSetting("Компенсация замедления", false, () -> mode.is("Легит"));
    // стелс: держать серверный aim ТОЛЬКО когда рядом есть цель (а не постоянно). Снижает сигнатуру АЧ
    // «isAiming=true без визуального ADS», оставаясь near-zero spread в момент боя. По умолчанию выкл.
    private final BooleanSetting onlyCombat = new BooleanSetting("Только в бою", false, () -> mode.is("Легит"));
    private final SliderSetting aimInterval = new SliderSetting("Интервал aim, тики", 20, 1, 100, 1, () -> mode.is("Легит"));

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
        addSettings(mode, hiddenAds, compSlow, onlyCombat, aimInterval);
    }

    // read by MixinInaccuracyType: force AIM inaccuracy without ever aiming (no slow / no FOV)
    public boolean forceAimSpread() {
        return state && mode.is("Инстант");
    }

    // read by MixinKineticBulletSpread: занулить разброс пули полностью (не только тип AIM). Свой хост.
    public boolean forcePinpoint() {
        return state && mode.is("Пинпоинт");
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
        if (mode.is("Легит") && hasGunInHand() && (!onlyCombat.get() || hasCombatTarget())) {
            net.minecraft.world.item.Item gun = mc.player.getMainHandItem().getItem();
            boolean changed = gun != lastGun;
            lastGun = gun;
            int iv = Math.max(1, (int) aimInterval.get().floatValue());
            if (!latched || changed || (heartbeat++ % iv == 0)) {
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

    // есть ли поблизости потенциальная цель: цель GunAimbot, иначе любая живая сущность в ~40 блоках.
    // fail-open (true при ошибке) — чтобы не отключать near-zero spread из-за нештатной ситуации.
    private boolean hasCombatTarget() {
        try {
            if (Manager.FUNCTION_MANAGER.gunAimbot != null
                    && Manager.FUNCTION_MANAGER.gunAimbot.getTarget() != null) {
                return true;
            }
            if (mc.level == null || mc.player == null) return false;
            double r = 40.0;
            for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player) continue;
                if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) continue;
                if (e instanceof net.minecraft.world.entity.LivingEntity le
                        && le.isAlive() && mc.player.distanceTo(le) <= r) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }
}
