package com.github.jikoo.regionerator.world.impl;

import com.github.jikoo.regionerator.ChunkFlagger;
import com.github.jikoo.regionerator.DebugLevel;
import com.github.jikoo.regionerator.VisitStatus;
import com.github.jikoo.regionerator.hooks.Hook;
import com.github.jikoo.regionerator.util.Config;
import com.github.jikoo.regionerator.util.SupplierCache;
import com.github.jikoo.regionerator.world.ChunkInfo;
import com.github.jikoo.regionerator.world.RegionInfo;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class AnvilRegion extends RegionInfo {

	private byte[] header;

	AnvilRegion(@NotNull AnvilWorld world, @NotNull File regionFile, int lowestChunkX, int lowestChunkZ) throws IOException {
		super(world, regionFile, lowestChunkX, lowestChunkZ);
	}

	@Override
	public void read() throws IOException {
		if (!getRegionFile().canWrite() && !getRegionFile().setWritable(true) && !getRegionFile().canWrite()) {
			throw new IOException("Unable to write file " + getRegionFile().getPath());
		}

		if (header == null) {
			header = new byte[8192];
		}

		// Chunk pointers are the first 4096 bytes, last modification is the second set
		try (RandomAccessFile regionRandomAccess = new RandomAccessFile(getRegionFile(), "r")) {
			regionRandomAccess.read(header);
		}
	}

	@Override
	public boolean write() throws IOException {
		if (!getRegionFile().exists()) {
			return false;
		}

		if (!getRegionFile().canWrite() && !getRegionFile().setWritable(true) && !getRegionFile().canWrite()) {
			throw new IOException("Unable to set " + getRegionFile().getName() + " writable");
		}

		for (int i = 0; i < 4096; ++i) {
			if (header[i] != 0) {
				try (RandomAccessFile regionRandomAccess = new RandomAccessFile(getRegionFile(), "rwd")) {
					regionRandomAccess.write(header, 0, 4096);
				}
				return true;
			}
		}
		// Header contains no content, delete region
		return getRegionFile().exists() && getRegionFile().delete();
	}

	@NotNull
	@Override
	public AnvilWorld getWorldInfo() {
		return (AnvilWorld) super.getWorldInfo();
	}

	@NotNull
	@Override
	protected ChunkInfo getChunkInternal(int localChunkX, int localChunkZ) {

		return new ChunkInfo(this, localChunkX, localChunkZ) {
			private final SupplierCache<VisitStatus> visitStatusSupplier = new SupplierCache<>(() -> {
				if (isOrphaned()) {
					return VisitStatus.ORPHANED;
				}

				ChunkFlagger.FlagData flagData = getWorldInfo().getPlugin().getFlagger().getChunkFlag(getWorld(), getChunkX(), getChunkZ()).join();
				AnvilWorld world = getWorldInfo();

				long lastVisit = flagData.getLastVisit();

				if (lastVisit != Long.MAX_VALUE && lastVisit > System.currentTimeMillis()) {
					world.getPlugin().debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " is flagged.");

					if (lastVisit == Config.getFlagEternal()) {
						return VisitStatus.PERMANENTLY_FLAGGED;
					}

					return VisitStatus.VISITED;
				}

				Collection<Hook> syncHooks = Bukkit.isPrimaryThread() ? null : new ArrayList<>();
				int chunkX = getChunkX();
				int chunkZ = getChunkZ();

				for (Hook hook : world.getPlugin().getProtectionHooks()) {
					if (syncHooks != null && !hook.isAsyncCapable()) {
						syncHooks.add(hook);
						continue;
					}
					if (hook.isChunkProtected(world.getWorld(), chunkX, chunkZ)) {
						world.getPlugin().debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " contains protections by " + hook.getProtectionName());
						return VisitStatus.PROTECTED;
					}
				}

				if (syncHooks != null) {
					try {
						VisitStatus visitStatus = Bukkit.getScheduler().callSyncMethod(world.getPlugin(), () -> {
							for (Hook hook : syncHooks) {
								if (hook.isChunkProtected(world.getWorld(), chunkX, chunkZ)) {
									world.getPlugin().debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " contains protections by " + hook.getProtectionName());
									return VisitStatus.PROTECTED;
								}
							}
							return VisitStatus.UNKNOWN;
						}).get();
						if (visitStatus == VisitStatus.PROTECTED) {
							return VisitStatus.PROTECTED;
						}
					} catch (InterruptedException | ExecutionException e) {
						throw new CancellationException(e.getMessage());
					}
				}

				if (lastVisit == Long.MAX_VALUE) {
					world.getPlugin().debug(DebugLevel.HIGH, () -> "Chunk " + flagData.getChunkId() + " has not been visited since it was generated.");
					return VisitStatus.GENERATED;
				}

				return VisitStatus.UNVISITED;
			}, calcCacheDuration(), TimeUnit.MINUTES);

			@Override
			public boolean isOrphaned() {
				boolean orphaned = true;
				int index = 4 * (getLocalChunkX() + getLocalChunkZ() * 32);
				for (int i = 0; i < 4; ++i) {
					// Chunk data location in file is defined by a 4 byte pointer. If no location, orphaned.
					if (header[index + i] != 0) {
						orphaned = false;
						break;
					}
				}
				return orphaned;
			}

			@Override
			public void setOrphaned() {
				int index = 4 * (getLocalChunkX() + getLocalChunkZ() * 32);
				for (int i = 0; i < 4; ++i) {
					header[index + i] = 0;
				}
			}

			@Override
			public long getLastModified() {
				int index = 4096 + 4 * (localChunkX + localChunkZ * 32);
				// Last modification is stored as a big endian integer. Last 3 bytes are unsigned.
				return 1000 * (long) (header[index] << 24 | (header[index + 1] & 0xFF) << 16 | (header[index + 2] & 0xFF) << 8 | (header[index + 3] & 0xFF));
			}

			@Override
			public long getLastVisit() {
				return getWorldInfo().getPlugin().getFlagger().getChunkFlag(getWorld(), getLowestChunkX() + localChunkX, getLowestChunkZ() + localChunkZ).join().getLastVisit();
			}

			@Override
			public VisitStatus getVisitStatus() {
				return visitStatusSupplier.get();
			}
		};
	}

	private int calcCacheDuration() {
		Config config = getWorldInfo().getPlugin().config();
		return (int) Math.ceil(1024D / config.getDeletionChunkCount() * config.getDeletionRecoveryMillis() / 60000);
	}

}
