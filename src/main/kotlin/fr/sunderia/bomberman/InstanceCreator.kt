package fr.sunderia.bomberman

import com.google.gson.JsonObject
import fr.sunderia.bomberman.utils.BlockPos
import fr.sunderia.bomberman.utils.Structure
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.instance.generator.GenerationUnit
import net.minestom.server.utils.chunk.ChunkUtils
import org.jglrxavpok.hephaistos.nbt.NBTException
import org.jglrxavpok.hephaistos.nbt.NBTReader
import java.io.IOException
import java.util.LinkedList
import java.util.stream.IntStream
import kotlin.random.Random

class InstanceCreator {
    companion object {
        fun createInstanceContainer(manager: InstanceManager): InstanceContainer {
            val container = manager.createInstanceContainer(Bomberman.fullBright)
            container.setGenerator { unit: GenerationUnit ->
                unit.modifier().fillHeight(0, 40, Block.STONE)
            }
            return container
        }

        @Suppress("UnstableApiUsage")
        fun generateStructure(container: Instance) {
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
            getAffectedChunks(batch).let {
                ChunkUtils.optionalLoadAll(container, it, null)
                        .thenRun { batch.apply(container) { batch.clear() } }
            }
        }

        private fun parseNBT(): Structure? {
            try {
                Bomberman::class.java.getResourceAsStream("/bomberman.nbt").use { stream ->
                    NBTReader(stream!!).use { reader ->
                        val structure: MutableList<BlockPos> = LinkedList()
                        val nbt = Bomberman.gson.fromJson(reader.read().toSNBT(), JsonObject::class.java)
                        val palettes = nbt.getAsJsonArray("palette")
                        val palette =
                                IntStream.range(0, palettes.size())
                                        .mapToObj { palettes[it] }
                                        .map { it.asJsonObject }
                                        .map { it.getAsJsonPrimitive("Name").asString }
                                        .map<Block?> { Block.fromNamespaceId(it) }.toList().toTypedArray()
                        val blockArray = nbt.getAsJsonArray("blocks")
                        blockArray.forEach {
                            val blockObj = it.asJsonObject
                            val jsonPos = blockObj.getAsJsonArray("pos")
                            structure.add(
                                    BlockPos(Vec(jsonPos[0].asInt.toDouble(), jsonPos[1].asInt.toDouble(), jsonPos[2].asInt.toDouble()), palette[blockObj["state"].asInt])
                            )
                        }
                        val size = IntArray(3)
                        val jsonSize = nbt.getAsJsonArray("size")
                        for (i in 0..2) size[i] = jsonSize[i].asInt
                        return Structure(Vec(jsonSize[0].asInt.toDouble(), jsonSize[1].asInt.toDouble(), jsonSize[2].asInt.toDouble()), structure)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: NBTException) {
                e.printStackTrace()
            }
            return null
        }

        private fun getAffectedChunks(batch: AbsoluteBlockBatch): LongArray {
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
    }
}