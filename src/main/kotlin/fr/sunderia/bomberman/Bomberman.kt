package fr.sunderia.bomberman

import com.google.gson.Gson
import fr.sunderia.bomberman.InstanceCreator.Companion.createInstanceContainer
import fr.sunderia.bomberman.party.GameCommand
import fr.sunderia.bomberman.party.PartyCommand
import fr.sunderia.bomberman.utils.FakeNPC
import fr.sunderia.bomberman.utils.NPC
import fr.sunderia.bomberman.utils.ResourceUtils
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.extras.lan.OpenToLAN
import net.minestom.server.extras.velocity.VelocityProxy
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.registry.DynamicRegistry
import net.minestom.server.scoreboard.TeamBuilder
import net.minestom.server.utils.NamespaceID
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
        FakeNPC.createFakeNPC(lobbyContainer, Pos(5.0, 40.0, 10.0))
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