package com.github.jikoo.regionerator.database;

import com.github.jikoo.regionerator.ChunkFlagger;
import com.github.jikoo.regionerator.Regionerator;
import com.github.jikoo.regionerator.util.Config;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.logging.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Adapter for old versions of SQLite.
 */
public class SQLeadenAdapter implements DatabaseAdapter {

	private final Regionerator plugin;
	final Connection database;

	SQLeadenAdapter(@NotNull Regionerator plugin, @NotNull Connection database) throws SQLException {
		this.plugin = plugin;
		this.database = database;

		// Set up database
		try (Statement st = database.createStatement()) {
			st.executeUpdate("CREATE TABLE IF NOT EXISTS `chunkdata`(`chunk_id` TEXT NOT NULL UNIQUE, `time` BIGINT NOT NULL)");
		}

		database.setAutoCommit(false);
	}

	@Override
	public void close() {
		synchronized (database) {
			try {
				database.commit();
				database.close();
			} catch (SQLException e) {
				plugin.getLogger().log(Level.SEVERE, "Exception committing to and closing DB connection", e);
			}
		}
	}

	@Override
	public void update(@NotNull Collection<ChunkFlagger.FlagData> flags) throws SQLException {
		synchronized (database) {
			try (PreparedStatement boyIWishThisWasAnUpsert = database.prepareStatement("INSERT OR REPLACE INTO chunkdata(chunk_id,time) VALUES (?, MAX(COALESCE((SELECT time FROM chunkdata WHERE chunk_id=?),0),?))");
				PreparedStatement deleteForeverBecauseReplaceEqualsDeleteThenInsertFrownyFace = database.prepareStatement("DELETE FROM chunkdata WHERE chunk_id=?")) {
				for (ChunkFlagger.FlagData data : flags) {
					if (data.getLastVisit() == Config.getFlagDefault()) {
						deleteForeverBecauseReplaceEqualsDeleteThenInsertFrownyFace.setString(1, data.getChunkId());
						deleteForeverBecauseReplaceEqualsDeleteThenInsertFrownyFace.addBatch();
					} else {
						boyIWishThisWasAnUpsert.setString(1, data.getChunkId());
						boyIWishThisWasAnUpsert.setString(2, data.getChunkId());
						boyIWishThisWasAnUpsert.setLong(3, data.getLastVisit());
						boyIWishThisWasAnUpsert.addBatch();
					}
				}
				deleteForeverBecauseReplaceEqualsDeleteThenInsertFrownyFace.executeBatch();
				boyIWishThisWasAnUpsert.executeBatch();
				this.database.commit();
			}
		}
	}

	@Override
	public long get(@NotNull String identifier) throws SQLException {
		synchronized (database) {
			if (database.isClosed()) {
				return Config.getFlagEternal();
			}

			try (PreparedStatement st = database.prepareStatement("SELECT time FROM chunkdata WHERE chunk_id=?")) {
				st.setString(1, identifier);
				try (ResultSet rs = st.executeQuery()) {
					if (rs.next()) {
						return rs.getLong(1);
					} else {
						return Config.getFlagDefault();
					}
				}
			}
		}
	}

}
