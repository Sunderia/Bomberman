package fr.sunderia.bomberman.party

import fr.sunderia.bomberman.Bomberman
import fr.sunderia.bomberman.Bomberman.Companion.powerMap
import fr.sunderia.bomberman.InstanceCreator.Companion.createInstanceContainer
import fr.sunderia.bomberman.InstanceCreator.Companion.generateStructure
import fr.sunderia.bomberman.utils.PowerupTags
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.attribute.Attribute
import net.minestom.server.attribute.AttributeModifier
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.tag.Tag
import net.minestom.server.timer.SchedulerManager
import net.minestom.server.timer.TaskSchedule
import java.util.*

data class Game(val instance: InstanceContainer, val scheduler: SchedulerManager = MinecraftServer.getSchedulerManager(), var gameStatus: GameStatus = GameStatus.WAITING) {
    private var timeLeftBeforeClose = 5
    private var timeLeftBeforeStart = 10

    init {
        val manager = MinecraftServer.getSchedulerManager()
        manager.submitTask {
            if(timeLeftBeforeStart == 0) {
                instance.sendMessage(text("Starting game"))
                gameStatus = GameStatus.RUNNING
                return@submitTask TaskSchedule.stop()
            }
            if(instance.players.size >= 2) {
                timeLeftBeforeStart--
                instance.sendMessage(text("Starting in $timeLeftBeforeStart seconds"))
            } else {
                timeLeftBeforeStart = 10
            }
            return@submitTask TaskSchedule.seconds(1)
        }
    }

    private fun closeGame() {
        this.instance.players.forEach {
            it.sendMessage(text("Game closed"))
            it.instance = Bomberman.getLobbyInstance()
            it.inventory.clear()
        }
        removeGame(this.instance)
    }

    fun endGame() {
        this.gameStatus = GameStatus.ENDING
        MinecraftServer.getSchedulerManager().submitTask {
            if (this.timeLeftBeforeClose == 0) {
                resetGame(this.instance)
                this.closeGame()
                return@submitTask TaskSchedule.stop()
            }
            this.instance.sendMessage(text("Closing in ").append(text(this.timeLeftBeforeClose).color(NamedTextColor.RED)))
            this.timeLeftBeforeClose--
            return@submitTask TaskSchedule.seconds(1)
        }
    }

    private fun resetGame(instance: Instance) {
        powerMap.replaceAll { _: UUID, _: Int -> 2 }
        instance.players.forEach { p: Player ->
            p.clearEffects()
            val uuids = p.getAttribute(Attribute.MOVEMENT_SPEED).modifiers.stream()
                    .map { obj: AttributeModifier -> obj.id }
                    .toList().toTypedArray()
            for (uuid in uuids) {
                p.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(uuid!!)
            }
            p.removeTag(PowerupTags.BOXING_GLOVE.tag)
        }
        instance.entities.stream()
            .filter { e: Entity ->
                e.entityType.id() == EntityType.TNT.id() || e.entityType.id() == EntityType.ITEM.id()
            }
            .forEach { obj: Entity -> obj.remove() }
    }

    companion object {
        // Can be bypassed by having a party with more than 4 players
        private const val MAX_PLAYERS_PER_GAME = 4
        private val games = mutableMapOf<Instance, Game>()

        fun getNonFilledGames(): Game? {
            return games.values.filter { it.instance.players.size < MAX_PLAYERS_PER_GAME && it.gameStatus == GameStatus.WAITING }.maxByOrNull { it.instance.players.size }
        }

        fun getGame(instance: Instance) = games[instance]

        fun removeGame(instance: Instance) = games.remove(instance)

        fun createGame(manager: InstanceManager): InstanceContainer {
            val container = createInstanceContainer(manager)
            container.setTag(Tag.Boolean("game"), true)
            generateStructure(container)
            val game = Game(container)
            games[container] = game
            return container
        }

        fun playerLeft(instance: Instance) {
            val game = games[instance]!!
            game.instance.players.first().sendMessage(text("You won!"))
            game.endGame()
        }
    }
}
