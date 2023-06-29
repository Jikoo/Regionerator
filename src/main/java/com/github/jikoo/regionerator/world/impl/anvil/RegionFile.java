/*
 * Copyright (c) 2015-2023 by Jikoo.
 *
 * Regionerator is licensed under a Creative Commons
 * Attribution-ShareAlike 4.0 International License.
 *
 * You should have received a copy of the license along with this
 * work. If not, see <http://creativecommons.org/licenses/by-sa/4.0/>.
 */

package com.github.jikoo.regionerator.world.impl.anvil;

import com.github.jikoo.planarwrappers.util.Coords;
import com.google.common.annotations.Beta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

/**
 * A from-scratch re-implementation of the MCR format as described in
 * <a href="https://pastebin.com/niWTqLvk">RegionFile</a>, written by Scaevolus and Mojang AB.
 *
 * <p>This class is not intended to have any capacity to repair, recover, or parse broken regions.
 *
 * <h1>This is a work in progress!</h1>
 * <p>Any and all API is subject to change as I fuddle my way through the intricacies of the format and more recent
 * undocumented modifications by Mojang, such as {@code .mcc} files for overly-large chunks.</p>
 */
@Beta
public class RegionFile implements AutoCloseable {

  public static final Pattern FILE_NAME_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)(\\.mc[ar])$");

  // Useful constants for region file parsing.
  /** Each sector is made up of a fixed number of bytes. */
  public static final int SECTOR_BYTES = 4096;
  /** Integers comprise certain entire sectors. */
  private static final int SECTOR_INTS = SECTOR_BYTES / Integer.BYTES;
  /** The header consumes a number of sectors at the start of the file. */
  static final int REGION_HEADER_SECTORS = 2;
  /** The total header size in bytes. */
  public static final int REGION_HEADER_LENGTH = SECTOR_BYTES * REGION_HEADER_SECTORS;
  public static final int CHUNK_NOT_PRESENT = 0;
  /** Chunks start with a header declaring their size and compression type. */
  public static final int CHUNK_HEADER_LENGTH = Integer.BYTES + 1;
  /** Internally-saved chunk data may not exceed a certain number of sectors. */
  private static final int CHUNK_MAXIMUM_SECTORS = 256;
  /** Internally-saved chunk data may not exceed a certain length. */
  private static final int CHUNK_MAXIMUM_LENGTH = CHUNK_MAXIMUM_SECTORS * SECTOR_BYTES - CHUNK_HEADER_LENGTH;
  /** Chunks exceeding {@link #CHUNK_MAXIMUM_LENGTH} are flagged and saved externally. */
  private static final int FLAG_CHUNK_TOO_LARGE = 0b10000000;
  /** Bitmask for a local chunk coordinate. */
  private static final int BITMASK_LOCAL_CHUNK = 0x1F;
  /** Bitmask for the number of sectors used to store a chunk's data. */
  private static final int BITMASK_OFFSET_SECTOR_COUNT = 0xFF;
  /** Bitmask for the start sector of a chunk's data. */
  private static final int BITMASK_OFFSET_START_SECTOR = 0xFFFFFF;
  // FUTURE may want a util to construct these automatically from bitmasks
  /** Number of bits in {@link #BITMASK_OFFSET_SECTOR_COUNT} */
  private static final int BIT_COUNT_LOCAL_CHUNK = 5;
  /** Number of bits in {@link #BITMASK_OFFSET_SECTOR_COUNT} */
  private static final int BIT_COUNT_OFFSET_SECTOR_COUNT = 8;

  private final Path regionPath;
  private final int regionX;
  private final int regionZ;
  private final ByteBuffer regionHeader;
  private final IntBuffer chunkOffsets;
  private final IntBuffer chunkTimestamps;
  private final SectorBitSet sectorsUsed;
  private final ByteBuffer chunkHeader;
  private boolean regionHeaderRead = false;
  private @Nullable FileChannel file;

  /**
   * Constructor for a {@code RegionFile}.
   *
   * @param regionPath the path of the region file
   */
  public RegionFile(@NotNull Path regionPath) {
    this(regionPath,
        ByteBuffer.allocateDirect(REGION_HEADER_LENGTH),
        ByteBuffer.allocateDirect(CHUNK_HEADER_LENGTH));
  }

  /**
   * Constructor for a {@code RegionFile} using provided {@link ByteBuffer ByteBuffers}.
   *
   * <p>Note that the provided buffers must be {@link ByteBuffer#isDirect() direct} and of the correct capacity.
   *
   * @param regionPath the path of the region file
   * @param regionHeaderBuffer a {@link ByteBuffer#isDirect() direct} buffer with capacity {@link #REGION_HEADER_LENGTH}
   * @param chunkHeaderBuffer a {@link ByteBuffer#isDirect() direct} buffer with capacity {@link #CHUNK_HEADER_LENGTH}
   * @param whoAreYouAndIsThisSafe a phrase to make sure the user has acknowledged the drawbacks of providing buffers
   * @throws IllegalArgumentException if buffers do not meet every criterion or if the passphrase is incorrect
   */
  public RegionFile(
          @NotNull Path regionPath,
          @NotNull ByteBuffer regionHeaderBuffer,
          @NotNull ByteBuffer chunkHeaderBuffer,
          @Nullable String whoAreYouAndIsThisSafe) {
    this(verifyIntent(whoAreYouAndIsThisSafe, regionPath),
            verifyBuffer(regionHeaderBuffer, REGION_HEADER_LENGTH),
            verifyBuffer(chunkHeaderBuffer, CHUNK_HEADER_LENGTH));
  }

  private RegionFile(@NotNull Path regionPath, @NotNull ByteBuffer regionHeaderBuffer, @NotNull ByteBuffer chunkHeaderBuffer) {
    if (Files.isDirectory(regionPath)) {
      throw new IllegalArgumentException("Provided region file is a directory " + regionPath.toAbsolutePath());
    }
    this.regionPath = regionPath.normalize();
    // TODO should add a constructor accepting coordinates and skip this
    //  May want to make regionPath a #read param instead
    Matcher matcher = FILE_NAME_PATTERN.matcher(this.regionPath.getFileName().toString());
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Provided region file does not match name format " + regionPath.getFileName());
    }
    this.regionX = Integer.parseInt(matcher.group(1));
    this.regionZ = Integer.parseInt(matcher.group(2));

    regionHeader = regionHeaderBuffer;
    regionHeader.clear();
    chunkOffsets = regionHeader.slice(0, SECTOR_BYTES).asIntBuffer();
    chunkTimestamps = regionHeader.slice(SECTOR_BYTES, SECTOR_BYTES).asIntBuffer();
    sectorsUsed = new SectorBitSet();
    // TODO should 2-indexing be done inside the SectorBitSet?
    //  I.e. not even bother having bits, just add 2 to all return values?
    //  May reduce need for data verification, but may make errors less clear.
    sectorsUsed.set(0, REGION_HEADER_SECTORS);

    chunkHeader = chunkHeaderBuffer;
    chunkHeader.clear();
  }

  /**
   * Open the {@link FileChannel} used internally for reading data from the region file.
   *
   * @param accessMode the {@link OpenOption OpenOptions} used when opening the file
   * @throws IOException if an I/O error occurs. See {@link FileChannel#open(Path, OpenOption...)}
   */
  public void open(@NotNull AccessMode accessMode) throws IOException {
    close();
    file = FileChannel.open(regionPath, accessMode.asOpenOptions());
  }

  /**
   * Read the header of the region file. This must be done before performing any operations that read or manipulate
   * the file.
   *
   * @throws ClosedChannelException if the backing {@link FileChannel} is not open
   * @throws IOException if an I/O error occurs. See {@link java.nio.channels.ReadableByteChannel#read(ByteBuffer)}
   * @throws DataFormatException if the header is present but too short
   */
  public void readHeader() throws IOException, DataFormatException {
    if (file == null) {
      throw new ClosedChannelException();
    }

    regionHeader.rewind();
    int bytesRead = file.read(regionHeader, 0);
    regionHeader.flip();

    if (bytesRead != -1 && bytesRead < REGION_HEADER_LENGTH) {
      throw new DataFormatException(String.format(
              "Invalid header for %s; read %s bytes of expected %s",
              regionPath.getFileName(),
              bytesRead,
              REGION_HEADER_LENGTH));
    }

    for (int index = 0; index < SECTOR_INTS; ++index) {
      int packedOffsetData = chunkOffsets.get(index);

      if (packedOffsetData == CHUNK_NOT_PRESENT) {
        continue;
      }

      int startSector = packedOffsetData >> BIT_COUNT_OFFSET_SECTOR_COUNT & BITMASK_OFFSET_START_SECTOR;
      if (startSector < REGION_HEADER_SECTORS) {
        throw new DataFormatException("Start sector in region header for " + friendlyIndex(index));
      }
      int sectorCount = packedOffsetData & BITMASK_OFFSET_SECTOR_COUNT;
      sectorsUsed.set(startSector, sectorCount);
    }

    regionHeaderRead = true;
  }

  /**
   * Write the header of the region file.
   *
   * @throws ClosedChannelException if the backing {@link FileChannel} is not open
   * @throws IOException if an I/O error occurs. See {@link java.nio.channels.WritableByteChannel#write(ByteBuffer)}
   * @throws IllegalStateException if the existing header was never read; as all header modifications require the header
   *         to have been read initially the header must be blank and the file should be deleted
   */
  public void writeHeader() throws IOException {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }
    if (file == null) {
      throw new ClosedChannelException();
    }
    regionHeader.rewind();
    file.write(regionHeader, 0);
  }

  /**
   * Check if the header contains data for a chunk.
   *
   * @param x the X coordinate
   * @param z the Z coordinate
   * @return true if the header contains data for the coordinates
   * @throws IllegalArgumentException if the chunk coordinates are not either local or within the region's world bounds
   * @throws IllegalStateException if the existing header was never read
   */
  public boolean isPresent(int x, int z) {
    checkLegalChunks(x, z);
    return isPresent(packIndex(x, z));
  }

  /**
   * Check if the header contains data for a chunk.
   *
   * @param index the packed chunk index
   * @return true if the header contains data for the coordinates
   * @throws IllegalStateException if the existing header was never read
   * @throws IndexOutOfBoundsException if the index is negative or equals/exceeds the number of chunks in a region
   */
  public boolean isPresent(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    return chunkOffsets.get(index) != CHUNK_NOT_PRESENT;
  }

  /**
   * Get the last modification timestamp for a chunk in milliseconds since the epoch.
   *
   * @param x the X coordinate
   * @param z the Z coordinate
   * @return the number of milliseconds since the epoch of 1970-01-01T00:00:00Z
   * @throws IllegalArgumentException if the chunk coordinates are not either local or within the region's world bounds
   * @throws IllegalStateException if the existing header was never read
   */
  public long getLastModified(int x, int z) {
    checkLegalChunks(x, z);
    return getLastModified(packIndex(x, z));
  }

  /**
   * Get the last modification timestamp for a chunk in milliseconds since the epoch.
   *
   * @param index the packed chunk index
   * @return the number of milliseconds since the epoch of 1970-01-01T00:00:00Z
   * @throws IllegalStateException if the existing header was never read
   * @throws IndexOutOfBoundsException if the index is negative or equals/exceeds the number of chunks in a region
   */
  public long getLastModified(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    return TimeUnit.MILLISECONDS.convert(chunkTimestamps.get(index), TimeUnit.SECONDS);
  }

  /**
   * Delete the specified chunk.
   *
   * <p>This frees up the corresponding sectors, but does not compress the region to use those sectors. Doing so would
   * require rewriting the full region file, which is a very expensive prospect.
   *
   * @param x the X coordinate
   * @param z the Z coordinate
   * @throws IllegalArgumentException if the chunk coordinates are not either local or within the region's world bounds
   * @throws IllegalStateException if the existing header was never read
   * @throws IOException if the chunk is extra-large and there is an exception deleting the file
   */
  public void deleteChunk(int x, int z) throws IOException {
    deleteChunk(packIndex(x, z));
  }

  /**
   * Delete the specified chunk.
   *
   * <p>This frees up the corresponding sectors, but does not compress the region to use those sectors. Doing so would
   * require rewriting the full region file, which is a very expensive prospect.
   *
   * @param index the packed chunk index
   * @throws IllegalStateException if the existing header was never read
   * @throws IndexOutOfBoundsException if the index is negative or equals/exceeds the number of chunks in a region
   * @throws IOException if the chunk is extra-large and there is an exception deleting the file
   */
  public void deleteChunk(int index) throws IOException {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    int packedOffsetData = chunkOffsets.get(index);
    if (packedOffsetData == CHUNK_NOT_PRESENT) {
      // Chunk already deleted.
      return;
    }

    chunkOffsets.put(index, CHUNK_NOT_PRESENT);
    chunkTimestamps.put(index, (int) Clock.systemUTC().instant().getEpochSecond());
    Files.deleteIfExists(getXlChunkPath(index)); // TODO should this not delete if not open/delete on write?

    int startSector = packedOffsetData >> BIT_COUNT_OFFSET_SECTOR_COUNT & BITMASK_OFFSET_START_SECTOR;
    if (startSector >= REGION_HEADER_SECTORS) {
      int sectorCount = packedOffsetData & BITMASK_OFFSET_SECTOR_COUNT;
      sectorsUsed.clear(startSector, sectorCount);
    }
  }

  private @NotNull Path getXlChunkPath(int index) {
    int localX = unpackLocalX(index);
    int localZ = unpackLocalZ(index);
    String fileName = String.format(
            "c.%s.%s.mcc",
            Coords.regionToChunk(regionX) + localX,
            Coords.regionToChunk(regionZ) + localZ);
    return regionPath.resolveSibling(fileName);
  }

  // FUTURE publicize readChunk
  private @Nullable InputStream readChunk(int x, int z) throws IOException, DataFormatException {
    return readChunk(x, z, true);
  }

  private @Nullable InputStream readChunk(int x, int z, boolean decode) throws IOException, DataFormatException {
    return readChunk(packIndex(x, z), decode);
  }

  private @Nullable InputStream readChunk(int index) throws IOException, DataFormatException {
    return readChunk(index, true);
  }

  // FUTURE add chunk identifiers to exceptions
  private @Nullable InputStream readChunk(int index, boolean decode) throws IOException, DataFormatException {
    if (file == null) {
      throw new ClosedChannelException();
    }
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    int packedOffsetData = chunkOffsets.get(index);

    if (packedOffsetData == CHUNK_NOT_PRESENT) {
      return null;
    }

    int startSector = packedOffsetData >> BIT_COUNT_OFFSET_SECTOR_COUNT & BITMASK_OFFSET_START_SECTOR;
    if (startSector < REGION_HEADER_SECTORS) {
      throw new DataFormatException("start sector in region header");
    }

    chunkHeader.clear();
    file.read(chunkHeader, (long) startSector * SECTOR_BYTES);
    chunkHeader.flip();

    if (chunkHeader.remaining() < CHUNK_HEADER_LENGTH) {
      throw new DataFormatException("chunk header too short");
    }

    int declaredLength = (packedOffsetData & BITMASK_OFFSET_SECTOR_COUNT) * SECTOR_BYTES;
    int realLength = chunkHeader.getInt();

    if (realLength > declaredLength || realLength < 0) {
      // FUTURE Should external chunks skip this check? Need to actually read how mcc is handled
      throw new DataFormatException("invalid chunk data length");
    }

    byte encoding = chunkHeader.get();
    RegionCompression compression;
    if (decode) {
      compression = RegionCompression.byCompressionId(encoding);
      if (compression == null) {
        throw new DataFormatException("Unknown compression ID " + encoding);
      }
    } else {
      compression = RegionCompression.NONE;
    }

    if ((encoding & FLAG_CHUNK_TOO_LARGE) != 0) {
      return compression.decode(getXlChunk(index));
    }

    // FUTURE
    throw new IOException("Lazy person negates compiler warnings for " + index);
  }

  private @NotNull InputStream getXlChunk(int index) throws IOException {
    Path xlFile = getXlChunkPath(index);
    if (!Files.isRegularFile(xlFile)) {
      throw new NoSuchFileException(xlFile.toAbsolutePath().toString());
    }

    return Files.newInputStream(xlFile, StandardOpenOption.READ);
  }

  // FUTURE
  //  fragment -> all decompressed .mcc for readability
  //  defragment(RegionCompression) -> parse and recompress, re-assign sectors and fully rewrite file

  @Override
  public void close() throws IOException {
    if (file != null) {
      file.close();
    }
  }

  private void checkLegalChunks(int chunkX, int chunkZ) {
    boolean regionalX = checkLocalOrRegionalChunk(chunkX, regionX);
    boolean regionalZ = checkLocalOrRegionalChunk(chunkZ, regionZ);
    /*
     * If the raw regional state matches, the pair is either local-local or regional-regional and is good. However,
     * region 0 on one axis will result in valid coordinates that are both regional and local. As a result of assuming
     * locality (because ideally everyone will use local coordinates and just pass the first condition outright),
     * valid chunk coordinates may be deemed a regional/local mismatch due to the ambiguity of one axis. Therefore,
     * if a coordinate is regional and the other coordinate is in region 0, the other coordinate is also regional.
     */
    if (regionalX != regionalZ && (regionalZ && regionX != 0 || regionalX && regionZ != 0)) {
      // Mixed regional and local chunks, i.e. probably trying to query the wrong region.
      throw new IllegalArgumentException("Mismatched local and regional coordinates " + chunkX + " and " + chunkZ);
    }
  }

  private boolean checkLocalOrRegionalChunk(int chunk, int region) {
    if (chunk >= 0 && chunk <= BITMASK_LOCAL_CHUNK) {
      // Note: Can't tell whether individual axes are regional or local for region 0.
      return false;
    }
    int regionChunk = region << BIT_COUNT_LOCAL_CHUNK;
    if (chunk < regionChunk || chunk > regionChunk + BITMASK_LOCAL_CHUNK) {
      throw new IllegalArgumentException("Chunk " + chunk + " is out of range for region " + region);
    }
    return true;
  }

  public static int packIndex(int x, int z) {
    return (z & BITMASK_LOCAL_CHUNK) << BIT_COUNT_LOCAL_CHUNK | (x & BITMASK_LOCAL_CHUNK);
  }

  public static int unpackLocalX(int index) {
    return index & BITMASK_LOCAL_CHUNK;
  }

  public static int unpackLocalZ(int index) {
    return index >> BIT_COUNT_LOCAL_CHUNK & BITMASK_LOCAL_CHUNK;
  }

  private static @NotNull String friendlyIndex(int index) {
    return "[" + unpackLocalX(index) + "," + unpackLocalZ(index) + "]";
  }

  @Contract("_, _ -> param2")
  private static Path verifyIntent(@Nullable String password, Path path) {
    // This is a silly little stopgap to ensure that people are at least a little aware that reusing a ByteBuffer can
    // introduce unexpected behavior. If they know enough to look, they (hopefully) know enough to ensure safety.
    if ("I am John RegionFile; I understand that providing my own buffer may be unsafe.".equals(password)) {
      return path;
    }
    throw new IllegalArgumentException("Please identify yourself and acknowledge that issues may arise.");
  }

  private static @NotNull ByteBuffer verifyBuffer(@NotNull ByteBuffer buffer, int capacity) {
    if (buffer.isReadOnly() || !buffer.isDirect() || buffer.capacity() != capacity) {
      throw new IllegalArgumentException("Buffer must be a writable direct ByteBuffer with capacity " + capacity);
    }
    return buffer;
  }

}
