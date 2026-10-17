package com.csc210.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

/** SQLite migration for notification tables and their query indexes. */
@Component
public class NotificationSchemaMigrator {
    private static final List<String> NOTIFICATION_TYPES = List.of(
            "REPORT_SUBMITTED", "POSSIBLE_MATCH", "CLAIM_CREATED",
            // Kept so historical notification rows remain readable.
            "CLAIM_VERIFIED", "CLAIM_APPROVED_FOR_HANDOVER", "CLAIM_REJECTED",
            "CLAIM_RESOLVED", "CLAIM_CANCELLED");
    private final DataSource dataSource;

    public NotificationSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean foreignKeysWereEnabled = foreignKeysEnabled(connection);
            setForeignKeysEnabled(connection, false);
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS notifications (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                recipient_id BIGINT NOT NULL,
                                type VARCHAR(64) NOT NULL,
                                title VARCHAR(160) NOT NULL,
                                message VARCHAR(1000) NOT NULL,
                                action_url VARCHAR(255),
                                dedupe_key VARCHAR(255),
                                created_at TIMESTAMP NOT NULL,
                                is_read BOOLEAN NOT NULL DEFAULT 0,
                                read_at TIMESTAMP
                            )
                            """);
                    statement.executeUpdate("""
                            CREATE TABLE IF NOT EXISTS user_notification_preferences (
                                id INTEGER PRIMARY KEY AUTOINCREMENT,
                                user_id BIGINT NOT NULL UNIQUE,
                                in_app_enabled BOOLEAN NOT NULL DEFAULT 1,
                                email_enabled BOOLEAN NOT NULL DEFAULT 0,
                                match_alerts BOOLEAN NOT NULL DEFAULT 1,
                                report_updates BOOLEAN NOT NULL DEFAULT 1,
                                claim_updates BOOLEAN NOT NULL DEFAULT 1
                            )
                            """);
                    if (!hasColumn(connection, "notifications", "dedupe_key")) {
                        statement.executeUpdate("ALTER TABLE notifications ADD COLUMN dedupe_key VARCHAR(255)");
                    }
                    // Existing Hibernate DDL used a CHECK over the original
                    // enum values. The replacement keeps all rows and indexes
                    // while permitting CLAIM_APPROVED_FOR_HANDOVER.
                    SqliteEnumConstraintMigrator.rebuildIfEnumCheckIsOutdated(connection, "notifications", "type",
                            NOTIFICATION_TYPES, Map.of());
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notification_recipient_created ON notifications (recipient_id, created_at)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_notification_recipient_unread ON notifications (recipient_id, is_read)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS notification_recipient_created_idx ON notifications (recipient_id, created_at DESC)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS notification_recipient_unread_idx ON notifications (recipient_id, is_read)");
                    statement.executeUpdate("CREATE INDEX IF NOT EXISTS notification_recipient_dedupe_idx ON notifications (recipient_id, dedupe_key)");
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
                setForeignKeysEnabled(connection, foreignKeysWereEnabled);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize notification storage.", exception);
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
            while (columns.next()) if (column.equalsIgnoreCase(columns.getString("name"))) return true;
            return false;
        }
    }

    private boolean foreignKeysEnabled(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet value = statement.executeQuery("PRAGMA foreign_keys")) {
            return value.next() && value.getInt(1) != 0;
        }
    }

    private void setForeignKeysEnabled(Connection connection, boolean enabled) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = " + (enabled ? "ON" : "OFF"));
        }
    }
}
