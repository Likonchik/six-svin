package ru.levin.modules.combat;

import ru.levin.events.Event;
import ru.levin.modules.Function;
import ru.levin.modules.FunctionAnnotation;
import ru.levin.modules.Type;
import ru.levin.modules.setting.BooleanSetting;

// NoVisualADS: keep the FOV magnification (screen zooms in) but hide the VISUAL scope. The zoom is
// produced by separate CameraSetupEvent ComputeFov handlers, independent of the gun pose and the scope
// lens, so we can suppress those two while the zoom stays:
//   - pose:    MixinFirstPersonRenderGunEvent zeros the gun's aiming-progress -> gun stays in idle/hip
//              position (idle positioning still applies, only the aim-snap is cancelled).
//   - overlay: MixinBedrockAttachmentModel cancels the stencil ocular/lens/reticle draw.
// Passive module — the mixins read its state.
@FunctionAnnotation(name = "NoVisualADS", keywords = {"ADS", "Прицел", "Зум"}, desc = "Скрывает визуал прицела TACZ, оставляя зум", type = Type.Combat)
public class NoVisualAds extends Function {

    private final BooleanSetting pose = new BooleanSetting("Скрыть поднятие ствола", true);
    private final BooleanSetting overlay = new BooleanSetting("Скрыть линзу прицела", true);

    public NoVisualAds() {
        addSettings(pose, overlay);
    }

    // read by MixinFirstPersonRenderGunEvent
    public boolean suppressPose() {
        return state && pose.get();
    }

    // read by MixinBedrockAttachmentModel
    public boolean suppressOverlay() {
        return state && overlay.get();
    }

    @Override
    public void onEvent(Event event) {
    }
}
