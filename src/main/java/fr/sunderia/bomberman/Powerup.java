package fr.sunderia.bomberman;

import java.util.function.Consumer;

import net.minestom.server.entity.Player;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;

enum Powerup {
    // TODO: Add min and max
    FIRE_UP(p -> incrementPower(p, 1)),
    FULL_FIRE(p -> Bomberman.powerMap.put(p.getUuid(), 8)),
    FIRE_DOWN(p -> incrementPower(p, -1)),
    SPEED_UP(p -> p.addEffect(new Potion(PotionEffect.SPEED,
            (byte) (p.getActiveEffects().stream().filter(e -> e.getPotion().effect().id() == PotionEffect.SPEED.id())
                    .findFirst().map(e -> (int) e.getPotion().amplifier()).orElse(0) + 1),
            Integer.MAX_VALUE))),
    SPEED_DOWN(p -> p.addEffect(new Potion(PotionEffect.SLOWNESS,
            (byte) (p.getActiveEffects().stream().filter(e -> e.getPotion().effect().id() == PotionEffect.SLOWNESS.id())
                    .findFirst().map(e -> (int) e.getPotion().amplifier()).orElse(0) + 1),
            Integer.MAX_VALUE))),
            ;

    private final Consumer<Player> effect;

    Powerup(Consumer<Player> effect) {
        this.effect = effect;
    }

    public Consumer<Player> getEffect() {
        return effect;
    }

    private static void incrementPower(Player p, int increment) {
        int currentPower = Bomberman.powerMap.get(p.getUuid());
        Bomberman.powerMap.put(p.getUuid(), Math.min(Math.max(currentPower + increment, 1), 8));
    }
}
