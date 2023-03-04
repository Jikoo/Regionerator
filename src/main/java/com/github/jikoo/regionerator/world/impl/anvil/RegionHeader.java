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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class RegionHeader {

  public static final Pattern REGION_FILE_PATTERN = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)(\\.mc[ar])");

  // Useful constants for various implementations of region file parsing.
  protected static final int SECTOR_BYTES = 4096;
  protected static final int SECTOR_INTS = SECTOR_BYTES >> 2;
  protected static final int HEADER_SECTORS = 2;
  protected static final int HEADER_LENGTH = SECTOR_BYTES * HEADER_SECTORS;
  protected static final int CHUNK_HEADER_LENGTH = 5;
  protected static final int CHUNK_MAXIMUM_SECTORS = 255;
  protected static final int CHUNK_TOO_LARGE_FLAG = 128;

  protected final Path regionPath;
  protected final boolean sync;
  private final ByteBuffer header;
  protected final IntBuffer offsets;
  protected final IntBuffer timestamps;

  public RegionHeader(@NotNull Path regionPath, boolean sync) {
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
    header = ByteBuffer.allocateDirect(SECTOR_BYTES * HEADER_SECTORS);
    offsets = header.slice(0, SECTOR_BYTES).asIntBuffer();
    timestamps = header.position(SECTOR_BYTES).slice().asIntBuffer();
    header.position(0);

    // TODO should we just open here and leave open, have read/write methods?
    //  - Windows may be an issue. Minimizing risk of blocking MC saving > ease of use.
    //  For our use case where we do a read, process, then re-read, edit, and write,
    //  this approach may be better. Reduce performance loss of allocation.
    //  If that's the case may be better off having some kind of reusable buffer.
  }

  protected final FileChannel openAndReadHeader(boolean write) throws IOException {
    // TODO AsyncFileChannel? Supposedly faster due to less internal sync. We can single thread ourselves. Windows again
    FileChannel region;
    // TODO this looks like trash, condense. Does not using write even help? Benchmark and test
    //  Windows a concern AGAIN, because read may not lock while write will
    if (write) {
      if (sync) {
        // DSYNC and not SYNC because there's no need to write meta - MC doesn't use SPARSE, so we can't either.
        // If underlying system always is SPARSE then DSYNC and SYNC should perform identically.
        region = FileChannel.open(regionPath, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
      } else {
        region = FileChannel.open(regionPath, StandardOpenOption.READ, StandardOpenOption.WRITE);
      }
    } else {
      if (sync) {
        region = FileChannel.open(regionPath, StandardOpenOption.READ, StandardOpenOption.DSYNC);
      } else {
        region = FileChannel.open(regionPath, StandardOpenOption.READ);
      }
    }

    header.rewind();
    int bytesRead = region.read(header);
    header.flip();

    if (bytesRead != -1 && bytesRead < HEADER_LENGTH) {
      // TODO probably should be IllegalStateException instead.
      //  If data is truncated to the point where there isn't even a header, the whole region is just empty.
      // Region file is not valid.
      getLogger().warning(() -> String.format(
              "Invalid header %s; read %s bytes of expected %s",
              regionPath.getFileName(),
              bytesRead,
              HEADER_LENGTH));
    }

    return region;
  }

  protected void writeHeader(@NotNull FileChannel channel) throws IOException {
    header.rewind();
    channel.write(header, 0);
  }

  protected abstract @NotNull Logger getLogger();

}
