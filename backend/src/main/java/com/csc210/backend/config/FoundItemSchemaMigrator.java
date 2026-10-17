package com.csc210.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Adds the collection-desk column without inventing a custody location for
 * legacy found reports. SQLite triggers enforce the two permitted values for
 * future inserts and desk changes while allowing existing null values to be
 * reviewed and assigned by staff later.
 */
@Component
public class FoundItemSchemaMigrator {
    private static final String TABLE = "found_item";
    private final DataSource dataSource;

    public FoundItemSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                if (!tableExists(connection, TABLE)) {
                    connection.commit();
                    return;
                }
                if (!hasColumn(connection, TABLE, "collection_desk")) {
                    statement.executeUpdate("ALTER TABLE found_item ADD COLUMN collection_desk VARCHAR(64)");
                }
                statement.executeUpdate("""
                        CREATE TRIGGER IF NOT EXISTS found_item_collection_desk_insert_valid
                        BEFORE INSERT ON found_item
                        WHEN NEW.collection_desk IS NULL
                          OR NEW.collection_desk NOT IN ('University Centre Reception', 'Gate 12')
                        BEGIN
                            SELECT RAISE(ABORT, 'Found-item collection desk must be University Centre Reception or Gate 12');
                        END
                        """);
                statement.executeUpdate("""
                        CREATE TRIGGER IF NOT EXISTS found_item_collection_desk_update_valid
                        BEFORE UPDATE OF collection_desk ON found_item
                        WHEN NEW.collection_desk IS NULL
                          OR NEW.collection_desk NOT IN ('University Centre Reception', 'Gate 12')
                        BEGIN
                            SELECT RAISE(ABORT, 'Found-item collection desk must be University Centre Reception or Gate 12');
                        END
                        """);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize found-item collection desk storage.", exception);
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) return true;
            }
            return false;
        }
    }
}
