/*
 * Copyright (c) 2015-2021 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator;

/**
 * Enum representing whether a chunk is potentially eligible for deletion.
 */
public enum VisitStatus {

	/** Chunk is orphaned on disk. */
	ORPHANED(true),
	/** Chunk is not visited. */
	UNVISITED(true),
	/** Chunk has not been modified since creation. */
	GENERATED(true),
	/** Chunk is visited. */
	VISITED,
	/** Chunk is recently modified. */
	RECENTLY_MODIFIED,
	/** Chunk is permanently flagged as visited. */
	PERMANENTLY_FLAGGED,
	/** Chunk is protected by an external plugin. */
	PROTECTED,
	/** Chunk status cannot be read. */
	UNKNOWN;

	private final boolean canDelete;

	VisitStatus(boolean canDelete) {
		this.canDelete = canDelete;
	}

	VisitStatus() {
		this.canDelete = false;
	}

	public boolean canDelete() {
		return canDelete;
	}

}
