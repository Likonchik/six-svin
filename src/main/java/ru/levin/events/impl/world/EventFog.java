package ru.levin.events.impl.world;

import com.mojang.blaze3d.shaders.FogShape;
import ru.levin.events.Event;

public class EventFog extends Event {
    public boolean modified = false;
    public float r, g, b, alpha;
    public float start, end;
    public FogShape shape;
}
