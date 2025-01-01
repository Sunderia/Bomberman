package fr.sunderia.bomberman

import fr.sunderia.bomberman.utils.BlockPos
import fr.sunderia.bomberman.utils.Structure
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block
import java.io.IOException
import java.util.stream.IntStream
import kotlin.random.Random

class GameMap(name: String) {

    val settings: MapSettings

    data class MapSettings(val maxPlayers: Int, val minPlayers: Int, val path: String, val spawnPoints: List<Pos>)

    init {
        settings = Bomberman::class.java.getResourceAsStream("/maps/$name.json")!!.reader()
            .use { Bomberman.gson.fromJson(it.readText(), MapSettings::class.java) }
    }

    fun parseNBT() = parseNBT("/maps/${settings.path}")

    private fun parseNBT(path: String): Structure? {
        try {
            Bomberman::class.java.getResourceAsStream(path).use { stream ->
                val reader = BinaryTagIO.unlimitedReader().read(stream!!, BinaryTagIO.Compression.GZIP)
                val structure = mutableListOf<BlockPos>()
                val palettes = reader.getList("palette")
                val palette =
                    IntStream.range(0, palettes.size())
                        .mapToObj { palettes.getCompound(it) }
                        .map { it.getString("Name") }
                        .map<Block?> { Block.fromNamespaceId(it) }.toList().toTypedArray()
                val blockArray = reader.getList("blocks")
                blockArray.forEach {
                    val blockObj = it.asBinaryTag() as CompoundBinaryTag
                    val jsonPos = blockObj.getList("pos")
                    structure.add(
                        BlockPos(
                            Vec(
                                jsonPos.getInt(0).toDouble(),
                                jsonPos.getInt(1).toDouble(),
                                jsonPos.getInt(2).toDouble()
                            ), palette[blockObj.getInt("state")]
                        )
                    )
                }
                val size = IntArray(3)
                val jsonSize = reader.getList("size")
                for (i in 0..2) size[i] = jsonSize.getInt(i)
                return Structure(
                    Vec(
                        jsonSize.getInt(0).toDouble(),
                        jsonSize.getInt(1).toDouble(),
                        jsonSize.getInt(2).toDouble()
                    ), structure
                )
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    companion object {
        fun random(): GameMap {
            val maps = Bomberman.getAvailableMaps()
            return maps.values.toList()[Random.nextInt(maps.size)]
        }
        fun random(playerCount: Int): GameMap {
            val maps = Bomberman.getAvailableMaps().filter { it.value.settings.maxPlayers >= playerCount }
            return maps.values.toList()[Random.nextInt(maps.size)]
        }
    }
}