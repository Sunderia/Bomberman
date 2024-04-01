package fr.sunderia.bomberman.utils

import fr.sunderia.bomberman.PrimedTntEntity
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType

class CustomDamage(source: PrimedTntEntity): Damage(DamageType.PLAYER_EXPLOSION, source, source.player, source.position, 100f) {

    override fun buildDeathMessage(killed: Player): Component {
        return Component.translatable("death.attack.explosion.player.item", Component.text(killed.username), Component.text((attacker as Player).username), Component.text("a Bomb"))
    }

    override fun buildDeathScreenText(killed: Player): Component = buildDeathMessage(killed)

}