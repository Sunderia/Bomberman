package fr.sunderia.bomberman

import com.google.gson.Gson
import fr.sunderia.bomberman.InstanceCreator.Companion.createInstanceContainer
import fr.sunderia.bomberman.party.*
import fr.sunderia.bomberman.utils.NPC
import fr.sunderia.bomberman.utils.ResourceUtils
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.display.ItemDisplayMeta
import net.minestom.server.extras.lan.OpenToLAN
import net.minestom.server.extras.velocity.VelocityProxy
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.component.HeadProfile
import net.minestom.server.registry.DynamicRegistry
import net.minestom.server.scoreboard.TeamBuilder
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import org.apache.commons.codec.digest.DigestUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.Map

fun main() {
    val game = Bomberman()
    if(System.getenv().containsKey("VELOCITY_SECRET")) VelocityProxy.enable(System.getenv("VELOCITY_SECRET"))
    val mc = MinecraftServer.init()
    game.initialize()
    mc.start("0.0.0.0", 25565)
}

class Bomberman {

    companion object {
        val gson = Gson()
        val logger: Logger = Logger.getLogger("Bomberman")

        val fullBrightType = DimensionType.builder().ambientLight(2.0f).build()
        lateinit var fullbright: DynamicRegistry.Key<DimensionType>

        val fontKey = Key.key("bomberman", "font")
        lateinit var resourcePackSha1: String

        val powerMap = mutableMapOf<UUID, Int>()
        private lateinit var maps: Map<String, GameMap>
        private lateinit var lobbyInstance: Instance

        fun getLobbyInstance(): Instance {
            return lobbyInstance
        }
        fun getAvailableMaps(): Map<String, GameMap> = maps
        fun getMaxAmountOfPlayers(): Int = maps.values.maxOf { it.settings.maxPlayers }
    }

    private fun createPlayerPart(model: String, profile: HeadProfile, pos: Pos, translation: Point, lobby: Instance) {
        val entity = Entity(EntityType.ITEM_DISPLAY)
        entity.setNoGravity(true)
        val meta = entity.entityMeta as ItemDisplayMeta
        meta.displayContext = ItemDisplayMeta.DisplayContext.THIRD_PERSON_RIGHT_HAND
        meta.viewRange = .6f
        meta.leftRotation = floatArrayOf(.0f, .0f, .0f, 1f)
        meta.rightRotation = floatArrayOf(.0f, .0f, .0f, 1f)
        meta.scale = Vec(1.0, 1.0, 1.0)
        meta.translation = translation
        val headItem = ItemStack.of(Material.PLAYER_HEAD).withItemModel("player_display:player/${model}").with(ItemComponent.PROFILE, profile)
        meta.itemStack = headItem
        entity.setInstance(lobby, pos)
    }

    private fun createFakeNPC(lobby: Instance) {
        val map = mapOf(
            "head" to (Pos(.0, 1.4, .0) to Pos.ZERO), "torso" to (Pos(.0, 1.4, .0) to Pos(.0, -3072.0, .0)),
            "right_arm" to (Pos(.0, 1.4, .0) to Pos(.0, -1024.0, .0)), "left_arm" to (Pos(.0, 1.4, .0) to Pos(.0, -2048.0, .0)),
            "right_leg" to (Pos(.0, .7, .0) to Pos(.0, -4096.0, .0)), "left_leg" to (Pos(.0, .7, .0) to Pos(.0, -5120.0, .0)))

        for ((key, value) in map) {
            val (relativePos, transform) = value
            createPlayerPart(key, HeadProfile(NPC.BOMBERMAN_SKIN), Pos(5.0, 40.0, 10.0).add(relativePos), transform, lobby)
        }
    }

    fun initialize() {
        val uri = Bomberman::class.java.getResource("/maps/")?.toURI() ?: throw IOException("Could not find maps folder")
        val paths = ResourceUtils.getPaths(uri)
        logger.info("Found ${paths.size} maps")
        maps = paths.map {
            val name = it.fileName.toString().replace(".json", "")
            logger.info("Loading map $name")
            name to GameMap(name)
        }.toList().toMap()
        val manager = MinecraftServer.getInstanceManager()
        fullbright = MinecraftServer.getDimensionTypeRegistry().register(NamespaceID.from("sunderia:full_bright"), fullBrightType)
        val lobbyContainer: InstanceContainer = createInstanceContainer(manager)
        lobbyInstance = lobbyContainer
        NPC.createNPC(lobbyContainer)
        createFakeNPC(lobbyContainer)
        OpenToLAN.open()
        //MojangAuth.init()
        Listeners.registerListeners(lobbyContainer)
        val commandManager = MinecraftServer.getCommandManager()
        commandManager.register(PartyCommand())
        commandManager.register(GameCommand())

        PrimedTntEntity.pierceTeam = TeamBuilder("Pierce TNT", MinecraftServer.getTeamManager()).teamColor(NamedTextColor.AQUA).build()

        try {
            ByteArrayOutputStream().use { os ->
                URI.create("https://raw.githubusercontent.com/Sunderia/Bomberman/main/bomberman.zip").toURL().openStream().use { input ->
                    input.transferTo(os)
                    resourcePackSha1 = String(DigestUtils.getSha1Digest().digest(os.toByteArray()))
                }
            }
        } catch (e: IOException) {
            logger.log(Level.SEVERE, e) { "Couldn't get the SHA1 of the Resource Pack" }
        }
        logger.info("Bomberman starting")
    }
}