package com.github.jikoo.regionerator.database;

import com.github.jikoo.regionerator.ChunkFlagger;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.util.yaml.Config;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter for modern SQLite.
 */
public class SQLiteAdapter extends SQLeadenAdapter {

	SQLiteAdapter(@NotNull Regionerator plugin, @NotNull Connection database) throws SQLException {
		super(plugin, database);

		// Set up triggers
		try (Statement st = database.createStatement()) {
			st.executeUpdate(
					"CREATE TRIGGER IF NOT EXISTS chunkdataold\n" +
							"AFTER DELETE ON chunkdata\n" +
							"WHEN OLD.time NOT NULL AND OLD.chunk_id NOT LIKE '%_old'\n" +
							"BEGIN\n" +
							"INSERT INTO chunkdata(chunk_id,time) VALUES (OLD.chunk_id || '_old',OLD.time) ON CONFLICT(chunk_id) DO UPDATE SET `time`=OLD.time;\n" +
							"END");
			database.commit();
		} catch (SQLException e) {
			plugin.getLogger().severe("An exception setting up database trigger!");
			try {
				DatabaseMetaData metaData = database.getMetaData();
				plugin.getLogger().warning(String.format("SQLite driver: %s %s", metaData.getDriverName(), metaData.getDriverVersion()));
				plugin.getLogger().warning(String.format("SQLite database: %s %s", metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion()));
			} catch (SQLException ignored) {
			}
			throw e;
		}
	}

	@Override
	public void update(@NotNull Collection<ChunkFlagger.FlagData> flags) throws SQLException {
		synchronized (database) {
			try (PreparedStatement upsert = database.prepareStatement("INSERT INTO chunkdata(chunk_id,time) VALUES (?,?) ON CONFLICT(chunk_id) DO UPDATE SET time=excluded.time WHERE excluded.time>chunkdata.time");
				PreparedStatement delete = database.prepareStatement("DELETE FROM chunkdata WHERE chunk_id=?")) {
				for (ChunkFlagger.FlagData data : flags) {
					if (data.getLastVisit() == Config.FLAG_DEFAULT) {
						delete.setString(1, data.getChunkId());
						delete.addBatch();
					} else {
						upsert.setString(1, data.getChunkId());
						upsert.setLong(2, data.getLastVisit());
						upsert.addBatch();
					}
				}
				delete.executeBatch();
				upsert.executeBatch();
				this.database.commit();
			}
		}
	}

}
