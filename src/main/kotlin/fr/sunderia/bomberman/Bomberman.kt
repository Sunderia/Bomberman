package fr.sunderia.bomberman

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import fr.sunderia.bomberman.InstanceCreator.Companion.createInstanceContainer
import fr.sunderia.bomberman.party.Game
import fr.sunderia.bomberman.party.GameCommand
import fr.sunderia.bomberman.party.Party
import fr.sunderia.bomberman.party.PartyCommand
import fr.sunderia.bomberman.utils.Axis
import fr.sunderia.bomberman.utils.Cooldown
import fr.sunderia.bomberman.utils.PositionUtils.Companion.removeBlockAt
import fr.sunderia.bomberman.utils.PositionUtils.Companion.setBlockAt
import fr.sunderia.bomberman.utils.PowerupTags
import net.kyori.adventure.key.Key
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.player.*
import net.minestom.server.extras.lan.OpenToLAN
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.block.BlockFace
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.listener.PlayerDiggingListener
import net.minestom.server.network.NetworkBuffer
import net.minestom.server.network.packet.client.play.ClientPlayerDiggingPacket
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket
import net.minestom.server.network.packet.server.play.SetCooldownPacket
import net.minestom.server.scoreboard.TeamBuilder
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.PacketUtils
import net.minestom.server.world.DimensionType
import org.apache.commons.codec.digest.DigestUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger


fun main() {
    val game = Bomberman()
    val mc = MinecraftServer.init()
    game.initialize()
    mc.start("0.0.0.0", 25565)
}

class Bomberman {

    private var resourcePackSha1: String? = null

    companion object {
        val powerMap = mutableMapOf<UUID, Int>()
        val gson: Gson = GsonBuilder().create()
        val logger: Logger = Logger.getLogger("Bomberman")
        private lateinit var lobbyInstance: Instance
        fun getLobbyInstance(): Instance {
            return lobbyInstance
        }
        val fullBright: DimensionType = DimensionType.builder(NamespaceID.from("sunderia:full_bright")).ambientLight(2.0f).build()
    }

    fun initialize() {
        val manager = MinecraftServer.getInstanceManager()
        MinecraftServer.getDimensionTypeManager().addDimension(fullBright)
        val lobbyContainer: InstanceContainer = createInstanceContainer(manager)
        lobbyInstance = lobbyContainer
        OpenToLAN.open()
        //MojangAuth.init()
        registerListeners(lobbyContainer)
        val commandManager = MinecraftServer.getCommandManager()
        commandManager.register(PartyCommand())
        commandManager.register(GameCommand())

        PrimedTntEntity.pierceTeam = TeamBuilder("Pierce TNT", MinecraftServer.getTeamManager()).teamColor(NamedTextColor.AQUA).build()

        try {
            ByteArrayOutputStream().use { os ->
                URI.create("https://raw.githubusercontent.com/Sunderia/Bomberman/main/bomberman.zip").toURL().openStream().use { input ->
                    input.transferTo(os)
                    this.resourcePackSha1 = String(DigestUtils.getSha1Digest().digest(os.toByteArray()))
                }
            }
        } catch (e: IOException) {
            logger.log(Level.SEVERE, e) { "Couldn't get the SHA1 of the Resource Pack" }
        }
        logger.info("Bomberman starting")
    }

    @Suppress("UnstableApiUsage", "NestedLambdaShadowedImplicitParameter")
    private fun registerListeners(container: InstanceContainer) {
        val spawn = Pos(.0, 45.0, .0)
        val extensionNode = MinecraftServer.getGlobalEventHandler()
        val lobbyNode = EventNode.all("lobby")
        val gameNode = EventNode.all("game")
        extensionNode.addListener(AsyncPlayerConfigurationEvent::class.java) { it.spawningInstance = container }
        //extensionNode.addListener(PlayerSkinInitEvent::class.java) { it.skin = PlayerSkin.fromUuid(it.player.uuid.toString()) }

        /*extensionNode.addListener(PlayerChatEvent::class.java) {
            val gameMode = GameMode.entries.find { gm -> it.message.uppercase().contains(gm.name) } ?: return@addListener
            it.isCancelled = true
            it.player.gameMode = gameMode
        }*/

        extensionNode.addListener(PlayerDisconnectEvent::class.java) {
            Party.removePlayerFromParty(it.player)
            val instance = it.instance
            if(!it.instance.hasTag(Tag.Boolean("game"))) return@addListener
            val filter = instance.players.filter { p -> p.uuid != it.player.uuid }
            if(filter.isEmpty()) {
                Game.removeGame(instance)
            } else if(filter.filter { p -> p.gameMode == GameMode.ADVENTURE }.size == 1 ) {
               Game.playerLeft(instance)
            }
        }
        
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientPlayerDiggingPacket::class.java) { packet: ClientPlayerDiggingPacket, player: Player ->
            val instance = player.instance
            if(player.gameMode != GameMode.ADVENTURE || packet.status != ClientPlayerDiggingPacket.Status.STARTED_DIGGING) return@setPlayListener PlayerDiggingListener.playerDiggingListener(packet, player)
            logger.info("Digging")
            if(!player.hasTag(PowerupTags.BOXING_GLOVE.getBool())) return@setPlayListener
            logger.info("Has effect: ")
            if(!instance.getBlock(packet.blockPosition).compare(Block.BARRIER)) return@setPlayListener
            if(packet.blockFace == BlockFace.TOP || packet.blockFace == BlockFace.BOTTOM) return@setPlayListener
            val tnt = instance.entities
                    .filterIsInstance<PrimedTntEntity>()
                    .firstOrNull { it.position.sameBlock(packet.blockPosition) } ?: return@setPlayListener
            val lastPos = when (packet.blockFace) {
                BlockFace.NORTH -> {
                    getFurthestAirBlock(Axis.Z, 1.0, tnt.position, tnt.instance)
                }
                BlockFace.WEST -> {
                    getFurthestAirBlock(Axis.X, 1.0, tnt.position, tnt.instance)
                }
                BlockFace.SOUTH -> {
                    getFurthestAirBlock(Axis.Z, -1.0, tnt.position, tnt.instance)
                }
                else -> {
                    getFurthestAirBlock(Axis.X, -1.0, tnt.position, tnt.instance)
                }
            }
            removeBlockAt(tnt.position, tnt.instance)
            tnt.teleport(lastPos)
            setBlockAt(tnt.position, Block.BARRIER, tnt.instance)
        }

        gameNode.addListener(PickupItemEvent::class.java) {
            if(!it.instance.hasTag(Tag.Boolean("game"))) return@addListener
            if(it.livingEntity !is Player || it.itemStack.material().id() != Material.NAUTILUS_SHELL.id()) return@addListener
            val player = it.entity as Player
            if (player.gameMode != GameMode.ADVENTURE) {
                it.isCancelled = true
                return@addListener
            }
            Powerup.entries[it.itemStack.meta().customModelData - 1].effect.accept(player)
        }

        gameNode.addListener(PlayerSpawnEvent::class.java) {
            val player = it.player
            player.gameMode = GameMode.ADVENTURE
            player.teleport(spawn)
            player.respawnPoint = spawn
            if(it.isFirstSpawn) {
                //Disables Death screen
                val buffer = NetworkBuffer()
                buffer.write(NetworkBuffer.BYTE, 11)
                buffer.write(NetworkBuffer.FLOAT, 1f)
                val packet = ChangeGameStatePacket(buffer)
                PacketUtils.sendPacket(player, packet)
            }
            if(!it.spawnInstance.hasTag(Tag.Boolean("game"))) return@addListener
            player.inventory.addItemStack(ItemStack.of(Material.TNT).withMeta { it.canPlaceOn(Block.STONE, Block.BRICKS).canDestroy(Block.BARRIER).build() } )

            powerMap[player.uuid] = 2
            player.scheduler().scheduleTask({
                if(!player.instance.hasTag(Tag.Boolean("game"))) return@scheduleTask
                player.sendActionBar(
                    Component.join(JoinConfiguration.separator(Component.text(" ")),
                        Component.text("\uE000").style { it.font(Key.key("bomberman", "font")) },
                        Component.text(": ${powerMap[player.uuid]}")
                    ).style { it.font(Key.key("default")) })
            }, TaskSchedule.immediate(), TaskSchedule.tick(10))
            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest().packs(ResourcePackInfo.resourcePackInfo()
                    .hash(resourcePackSha1!!)
                    .uri(URI.create("https://raw.githubusercontent.com/Sunderia/Bomberman/main/bomberman.zip"))
            ))
        }

        gameNode.addListener(PlayerDeathEvent::class.java) { event ->
            if(!event.instance.hasTag(Tag.Boolean("game"))) return@addListener
            val player = event.player
            player.gameMode = GameMode.SPECTATOR
            val playerAlive = player.instance.players.filter { it.gameMode == GameMode.ADVENTURE }.toList()
            if(playerAlive.size != 1) return@addListener
            val winner = playerAlive[0]

            player.instance.players.forEach {
                if(it.isDead) it.respawn()
                it.sendMessage(Component.text("${winner.username} Won"))
                it.teleport(spawn)
                //it.gameMode = GameMode.ADVENTURE
                it.gameMode = GameMode.SPECTATOR
            }

            /*generateStructure(winner.instance)
            resetGame(winner.instance)*/
            winner.sendTitlePart(TitlePart.TITLE, Component.text("You won", NamedTextColor.GREEN))
            winner.teleport(spawn)
            val game = Game.getGame(event.instance)!!
            game.endGame()
        }

        gameNode.addListener(PlayerBlockPlaceEvent::class.java) {
            if(!it.instance.hasTag(Tag.Boolean("game"))) return@addListener
            if(it.block.id() != Block.TNT.id()) return@addListener
            it.isCancelled = true

            val player = it.player
            if(player.gameMode != GameMode.ADVENTURE && player.gameMode != GameMode.CREATIVE) return@addListener

            val blockBelowPlayer = player.instance.getBlock(it.blockPosition.sub(.0, 1.0, .0))
            if(blockBelowPlayer.id() != Block.STONE.id() || player.instance.getBlock(it.blockPosition.add(.0, 1.0, .0)).isSolid) return@addListener
            if (Cooldown.isInCooldown(it.player.uuid, "tnt")) return@addListener

            it.isCancelled = false
            it.player.playerConnection.sendPacket(SetCooldownPacket(Material.TNT.id(), 0))
            it.consumeBlock(false)
            it.block = Block.BARRIER

            if (player.gameMode == GameMode.ADVENTURE) {
                val timeInSeconds = 2
                val c = Cooldown(it.player.uuid, "tnt", timeInSeconds)
                c.start()
                val packet = SetCooldownPacket(Material.TNT.id(), timeInSeconds * 20)
                it.player.playerConnection.sendPacket(packet)
            }
            val tnt = PrimedTntEntity(it.player, it.instance, it.blockPosition.add(0.5, 0.0, 0.5))
            if(player.hasTag(PowerupTags.PIERCE.getBool())) {
                tnt.setPierce(true)
            }
        }

        extensionNode.addChild(lobbyNode)
        extensionNode.addChild(gameNode)
    }

    private fun getFurthestAirBlock(axis: Axis, add: Double, pos: Pos, instance: Instance): Pos {
        var lastPos: Pos = pos
        var iter = 0
        val addPos = Pos(if(axis == Axis.X) add else .0, .0, if(axis == Axis.Z) add else .0)
        while(instance.getBlock(lastPos.add(addPos)).isAirOrBarrier() && iter < 10) {
            lastPos = lastPos.add(addPos)
            iter++
        }
        return lastPos
    }

    private fun Block.isAirOrBarrier() = this.isAir || this.compare(Block.BARRIER)
}