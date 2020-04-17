package com.github.jikoo.regionerator;

import java.util.regex.Pattern;

/**
 * A small class for converting coordinates between blocks, chunks, and regions.
 * 
 * @author Jikoo
 */
public class Coords {

	private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	/**
	 * Converts region coordinates into chunk coordinates.
	 * 
	 * @param region the coordinate to convert
	 * 
	 * @return the converted coordinate
	 */
	public static int regionToChunk(int region) {
		return region << 5;
	}

	/**
	 * Converts region coordinates into block coordinates.
	 * 
	 * @param region the coordinate to convert
	 * 
	 * @return the converted coordinate
	 */
	public static int regionToBlock(int region) {
		return region << 9;
	}

	/**
	 * Converts chunk coordinates into region coordinates.
	 * 
	 * @param chunk the coordinate to convert
	 * 
	 * @return the converted coordinate
	 */
	public static int chunkToRegion(int chunk) {
		return chunk >> 5;
	}

	/**
	 * Converts chunk coordinates into block coordinates.
	 * 
	 * @param chunk the coordinate to convert
	 * 
	 * @return the converted coordinate
	 */
	public static int chunkToBlock(int chunk) {
		return chunk << 4;
	}

	/**
	 * Converts block coordinates into region coordinates.
	 * 
	 * @param block the coordinate to convert
	 * 
	 * @return the converted coordinate
	 */
	public static int blockToRegion(int block) {
		return block >> 9;
	}

	/**
	 * Converts block coordinates into chunk coordinates.
	 * 
	 * @param block the coordinate to convert
	 * 
	 * @return the converted coordinate
	 */
	public static int blockToChunk(int block) {
		return block >> 4;
	}

}
