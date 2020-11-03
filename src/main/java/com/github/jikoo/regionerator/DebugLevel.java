package com.github.jikoo.regionerator;

import org.jetbrains.annotations.Nullable;

/**
 * Tiny enum for debugging.
 * 
 * @author Jikoo
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
