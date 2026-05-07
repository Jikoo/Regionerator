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

import org.jetbrains.annotations.Nullable;

/**
 * Tiny enum for debugging.
 */
public enum DebugLevel {

	OFF,
	LOW,
	MEDIUM,
	HIGH,
	EXTREME;

	public static DebugLevel of(@Nullable String value) {
		if (value == null) return OFF;

		try {
			return valueOf(value);
		} catch (IllegalArgumentException e) {
			return OFF;
		}
	}

}
