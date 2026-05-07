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

import java.nio.file.OpenOption;
import java.nio.file.StandardOpenOption;

/**
 * A wrapper for I/O modes.
 */
public enum AccessMode {

  /** Read-only mode. */
  READ("r", new OpenOption[]{ StandardOpenOption.READ }),
  /** Read and write mode. Can also create new files if they do not exist. */
  WRITE("rw", new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE }),
  /** {@link #WRITE} with all writes to the file or its metadata being synchronized with the underlying storage. */
  WRITE_SYNC("rws", new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.SYNC }),
  /** {@link #WRITE} with all writes to the file being synchronized with the underlying storage. */
  WRITE_DSYNC("rwd", new OpenOption[] { StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.DSYNC });

  private final @NotNull String mode;
  private final OpenOption @NotNull [] options;

  AccessMode(@NotNull String mode, OpenOption @NotNull [] options) {
    this.mode = mode;
    this.options = options;
  }

  /**
   * Get the {@link java.io.RandomAccessFile#RandomAccessFile(java.io.File,String) mode} for a
   * {@link java.io.RandomAccessFile RandomAccessFile}.
   *
   * @return the corresponding mode string
   */
  public String asRandomAccessMode() {
    return mode;
  }

  /**
   * Get an array of {@link OpenOption OpenOptions} for use in a {@link java.nio.channels.FileChannel FileChannel} or
   * similar.
   *
   * @return the corresponding options
   */
  public OpenOption[] asOpenOptions() {
    return options;
  }

}
