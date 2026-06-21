package ru.levin.events.impl.world;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import ru.levin.events.Event;


@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
public class EventObsidianPlace extends Event {
    private final Block block;
    private final BlockPos pos;
}
