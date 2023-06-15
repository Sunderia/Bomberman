package fr.sunderia.bomberman

import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.block.Block

data class BlockPos(val vec: Vec, val block: Block)
data class Structure(val size: Vec, val blocks: List<BlockPos>)