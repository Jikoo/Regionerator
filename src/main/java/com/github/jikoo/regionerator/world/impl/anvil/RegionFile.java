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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

public abstract class RegionFile implements AutoCloseable {

  public static final Pattern FILE_NAME_PATTERN = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)(\\.mc[ar])");

  // Useful constants for region file parsing.
  // TODO restrict access to these probably
  /** Each sector is made up of a fixed number of bytes. */
  protected static final int SECTOR_BYTES = 4096;
  /** Integers comprise certain entire sectors. */
  protected static final int SECTOR_INTS = SECTOR_BYTES / Integer.BYTES;
  /** The header consumes a number of sectors at the start of the file. */
  protected static final int REGION_HEADER_SECTORS = 2;
  /** The total header size in bytes. */
  protected static final int REGION_HEADER_LENGTH = SECTOR_BYTES * REGION_HEADER_SECTORS;
  private static final int CHUNK_NOT_PRESENT = 0;
  /** Chunks start with a header declaring their size and compression type. */
  protected static final int CHUNK_HEADER_LENGTH = Integer.BYTES + 1;
  /** Internally-saved chunk data may not exceed a certain number of sectors. */
  protected static final int CHUNK_MAXIMUM_SECTORS = 256;
  /** Internally-saved chunk data may not exceed a certain length. */
  protected static final int CHUNK_MAXIMUM_LENGTH = CHUNK_MAXIMUM_SECTORS * SECTOR_BYTES - CHUNK_HEADER_LENGTH;
  /** Chunks exceeding {@link #CHUNK_MAXIMUM_LENGTH} are flagged and saved externally. */
  protected static final int CHUNK_TOO_LARGE_FLAG = 128;
  protected static final int LOCAL_CHUNK_MAX = 0x1F;

  protected final Path regionPath;
  protected final boolean sync;

  private final ByteBuffer regionHeader;
  protected final IntBuffer chunkOffsets;
  protected final IntBuffer chunkTimestamps;
  private boolean regionHeaderRead = false;

  private final ByteBuffer chunkHeader;

  private @Nullable FileChannel file;

  public RegionFile(@NotNull Path regionPath, boolean sync) {
    if (Files.isDirectory(regionPath)) {
      throw new IllegalArgumentException("Provided region file is a directory " + regionPath.toAbsolutePath());
    }
    this.regionPath = regionPath;
    this.sync = sync;

    // TODO benchmark these assumptions:
    //  A direct ByteBuffer is much slower to allocate but more performant when reading/writing.
    //  As we care most about minimizing the time we spend doing I/O, a direct buffer makes sense.
    //  However, as they are slow to create, we subdivide one larger buffer for our subsections.
    //  Using specific slices is slightly less performant but causes clearer errors.
    // TODO do we need clearer errors?
    regionHeader = ByteBuffer.allocateDirect(SECTOR_BYTES * REGION_HEADER_SECTORS);
    chunkOffsets = regionHeader.slice(0, SECTOR_BYTES).asIntBuffer();
    chunkTimestamps = regionHeader.position(SECTOR_BYTES).slice().asIntBuffer();
    regionHeader.position(0);

    chunkHeader = ByteBuffer.allocateDirect(CHUNK_HEADER_LENGTH);
  }

  public void open() throws IOException {
    close();

    // TODO Would opening read-only improve performance for exclusively read ops? Benchmark and test
    //  Windows: read may not lock while write will
    // TODO AsyncFileChannel? Supposedly faster due to less internal sync. We can single thread ourselves. Windows again
    if (sync) {
      // DSYNC and not SYNC because there's no need to write meta.
      file = FileChannel.open(regionPath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
    } else {
      file = FileChannel.open(regionPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
    }
  }

  public void readHeader() throws IOException, DataFormatException {
    if (file == null) {
      throw new ClosedChannelException();
    }

    regionHeader.rewind();
    int bytesRead = file.read(regionHeader);
    regionHeader.flip();

    if (bytesRead != -1 && bytesRead < REGION_HEADER_LENGTH) {
      throw new DataFormatException(String.format(
              "Invalid header for %s; read %s bytes of expected %s",
              regionPath.getFileName(),
              bytesRead,
              REGION_HEADER_LENGTH));
    }

    regionHeaderRead = true;
  }

  public void writeHeader() throws IOException {
    if (file == null) {
      throw new ClosedChannelException();
    }
    regionHeader.rewind();
    file.write(regionHeader, 0);
  }

  // TODO
  //  getLastModified x+z/index
  //  delete x+z/index

  // TODO readChunkRaw
  public @Nullable InputStream readChunk(int x, int z) throws IOException, DataFormatException {
    x = x & LOCAL_CHUNK_MAX;
    z = z & LOCAL_CHUNK_MAX;
    return readChunk(x << 8 | z);
  }

  // TODO add chunk identifiers to exceptions
  public @Nullable InputStream readChunk(int index) throws IOException, DataFormatException {
    if (file == null) {
      throw new ClosedChannelException();
    }

    int packedOffsetData = chunkOffsets.get(index);

    if (packedOffsetData == CHUNK_NOT_PRESENT) {
      return null;
    }

    int startSector = packedOffsetData >> 8 & 0xFFFFFF;
    if (startSector < REGION_HEADER_SECTORS) {
      throw new DataFormatException("start sector in region header");
    }

    chunkHeader.clear();
    file.read(chunkHeader, (long) startSector * SECTOR_BYTES);
    chunkHeader.flip();

    if (chunkHeader.remaining() < CHUNK_HEADER_LENGTH) {
      throw new DataFormatException("chunk header too short");
    }

    int declaredLength = (packedOffsetData & 0xFF) * SECTOR_BYTES;
    int realLength = chunkHeader.getInt();

    if (realLength > declaredLength || realLength < 0) {
      throw new DataFormatException("invalid chunk data length");
    }

    byte encoding = chunkHeader.get();
    if ((encoding & CHUNK_TOO_LARGE_FLAG) != 0) {
      // TODO oversized chunk
    }


    // TODO
    throw new IOException("Lazy person negates compiler warnings for " + index);
  }

  @Override
  public void close() throws IOException {
    if (file != null) {
      file.close();
    }
  }

}
