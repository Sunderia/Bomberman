package fr.sunderia.bomberman.utils

import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

class PositionUtils {
    companion object {
        fun removeBlockAt(pos: Pos, instance: Instance) {
            instance.setBlock(pos, Block.AIR)
        }

        fun setBlockAt(pos: Pos, block: Block, instance: Instance) {
            instance.setBlock(pos, block)
        }

        fun toBlockPos(pos: Pos) = Pos(pos.blockX() + .0, pos.blockY() + .0, pos.blockZ() +.0)
    }
}