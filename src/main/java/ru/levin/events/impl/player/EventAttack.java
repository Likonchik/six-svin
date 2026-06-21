package ru.levin.events.impl.player;


import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import ru.levin.events.Event;

public class EventAttack extends Event {
    private final Player attacker;
    private final Entity target;

    public EventAttack(Player attacker, Entity target) {
        this.attacker = attacker;
        this.target = target;
    }

    public Player getAttacker() {
        return attacker;
    }

    public Entity getTarget() {
        return target;
    }
}
