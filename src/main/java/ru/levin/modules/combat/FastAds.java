package ru.levin.modules.combat;

import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;

// FastADS: every first-person ADS visual for the local player (FOV zoom, gun-to-sight pose, scope lens,
// crosshair-hide) reads ONE method — LocalPlayerAim.getClientAimingProgress(). MixinLocalPlayerAim snaps
// it to 1.0 while aiming, so the scope opens INSTANTLY with no aimTime ramp (default 0.2s). Spread/sens
// read the server-synced progress instead, so this is purely the visual/zoom speed (use GunNoSpread for
// instant spread). Passive module — the mixin reads its state (NoRecoil pattern).
@FunctionAnnotation(name = "FastADS", keywords = {"ADS", "Прицел", "Инстант"}, desc = "Мгновенное открытие прицела TACZ", type = Type.Combat)
public class FastAds extends Function {

    // read by MixinLocalPlayerAim
    public boolean instantClient() {
        return state;
    }

    @Override
    public void onEvent(Event event) {
    }
}
