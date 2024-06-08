package fr.sunderia.bomberman

import fr.sunderia.bomberman.utils.BlockPos
import fr.sunderia.bomberman.utils.Structure
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.chunk.ChunkUtils
import java.io.IOException
import java.util.stream.IntStream
import kotlin.random.Random

class InstanceCreator {
    companion object {
        fun createInstanceContainer(manager: InstanceManager): InstanceContainer {
            val container = manager.createInstanceContainer(Bomberman.fullbright)
            container.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
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
                    val reader = BinaryTagIO.unlimitedReader().read(stream!!, BinaryTagIO.Compression.GZIP)
                    val structure = mutableListOf<BlockPos>()
                    //val nbt = Bomberman.gson.fromJson(reader.getList(), JsonObject::class.java)
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