package com.github.jikoo.regionerator.world;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;

/**
 * Dummy chunk used to prevent chunk loading when checking protection plugins.
 * 
 * @author Jikoo
 */
public class DummyChunk implements Chunk {

	private final World world;
	private final int chunkX, chunkZ;

	public DummyChunk(World world, int chunkX, int chunkZ) {
		this.world = world;
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
	}

	@Override
	public Block getBlock(int x, int y, int z) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public ChunkSnapshot getChunkSnapshot() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public ChunkSnapshot getChunkSnapshot(boolean includeMaxblocky, boolean includeBiome,
			boolean includeBiomeTempRain) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public Entity[] getEntities() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public BlockState[] getTileEntities() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public World getWorld() {
		return this.world;
	}

	@Override
	public int getX() {
		return this.chunkX;
	}

	@Override
	public int getZ() {
		return this.chunkZ;
	}

	@Override
	public boolean isLoaded() {
		return this.world.isChunkLoaded(chunkZ, chunkZ);
	}

	@Override
	public boolean load() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean load(boolean generate) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean unload() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean unload(boolean save) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean unload(boolean save, boolean safe) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean isSlimeChunk() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean isForceLoaded() {
		return this.world.isChunkForceLoaded(this.chunkX, this.chunkZ);
	}

	@Override
	public void setForceLoaded(boolean b) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

}
