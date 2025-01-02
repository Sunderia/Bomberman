package fr.sunderia.bomberman

import fr.sunderia.bomberman.Bomberman.Companion.fontKey
import fr.sunderia.bomberman.Bomberman.Companion.powerMap
import fr.sunderia.bomberman.party.Game
import fr.sunderia.bomberman.party.GameStatus
import fr.sunderia.bomberman.party.Party
import fr.sunderia.bomberman.utils.NPC
import fr.sunderia.bomberman.utils.PositionUtils.Companion.removeBlockAt
import fr.sunderia.bomberman.utils.PositionUtils.Companion.setBlockAt
import fr.sunderia.bomberman.utils.PowerupTags
import net.kyori.adventure.key.Key
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.PlayerHand
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.*
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import java.net.URI
import kotlin.math.abs

class Listeners {
    companion object {
        @Suppress("NestedLambdaShadowedImplicitParameter")
        fun registerListeners(container: InstanceContainer) {
            val scheduleManager = MinecraftServer.getSchedulerManager()
            val spawn = Pos(.0, 45.0, .0)
            val extensionNode = MinecraftServer.getGlobalEventHandler()
            val lobbyNode = EventNode.all("lobby")
            val gameNode = EventNode.all("game")
            extensionNode.addListener(AsyncPlayerConfigurationEvent::class.java) { it.spawningInstance = container }
            //extensionNode.addListener(PlayerSkinInitEvent::class.java) { it.skin = PlayerSkin.fromUuid(it.player.uuid.toString()) }

            extensionNode.addListener(PlayerChatEvent::class.java) {
                if (it.player.username != "minemobs") return@addListener
                val gameMode =
                    GameMode.entries.find { gm -> it.rawMessage.uppercase().contains(gm.name) } ?: return@addListener
                it.isCancelled = true
                it.player.gameMode = gameMode
            }

            extensionNode.addListener(PlayerDisconnectEvent::class.java) {
                Party.removePlayerFromParty(it.player)
                val instance = it.instance
                if (!it.instance.hasTag(Tag.Boolean("game"))) return@addListener
                val filter = instance.players.filter { p -> p.uuid != it.player.uuid }
                if (filter.isEmpty()) {
                    Game.removeGame(instance)
                } else if (filter.filter { p -> p.gameMode == GameMode.ADVENTURE }.size == 1) {
                    Game.playerLeft(instance)
                }
            }

            lobbyNode.addListener(PlayerEntityInteractEvent::class.java) {
                if (it.instance.hasTag(Tag.Boolean("game"))) return@addListener
                if (it.target !is NPC) return@addListener
                if (it.hand != PlayerHand.MAIN) return@addListener
                (it.target as NPC).onInteract(it)
            }

            gameNode.addListener(PlayerHandAnimationEvent::class.java) {
                if (it.player.gameMode != GameMode.SPECTATOR) return@addListener
                if (it.hand != PlayerHand.MAIN) return@addListener
                if (!it.player.hasTag(PowerupTags.BOXING_GLOVE.getBool())) return@addListener
                it.isCancelled = true
                val game = Game.getGame(it.instance) ?: return@addListener
                val npc = game.getFakeNPC(it.player.uuid) ?: return@addListener
                val (pos, blockInFront) = npc.getBlockInFront()
                if(pos == null || blockInFront == null) return@addListener
                if(blockInFront.id() != Block.BARRIER.id()) return@addListener
                val tnt = it.instance.entities
                    .filterIsInstance<PrimedTntEntity>()
                    .firstOrNull {  it.position.sameBlock(pos) } ?: return@addListener
                val headPos = npc.head.position.withY(40.0)
                val lastPos = getFurthestAirBlock(headPos, it.instance)
                removeBlockAt(tnt.position, it.instance)
                removeBlockAt(tnt.position.add(.0, 1.0, .0), it.instance)
                val lastAirBlock = tnt.position.sub(lastPos).addAll()
                val oldY = tnt.position.y
                val tickUpdate = 3
                val blockPerTicks = 1.0 / tickUpdate
                var relativeX = 0.0
                scheduleManager.scheduleTask({
                    if (tnt.instance == null) {
                        return@scheduleTask TaskSchedule.stop()
                    }
                    relativeX += blockPerTicks
                    if (abs(relativeX) >= abs(lastAirBlock)) {
                        setBlockAt(tnt.position, Block.BARRIER, tnt.instance)
                        return@scheduleTask TaskSchedule.stop()
                    }
                    val direction = headPos.direction().mul(-1.0)
                    tnt.teleport(
                        tnt.position.add(direction.x * blockPerTicks, .0, direction.z * blockPerTicks)
                            .withY(tnt.calculateNextY(relativeX, abs(lastAirBlock)) + oldY)
                    )
                    return@scheduleTask TaskSchedule.nextTick()
                }, TaskSchedule.immediate())
            }

            gameNode.addListener(PlayerBlockPlaceEvent::class.java) {
                it.isCancelled = true
            }

            gameNode.addListener(PlayerSpawnEvent::class.java) {
                val player = it.player
                player.gameMode = GameMode.ADVENTURE
                player.teleport(spawn)
                player.respawnPoint = spawn
                if (it.isFirstSpawn) {
                    player.updateViewerRule { p ->
                        if (p !is Player) return@updateViewerRule true
                        val game = Game.getGame(p.instance) ?: return@updateViewerRule true
                        if (game.gameStatus != GameStatus.RUNNING) return@updateViewerRule true
                        false
                    }
                    //Disables Death screen
                    player.sendPacket(ChangeGameStatePacket(ChangeGameStatePacket.Reason.ENABLE_RESPAWN_SCREEN, 1f))
                    it.player.sendMessage(
                        Component.text("Join a game with ")
                            .append(
                                Component.text("/game", NamedTextColor.GREEN)
                                    .hoverEvent(HoverEvent.showText(Component.text("Will execute the command /game")))
                                    .clickEvent(ClickEvent.runCommand("/game"))
                            )
                    )
                    player.sendResourcePacks(
                        ResourcePackRequest.resourcePackRequest().packs(
                            ResourcePackInfo.resourcePackInfo()
                                .hash(Bomberman.resourcePackSha1)
                                .uri(URI.create("https://raw.githubusercontent.com/Sunderia/Bomberman/main/bomberman.zip"))
                        )
                    )
                }
                player.updateViewerRule()
                if (!it.spawnInstance.hasTag(Tag.Boolean("game"))) return@addListener
                val game = Game.getGame(player.instance)!!
                player.gameMode = GameMode.SPECTATOR
                game.spawnPlayer(player)
                player.lookAt(Pos(.0, 40.0, .0))
                player.inventory.addItemStack(ItemStack.of(Material.TNT))

                powerMap[player.uuid] = 2
                game.showBossbar(player)
                player.scheduler().scheduleTask({
                    if (!player.instance.hasTag(Tag.Boolean("game"))) return@scheduleTask
                    val hasBoxingGlove = player.hasTag(PowerupTags.BOXING_GLOVE.getBool())
                    val hasPierce = player.hasTag(PowerupTags.PIERCE.getBool())
                    val playerLeftCount = player.instance.players.filter { it.gameMode == GameMode.ADVENTURE }.size
                    player.sendActionBar(
                        Component.join(
                            JoinConfiguration.separator(Component.text(" ")),
                            (Component.text("\uE100".repeat(3) + "Nn${playerLeftCount}"))
                                .style { it.font(fontKey).color(TextColor.color(0x3804f9)) },
                            Component.text("\u0020".repeat(5)),
                            Component.text("\uE000").style { it.font(fontKey) },
                            Component.text(": ${powerMap[player.uuid]}"),
                            Component.text(" ${if (hasBoxingGlove) "✔" else "❌"} ")
                                .color(if (hasBoxingGlove) NamedTextColor.GREEN else NamedTextColor.RED),
                            Component.text("\uE001").style { it.font(fontKey) },
                            Component.text(" ${if (hasPierce) "✔" else "❌"} ")
                                .color(if (hasPierce) NamedTextColor.GREEN else NamedTextColor.RED),
                            Component.text("\uE002").style { it.font(fontKey) }
                        ).style { it.font(Key.key("default")) })
                }, TaskSchedule.immediate(), TaskSchedule.tick(10))
            }

            gameNode.addListener(PlayerDeathEvent::class.java) { event ->
                if (!event.instance.hasTag(Tag.Boolean("game"))) return@addListener
                val player = event.player
                player.gameMode = GameMode.SPECTATOR
                val playerAlive = player.instance.players.filter { it.gameMode == GameMode.ADVENTURE }.toList()
                if (playerAlive.size != 1) return@addListener
                val winner = playerAlive[0]

                player.instance.players.forEach {
                    if (it.isDead) it.respawn()
                    it.sendMessage(Component.text("${winner.username} Won"))
                    it.teleport(spawn)
                    it.gameMode = GameMode.SPECTATOR
                }

                winner.sendTitlePart(TitlePart.TITLE, Component.text("You won", NamedTextColor.GREEN))
                winner.teleport(spawn)
                val game = Game.getGame(event.instance)!!
                game.endGame()
            }

            extensionNode.addChild(lobbyNode)
            extensionNode.addChild(gameNode)
        }

        private fun getFurthestAirBlock(pos: Pos, instance: Instance): Pos {
            var lastPos: Pos = pos
            var iter = 0
            val direction = pos.direction().mul(-1.0)
            while (instance.getBlock(lastPos.add(direction)).isAirOrBarrier() && iter < 10) {
                lastPos = lastPos.add(direction)
                iter++
            }
            return lastPos
        }

        private fun Block.isAirOrBarrier() = this.isAir || this.compare(Block.BARRIER)
        private fun Pos.addAll() = this.x + this.y + this.z
    }
}