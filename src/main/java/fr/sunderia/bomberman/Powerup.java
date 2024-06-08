package fr.sunderia.bomberman;

import fr.sunderia.bomberman.utils.PowerupTags;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;

import java.util.function.Consumer;

enum Powerup {
    FIRE_UP(p -> incrementPower(p, 1)),
    FULL_FIRE(p -> Bomberman.Companion.getPowerMap().put(p.getUuid(), 8)),
    FIRE_DOWN(p -> incrementPower(p, -1)),
    //MAX SPEED .1f
    SPEED_UP(p -> incrementSpeed(p, 0.1f / 8)),
    //MIN SPEED -0.025
    SPEED_DOWN(p -> incrementSpeed(p, -0.025f / 4)),
    BOXING_GLOVE(p -> setTag(p, PowerupTags.BOXING_GLOVE)),
    PIERCE(p -> setTag(p, PowerupTags.PIERCE))
    ;

    private static void setTag(Player player, PowerupTags tag) {
        player.setTag(tag.getBool(), true);
    }

    private final Consumer<Player> effect;

    Powerup(Consumer<Player> effect) {
        this.effect = effect;
    }

    public Consumer<Player> getEffect() {
        return effect;
    }

    private static void incrementSpeed(Player player, float amount) {
        player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).addModifier(new AttributeModifier("speed", amount, AttributeOperation.ADD_VALUE));
    }

    @SuppressWarnings("DataFlowIssue")
    private static void incrementPower(Player p, int increment) {
        Bomberman.Companion.getPowerMap().compute(p.getUuid(), (k, currentPower) -> Math.min(Math.max(currentPower + increment, 1), 8));
    }
}