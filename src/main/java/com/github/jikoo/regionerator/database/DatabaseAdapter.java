package com.github.jikoo.regionerator.database;

import com.github.jikoo.regionerator.ChunkFlagger;
import com.github.jikoo.regionerator.Regionerator;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * Interface defining behavior for interacting with a database.
 * <p>
 * All methods should expect to be called from multiple threads.
 *
 * @author Jikoo
 */
public interface DatabaseAdapter {

	void close();

	void update(@NotNull Collection<ChunkFlagger.FlagData> flags) throws Exception;

	long get(@NotNull String identifier) throws Exception;

	static @NotNull DatabaseAdapter getAdapter(@NotNull Regionerator plugin) throws Exception {
		Class.forName("org.sqlite.JDBC");

		Connection connection = DriverManager.getConnection("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/data.db");
		DatabaseMetaData metaData = connection.getMetaData();

		if (metaData.getDatabaseMajorVersion() < 3 || metaData.getDatabaseMajorVersion() == 3 && metaData.getDatabaseMinorVersion() < 24) {
			// Terrible SQLite
			return new SQLeadenAdapter(plugin, connection);
		}

		return new SQLiteAdapter(plugin, connection);

	}

}
