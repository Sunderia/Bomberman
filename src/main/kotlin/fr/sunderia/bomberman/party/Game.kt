package fr.sunderia.bomberman.party

import fr.sunderia.bomberman.Bomberman
import fr.sunderia.bomberman.InstanceCreator.Companion.createInstanceContainer
import fr.sunderia.bomberman.InstanceCreator.Companion.generateStructure
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.tag.Tag
import net.minestom.server.timer.SchedulerManager

data class Game(val instance: InstanceContainer, val scheduler: SchedulerManager = MinecraftServer.getSchedulerManager(), var gameStatus: GameStatus = GameStatus.WAITING) {
    private var timeLeftBeforeClose = 5

    fun decreaseTime() {
        timeLeftBeforeClose--
    }

    fun getTimeLeft() = timeLeftBeforeClose

    fun endGame() {
        this.instance.players.forEach {
            it.sendMessage(Component.text("Game closed"))
            it.instance = Bomberman.getLobbyInstance()
            it.inventory.clear()
        }
        removeGame(this.instance)
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
    }
}
