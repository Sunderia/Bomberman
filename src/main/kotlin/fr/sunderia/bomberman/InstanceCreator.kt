package fr.sunderia.bomberman

import fr.sunderia.bomberman.party.Game
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.instance.InstanceManager
import net.minestom.server.instance.batch.AbsoluteBlockBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.utils.chunk.ChunkUtils
import kotlin.random.Random

class InstanceCreator {
    companion object {
        fun createInstanceContainer(manager: InstanceManager): InstanceContainer {
            val container = manager.createInstanceContainer(Bomberman.fullbright)
            container.setGenerator { it.modifier().fillHeight(0, 40, Block.STONE) }
            return container
        }

        @Suppress("UnstableApiUsage")
        fun generateStructure(container: Instance, game: Game) {
            val (size, blocks1) = game.map.parseNBT() ?: return
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