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
 * Tiny enum representing when a chunk was last visited.
 */
public enum VisitStatus {

	ORPHANED,
	UNVISITED,
	GENERATED,
	VISITED,
	PERMANENTLY_FLAGGED,
	PROTECTED,
	UNKNOWN

}
