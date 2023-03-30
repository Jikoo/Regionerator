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
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;

public class RegionFile implements AutoCloseable {

  public static final Pattern FILE_NAME_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)(\\.mc[ar])$");

  // Useful constants for region file parsing.
  /** Each sector is made up of a fixed number of bytes. */
  private static final int SECTOR_BYTES = 4096;
  /** Integers comprise certain entire sectors. */
  private static final int SECTOR_INTS = SECTOR_BYTES / Integer.BYTES;
  /** The header consumes a number of sectors at the start of the file. */
  static final int REGION_HEADER_SECTORS = 2;
  /** The total header size in bytes. */
  private static final int REGION_HEADER_LENGTH = SECTOR_BYTES * REGION_HEADER_SECTORS;
  private static final int CHUNK_NOT_PRESENT = 0;
  /** Chunks start with a header declaring their size and compression type. */
  private static final int CHUNK_HEADER_LENGTH = Integer.BYTES + 1;
  /** Internally-saved chunk data may not exceed a certain number of sectors. */
  private static final int CHUNK_MAXIMUM_SECTORS = 256;
  /** Internally-saved chunk data may not exceed a certain length. */
  private static final int CHUNK_MAXIMUM_LENGTH = CHUNK_MAXIMUM_SECTORS * SECTOR_BYTES - CHUNK_HEADER_LENGTH;
  /** Chunks exceeding {@link #CHUNK_MAXIMUM_LENGTH} are flagged and saved externally. */
  private static final int FLAG_CHUNK_TOO_LARGE = 0b10000000;
  private static final int BITMASK_OFFSET_START_SECTOR = 0xFFFFFF;
  private static final int BITMASK_LOCAL_CHUNK = 0x1F;
  private static final int BITMASK_OFFSET_SECTOR_COUNT = 0xFF;

  private final Path regionPath;
  private final boolean sync;
  private final int regionX;
  private final int regionZ;
  private final ByteBuffer regionHeader;
  private final IntBuffer chunkOffsets;
  private final IntBuffer chunkTimestamps;
  private boolean regionHeaderRead = false;

  private final ByteBuffer chunkHeader;

  private @Nullable FileChannel file;

  public RegionFile(@NotNull Path regionPath, boolean sync) {
    if (Files.isDirectory(regionPath)) {
      throw new IllegalArgumentException("Provided region file is a directory " + regionPath.toAbsolutePath());
    }
    this.regionPath = regionPath.normalize();
    Matcher matcher = FILE_NAME_PATTERN.matcher(this.regionPath.getFileName().toString());
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Provided region file does not match name format " + regionPath.getFileName());
    }
    this.regionX = Integer.parseInt(matcher.group(1));
    this.regionZ = Integer.parseInt(matcher.group(2));
    this.sync = sync;

    // A direct ByteBuffer is much slower to allocate but more performant when reading/writing.
    // As we care most about minimizing the time we spend doing I/O, a direct buffer makes sense.
    regionHeader = ByteBuffer.allocateDirect(SECTOR_BYTES * REGION_HEADER_SECTORS);
    chunkOffsets = regionHeader.slice(0, SECTOR_BYTES).asIntBuffer();
    chunkTimestamps = regionHeader.position(SECTOR_BYTES).slice().asIntBuffer();
    regionHeader.position(0);

    chunkHeader = ByteBuffer.allocateDirect(CHUNK_HEADER_LENGTH);
  }

  public void open(boolean readOnly) throws IOException {
    close();

    OpenOption[] options;
    if (readOnly) {
      // This is the only case where a RandomAccessFile outperforms a FileChannel, but the gain is so marginal that it
      // isn't worth retooling the rest of the class around accepting two completely different input methods.
      options = new OpenOption[] { StandardOpenOption.READ };
    } else if (sync) {
      options = new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DSYNC };
    } else {
      options = new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE };
    }

    file = FileChannel.open(regionPath, options);
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

  public boolean isPresent(int x, int z) {
    return isPresent(packIndex(x, z));
  }

  public boolean isPresent(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    return chunkOffsets.get(index) != CHUNK_NOT_PRESENT;
  }

  public long getLastModified(int x, int z) {
    return getLastModified(packIndex(x, z));
  }

  public long getLastModified(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    return TimeUnit.MILLISECONDS.convert(chunkTimestamps.get(index), TimeUnit.SECONDS);
  }

  public void deleteChunk(int x, int z) {
    deleteChunk(packIndex(x, z));
  }

  public void deleteChunk(int index) {
    if (!regionHeaderRead) {
      throw new IllegalStateException("Region header has not been successfully read!");
    }

    chunkOffsets.put(index, 0);
    chunkTimestamps.put(index, (int) Instant.now().getEpochSecond());
    // FUTURE need to fetch old value and free sectors (and track sectors to start with)
  }

  // FUTURE
  //  readChunkRaw/skip decode
  private @Nullable InputStream readChunk(int x, int z) throws IOException, DataFormatException {
    return readChunk(packIndex(x, z));
  }

  // FUTURE add chunk identifiers to exceptions
  private @Nullable InputStream readChunk(int index) throws IOException, DataFormatException {
    if (file == null) {
      throw new ClosedChannelException();
    }

    int packedOffsetData = chunkOffsets.get(index);

    if (packedOffsetData == CHUNK_NOT_PRESENT) {
      return null;
    }

    int startSector = packedOffsetData >> 8 & BITMASK_OFFSET_START_SECTOR;
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
    if ((encoding & FLAG_CHUNK_TOO_LARGE) != 0) {
      return getOversizedChunk(index, encoding);
    }

    // FUTURE
    throw new IOException("Lazy person negates compiler warnings for " + index);
  }

  private @NotNull InputStream getOversizedChunk(int index, byte encoding) throws IOException, DataFormatException {
    int localX = index & BITMASK_LOCAL_CHUNK;
    int localZ = index >> 5 & BITMASK_LOCAL_CHUNK;
    String fileName = String.format(
            "c.%s.%s.mcc",
            Coords.regionToChunk(regionX) + localX,
            Coords.regionToChunk(regionZ) + localZ);
    Path oversizedFile = regionPath.resolveSibling(fileName);
    if (!Files.isRegularFile(oversizedFile)) {
      throw new NoSuchFileException(oversizedFile.toAbsolutePath().toString());
    }

    return decodeStream((byte) (encoding & ~FLAG_CHUNK_TOO_LARGE), Files.newInputStream(oversizedFile, StandardOpenOption.READ));
  }

  private @NotNull InputStream decodeStream(byte encoding, InputStream stream) throws DataFormatException, IOException {
    RegionCompression compression = RegionCompression.byCompressionId(encoding);
    if (compression == null) {
      throw new DataFormatException("Unknown compression ID " + encoding);
    }

    return compression.decode(stream);
  }

  @Override
  public void close() throws IOException {
    if (file != null) {
      file.close();
    }
  }

  private static int packIndex(int x, int z) {
    return (z & BITMASK_LOCAL_CHUNK) << 5 | (x & BITMASK_LOCAL_CHUNK);
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

}
