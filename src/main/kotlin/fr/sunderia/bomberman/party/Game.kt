package fr.sunderia.bomberman.party

import fr.sunderia.bomberman.Bomberman
import fr.sunderia.bomberman.Bomberman.Companion.fontKey
import fr.sunderia.bomberman.Bomberman.Companion.powerMap
import fr.sunderia.bomberman.GameMap
import fr.sunderia.bomberman.InstanceCreator.Companion.createInstanceContainer
import fr.sunderia.bomberman.InstanceCreator.Companion.generateStructure
import fr.sunderia.bomberman.Powerup
import fr.sunderia.bomberman.utils.FakeNPC
import fr.sunderia.bomberman.utils.PowerupTags
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.tag.Tag
import net.minestom.server.timer.ExecutionType
import net.minestom.server.timer.SchedulerManager
import net.minestom.server.timer.TaskSchedule
import java.util.*
import kotlin.math.abs

data class Game(val instance: InstanceContainer, val map: GameMap) {
    var gameStatus: GameStatus = GameStatus.WAITING
    private val playerNPCMap = mutableMapOf<UUID, FakeNPC>()
    private val playerCameraMap = mutableMapOf<UUID, Entity>()
    private var playerSpawnCounter = 0
    private val scheduler: SchedulerManager = MinecraftServer.getSchedulerManager()
    private var timeLeftBeforeClose = 5
    private var timeLeftBeforeStart = 10
    private var playersAtStartOfGame = 0
    // Time left until sudden death
    private var timeLimit = map.settings.timeLimit
    /*
    bossbar set bomberman:timer name [
        {"text": "\uE101\uE101\uE101\uE101\uE402\uE407", "color": "red", "font": "bomberman:font"},
        {"font":"bomberman:font", "text":"\uE100\uE100\uE100\uE102\uE200","shadow_color": 0}
    ]
     */
    private val bossbar: BossBar = BossBar.bossBar(
        bossBarText(),
        1f,
        BossBar.Color.WHITE,
        BossBar.Overlay.PROGRESS
    )

    private fun getFakeNPC(uuid: UUID) = playerNPCMap[uuid]

    private fun bossBarText() = text("${"\uE101".repeat(2)}${timeLimit.toString().toCharArray().map { Char(0xE400 + it.digitToInt()) }.joinToString("")}")
        .style { it.color(NamedTextColor.RED).font(fontKey) }
        .append(text("${"\uE100".repeat(4)}${"\uE102"}\uE200").style { it.shadowColor(ShadowColor.none()) })

    init {
        scheduler.submitTask {
            if(timeLeftBeforeStart == 0) {
                if(gameStatus == GameStatus.RUNNING) {
                    if(timeLimit > 0) {
                        timeLimit--
                        bossbar.name(bossBarText())
                        return@submitTask TaskSchedule.seconds(1)
                    }
                    // TODO: Sudden death
                    return@submitTask TaskSchedule.stop()
                }
                if(gameStatus == GameStatus.ENDING) return@submitTask TaskSchedule.stop()
                instance.sendMessage(text("Starting game"))
                gameStatus = GameStatus.RUNNING
                playersAtStartOfGame = instance.players.size
                instance.players.forEach { it.updateViewerRule() }
                return@submitTask TaskSchedule.seconds(1)
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
        removeGame(instance)
    }

    fun endGame() {
        this.gameStatus = GameStatus.ENDING
        resetGame()
        scheduler.submitTask {
            if (this.timeLeftBeforeClose == 0) {
                this.closeGame()
                return@submitTask TaskSchedule.stop()
            }
            this.instance.sendMessage(text("Closing in ").append(text(this.timeLeftBeforeClose).color(NamedTextColor.RED)))
            this.timeLeftBeforeClose--
            return@submitTask TaskSchedule.seconds(1)
        }
    }

    private fun resetGame() {
        playerNPCMap.values.forEach { it.remove() }
        playerNPCMap.clear()
        playerCameraMap.values.forEach { it.remove() }
        playerCameraMap.clear()
        powerMap.replaceAll { _, _ -> 2 }
        instance.players.forEach { p ->
            p.updateViewerRule() //TODO: Fix player not being invisible
            p.hideBossBar(bossbar)
            p.clearEffects()

            val uuids = p.getAttribute(Attribute.MOVEMENT_SPEED).modifiers().stream()
                    .map { obj-> obj.id }
                    .toList().toTypedArray()
            for (uuid in uuids) {
                p.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(uuid!!)
            }
            PowerupTags.entries.forEach {
                p.removeTag(it.getBool())
            }
        }
        instance.entities.stream()
            .filter { e: Entity ->
                e.entityType.id() == EntityType.TNT.id() || e.entityType.id() == EntityType.ITEM.id()
            }
            .forEach { obj: Entity -> obj.remove() }
    }

    fun showBossbar(player: Player) = player.showBossBar(bossbar)
    fun getFakeNPCs() = playerNPCMap.entries
    fun getCamera(player: Player) = playerCameraMap[player.uuid]

    fun spawnPlayer(player: Player) {
        val spawnPoints = map.settings.spawnPoints
        val pos = spawnPoints[playerSpawnCounter % spawnPoints.size].add(.5, .0, .5)
        player.teleport(pos)
        for(i in 0..9) Powerup.SPEED_UP.effect.accept(player)
        playerSpawnCounter++
        val entity = Entity(EntityType.ARMOR_STAND)
        val meta = entity.entityMeta as ArmorStandMeta
        meta.isInvisible = true
        meta.isMarker = true
        meta.isCustomNameVisible = false
        entity.setNoGravity(true)
        var (x, y, z) = pos
        y += 5
        x += (x / abs(x)) * 0.5
        z += (z / abs(z)) * 0.5
        entity.setInstance(instance, Pos(x,y,z, pos.yaw + 90+45, 75f))
        player.spectate(entity)
        playerCameraMap[player.uuid] = entity
        entity.aerodynamics = entity.aerodynamics.withVerticalAirResistance(0.7).withHorizontalAirResistance(0.7)
        playerNPCMap[player.uuid] = FakeNPC.createFakeNPC(instance, pos)
        player.scheduler().submitTask({
            val game = getGame(player.instance) ?: return@submitTask TaskSchedule.stop()
            val npc = game.getFakeNPC(player.uuid) ?: return@submitTask TaskSchedule.stop()
            if(npc.isDead()) return@submitTask TaskSchedule.stop()
            npc.pickupPowerup(game, player)
            val inputs = player.inputs()
            if(inputs.forward()) npc.moveForward(player, game)
            if(inputs.backward()) npc.moveBackward(player, game)
            if(inputs.right()) npc.rotateRight(player, game)
            if(inputs.left()) npc.rotateLeft(player, game)
            if(inputs.jump()) npc.placeTNT(player)
            return@submitTask TaskSchedule.tick(3)
        }, ExecutionType.TICK_START)
    }

    companion object {
        private val games = mutableMapOf<Instance, Game>()

        fun getNonFilledGames(): Game? {
            return games.values.filter { it.instance.players.size < it.map.settings.maxPlayers && it.gameStatus == GameStatus.WAITING }
                .maxByOrNull { it.instance.players.size }
        }

        fun getGame(instance: Instance) = games[instance]

        fun removeGame(instance: Instance) {
            games.remove(instance)
        }

        fun createGame(manager: InstanceManager, map: GameMap): InstanceContainer {
            val container = createInstanceContainer(manager)
            container.setTag(Tag.Boolean("game"), true)
            val game = Game(container, map)
            generateStructure(container, game)
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

private operator fun Pos.component1(): Double = x
private operator fun Pos.component2(): Double = y
private operator fun Pos.component3(): Double = z
