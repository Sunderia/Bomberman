package fr.sunderia.bomberman;

import net.minestom.server.attribute.Attribute;
import net.minestom.server.attribute.AttributeInstance;
import net.minestom.server.attribute.AttributeModifier;
import net.minestom.server.attribute.AttributeOperation;
import net.minestom.server.entity.Player;

import java.util.function.Consumer;

enum Powerup {
    FIRE_UP(p -> incrementPower(p, 1)),
    FULL_FIRE(p -> Bomberman.Companion.getPowerMap().put(p.getUuid(), 8)),
    FIRE_DOWN(p -> incrementPower(p, -1)),
    //MAX SPEED .1f
    SPEED_UP(p -> incrementSpeed(p, 0.1f / 8)),
    //MIN SPEED -0.025
    SPEED_DOWN(p -> incrementSpeed(p, -0.025f / 4)),
    ;

    private final Consumer<Player> effect;

    Powerup(Consumer<Player> effect) {
        this.effect = effect;
    }

    public Consumer<Player> getEffect() {
        return effect;
    }

    private static void incrementSpeed(Player player, float amount) {
        AttributeInstance currentAttribute = player.getAttribute(Attribute.MOVEMENT_SPEED);
        currentAttribute.addModifier(new AttributeModifier("speed", amount, AttributeOperation.ADDITION));
    }

    private static void incrementPower(Player p, int increment) {
        int currentPower = Bomberman.Companion.getPowerMap().get(p.getUuid());
        Bomberman.Companion.getPowerMap().put(p.getUuid(), Math.min(Math.max(currentPower + increment, 1), 8));
    }
}
