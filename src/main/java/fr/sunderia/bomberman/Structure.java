package fr.sunderia.bomberman;

import java.util.List;

import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;

public record Structure(Vec size, List<BlockPos> blocks) {
	public record BlockPos(Vec vec, Block block) {}
}
