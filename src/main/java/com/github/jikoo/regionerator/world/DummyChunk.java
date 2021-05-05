/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world;

import java.util.Collection;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Dummy chunk used to prevent chunk loading when checking protection plugins.
 */
public class DummyChunk implements Chunk {

	private final @NotNull World world;
	private final int chunkX, chunkZ;

	public DummyChunk(@NotNull World world, int chunkX, int chunkZ) {
		this.world = world;
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
	}

	@Override
	public @NotNull Block getBlock(int x, int y, int z) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public @NotNull ChunkSnapshot getChunkSnapshot() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public @NotNull ChunkSnapshot getChunkSnapshot(boolean includeMaxblocky, boolean includeBiome,
			boolean includeBiomeTempRain) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public @NotNull Entity @NotNull [] getEntities() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public @NotNull BlockState @NotNull [] getTileEntities() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public @NotNull World getWorld() {
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

	@Override
	public boolean addPluginChunkTicket(@NotNull Plugin plugin) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean removePluginChunkTicket(@NotNull Plugin plugin) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public @NotNull Collection<Plugin> getPluginChunkTickets() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public long getInhabitedTime() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public void setInhabitedTime(long l) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public boolean contains(@NotNull BlockData blockData) {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

	@Override
	public @NotNull PersistentDataContainer getPersistentDataContainer() {
		throw new UnsupportedOperationException("DummyChunk does not support world operations.");
	}

}
