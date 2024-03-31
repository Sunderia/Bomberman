package fr.sunderia.bomberman

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import fr.sunderia.bomberman.party.PartyCommand
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.kyori.adventure.key.Key
import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.attribute.Attribute
import net.minestom.server.attribute.AttributeModifier
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.event.player.*
import net.minestom.server.extras.lan.OpenToLAN
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.SetCooldownPacket
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.NamespaceID
import net.minestom.server.utils.chunk.ChunkUtils
import net.minestom.server.world.DimensionType
import org.apache.commons.codec.digest.DigestUtils
import org.jglrxavpok.hephaistos.nbt.NBTException
import org.jglrxavpok.hephaistos.nbt.NBTReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.*
import java.util.function.Consumer
import java.util.logging.Level
import java.util.logging.Logger
import java.util.stream.IntStream
import kotlin.random.Random

fun main() {
    val game = Bomberman()
    val mc = MinecraftServer.init()
    game.initialize()
    mc.start("0.0.0.0", 25565)
    game.terminate()
}

class Bomberman {

    private var resourcePackSha1: String? = null

    companion object {
        val powerMap = mutableMapOf<UUID, Int>()
        private val gson = GsonBuilder().create()
        val logger: Logger = Logger.getLogger("Bomberman")
    }

    fun initialize() {
        val manager = MinecraftServer.getInstanceManager()
        val lobbyContainer: InstanceContainer = createInstanceContainer(manager)
        OpenToLAN.open()
        //MojangAuth.init()
        registerListeners(lobbyContainer)
        MinecraftServer.getCommandManager().register(PartyCommand())

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

    private fun registerListeners(container: InstanceContainer) {
        val spawn = Pos(.0, 45.0, .0)
        val extensionNode = MinecraftServer.getGlobalEventHandler()
        val lobbyNode = EventNode.all("lobby")
        val gameNode = EventNode.all("game")
        extensionNode.addListener(AsyncPlayerConfigurationEvent::class.java) { it.spawningInstance = container }
        //extensionNode.addListener(PlayerSkinInitEvent::class.java) { it.skin = PlayerSkin.fromUuid(it.player.uuid.toString()) }

        extensionNode.addListener(PlayerChatEvent::class.java) {
            val gameMode = GameMode.entries.find { gm -> it.message.uppercase().contains(gm.name) } ?: return@addListener
            it.isCancelled = true
            it.player.gameMode = gameMode
        }

        gameNode.addListener(PickupItemEvent::class.java) {
            if(!it.instance.hasTag(Tag.Boolean("game"))) return@addListener
            if (it.livingEntity !is Player || it.itemStack.material().id() != Material.NAUTILUS_SHELL.id()) return@addListener
            val player = it.entity as Player
            if (player.gameMode != GameMode.ADVENTURE) {
                it.isCancelled = true
                return@addListener
            }
            Powerup.entries[it.itemStack.meta().customModelData - 1].effect.accept(player)
        }

        gameNode.addListener(PlayerSpawnEvent::class.java) {
            //if(!it.spawnInstance.hasTag(Tag.Boolean("game"))) return@addListener
            val player = it.player
            player.gameMode = GameMode.ADVENTURE
            player.teleport(spawn)
            player.respawnPoint = spawn
            player.inventory.addItemStack(ItemStack.of(Material.TNT).withMeta { it.canPlaceOn(Block.STONE, Block.BRICKS).build() } )

            powerMap[player.uuid] = 2
            player.scheduler().scheduleTask({
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


        gameNode.addListener(PlayerDeathEvent::class.java) {
            if(!it.instance.hasTag(Tag.Boolean("game"))) return@addListener
            val player = it.player
            player.gameMode = GameMode.SPECTATOR
            val playerAlive = player.instance!!.players.filter { it.gameMode == GameMode.ADVENTURE }.toList()
            if(playerAlive.size != 1) return@addListener
            val winner = playerAlive[0]

            player.instance!!.players.filter { it.gameMode != GameMode.ADVENTURE }.forEach {
                if(it.isDead) it.respawn()
                it.sendMessage(Component.text("${winner.username} Won"))
                it.teleport(spawn)
                it.gameMode = GameMode.ADVENTURE
            }

            winner.sendTitlePart(TitlePart.TITLE, Component.text("You won", NamedTextColor.GREEN))
            winner.teleport(spawn)
            generateStructure(winner.instance)
            resetGame(winner.instance)
        }

        gameNode.addListener(PlayerBlockPlaceEvent::class.java) {
            if(!it.instance.hasTag(Tag.Boolean("game"))) return@addListener
            if(it.block.id() != Block.TNT.id()) return@addListener
            it.isCancelled = true

            val player = it.player
            if(player.gameMode != GameMode.ADVENTURE && player.gameMode != GameMode.CREATIVE) return@addListener

            val blockBelowPlayer = player.instance!!.getBlock(it.blockPosition.sub(.0, 1.0, .0))
            if(blockBelowPlayer.id() != Block.STONE.id() || player.instance!!.getBlock(it.blockPosition.add(.0, 1.0, .0)).isSolid) return@addListener
            if (Cooldown.isInCooldown(it.player.uuid, "tnt")) return@addListener

            it.isCancelled = false
            it.player.playerConnection.sendPacket(SetCooldownPacket(Material.TNT.id(), 0))
            it.consumeBlock(false)
            it.block = Block.BARRIER

            if (player.gameMode == GameMode.ADVENTURE) {
                val timeInSeconds = 1
                val c = Cooldown(it.player.uuid, "tnt", timeInSeconds)
                c.start()
                val packet = SetCooldownPacket(Material.TNT.id(), timeInSeconds * 20)
                it.player.playerConnection.sendPacket(packet)
            }
            val tnt = PrimedTntEntity(it.player)
            tnt.setInstance(it.player.instance!!, it.blockPosition.add(0.5, 0.0, 0.5))
        }

        extensionNode.addChild(lobbyNode)
        extensionNode.addChild(gameNode)
    }

    private fun createInstanceContainer(manager: InstanceManager): InstanceContainer {
        val fullBright = DimensionType.builder(NamespaceID.from("sunderia:full_bright")).ambientLight(2.0f).build()
        MinecraftServer.getDimensionTypeManager().addDimension(fullBright)
        val container = manager.createInstanceContainer(fullBright)
        container.setGenerator { unit: GenerationUnit ->
            unit.modifier().fillHeight(0, 40, Block.STONE)
        }
        return container
    }

    private fun createGameInstance(manager: InstanceManager): InstanceContainer {
        val container = createInstanceContainer(manager)
        container.setTag(Tag.Boolean("game"), true)
        generateStructure(container)
        return container
    }

    private fun resetGame(instance: Instance?) {
        powerMap.replaceAll { _: UUID?, _: Int? -> 2 }
        instance!!.players.forEach(Consumer { p: Player ->
            p.clearEffects()
            val uuids =
                p.getAttribute(Attribute.MOVEMENT_SPEED).modifiers.stream()
                    .map { obj: AttributeModifier -> obj.id }
                    .toList().toTypedArray()
            for (uuid in uuids) {
                p.getAttribute(Attribute.MOVEMENT_SPEED).removeModifier(uuid!!)
            }
        })
        instance.entities.stream()
            .filter { e: Entity ->
                e.entityType.id() == EntityType.TNT.id() || e.entityType.id() == EntityType.ITEM.id()
            }
            .forEach { obj: Entity -> obj.remove() }
    }

    private fun generateStructure(container: Instance?) {
        val (size, blocks1) = parseNBT() ?: return
        val startPos = Pos(0.0, 0.0, 0.0).sub(size.div(2.0)).withY(40.0)
        val batch = AbsoluteBlockBatch()
        for ((vec, block) in blocks1) {
            if (block.isAir) continue
            if (block.id() == Block.BRICKS.id() && Random.nextInt(3) == 2) continue
            batch.setBlock(startPos.add(vec), block)
            if (block.id() == Block.BRICKS.id()) {
                batch.setBlock(startPos.add(vec.add(0.0, 1.0, 0.0)), Block.BARRIER)
            }
        }
        getAffectedChunks(batch)?.let {
            ChunkUtils.optionalLoadAll(container!!, it, null)
                .thenRun { batch.apply(container) { batch.clear() } }
        }
    }

    private fun parseNBT(): Structure? {
        try {
            Bomberman::class.java.getResourceAsStream("/bomberman.nbt").use { stream ->
                NBTReader(stream!!).use { reader ->
                    val structure: MutableList<BlockPos> = LinkedList()
                    val nbt = gson.fromJson(
                        reader.read().toSNBT(),
                        JsonObject::class.java
                    )
                    val palettes = nbt.getAsJsonArray("palette")
                    val palette =
                        IntStream.range(0, palettes.size())
                            .mapToObj { i: Int ->
                                palettes[i]
                            }.map { obj: JsonElement -> obj.asJsonObject }
                            .map { obj: JsonObject ->
                                obj.getAsJsonPrimitive(
                                    "Name"
                                ).asString
                            }
                            .map<Block?> { namespaceID: String? ->
                                Block.fromNamespaceId(
                                    namespaceID!!
                                )
                            }.toList().toTypedArray()
                    val blockArray = nbt.getAsJsonArray("blocks")
                    blockArray.forEach(Consumer { el: JsonElement ->
                        val blockObj = el.asJsonObject
                        val jsonPos = blockObj.getAsJsonArray("pos")
                        structure.add(
                            BlockPos(
                                Vec(
                                    jsonPos[0].asInt.toDouble(),
                                    jsonPos[1].asInt.toDouble(),
                                    jsonPos[2].asInt.toDouble()
                                ),
                                palette[blockObj["state"].asInt]
                            )
                        )
                    })
                    val size = IntArray(3)
                    val jsonSize = nbt.getAsJsonArray("size")
                    for (i in 0..2) size[i] = jsonSize[i].asInt
                    return Structure(
                        Vec(
                            jsonSize[0].asInt.toDouble(),
                            jsonSize[1].asInt.toDouble(),
                            jsonSize[2].asInt.toDouble()
                        ), structure
                    )
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: NBTException) {
            e.printStackTrace()
        }
        return null
    }

    private fun getAffectedChunks(batch: AbsoluteBlockBatch): LongArray? {
        return try {
            val field = batch.javaClass.getDeclaredField("chunkBatchesMap")
            field.isAccessible = true
            val chunkBatchesMap = field[batch] as Long2ObjectMap<*>
            chunkBatchesMap.keys.toLongArray()
        } catch (e: NoSuchFieldException) {
            throw RuntimeException(e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException(e)
        }
    }

    fun terminate() {

    }
}