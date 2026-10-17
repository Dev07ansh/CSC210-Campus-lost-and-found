package com.csc210.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.*;
import java.util.List;
import java.util.Map;

/** SQLite needs explicit indexes for constraints that Hibernate's update mode may skip. */
@Component
public class ClaimSchemaMigrator {
    private static final List<String> CLAIM_STATUSES = List.of(
            "PENDING_STAFF_REVIEW", "APPROVED_FOR_HANDOVER", "RESOLVED", "REJECTED", "CANCELLED",
            // Read aliases remain supported while an interrupted older migration is repaired.
            "PENDING_VERIFICATION", "VERIFIED");
    private static final Map<String, String> LEGACY_STATUS_REPLACEMENTS = Map.of(
            "PENDING_VERIFICATION", "PENDING_STAFF_REVIEW",
            "VERIFIED", "APPROVED_FOR_HANDOVER");
    private final DataSource dataSource;

    public ClaimSchemaMigrator(DataSource dataSource) { this.dataSource = dataSource; }

    @PostConstruct
    public void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            boolean foreignKeysWereEnabled = foreignKeysEnabled(connection);
            // Rebuilding an SQLite table requires DROP/RENAME. Disable FK
            // enforcement before the transaction so child tables continue to
            // reference the final table name after the atomic replacement.
            setForeignKeysEnabled(connection, false);
            connection.setAutoCommit(false);
            try {
                try (Statement statement = connection.createStatement()) {
                    ensureReportOwnershipColumns(connection, statement);
                    createClaimTables(statement);
                    ensureStaffReviewColumns(connection, statement);
                    // An older Hibernate-generated table has a CHECK which
                    // rejects PENDING_STAFF_REVIEW. Rebuild first and transform
                    // legacy values while copying; an UPDATE before this point
                    // would itself fail the old CHECK.
                    SqliteEnumConstraintMigrator.rebuildIfEnumCheckIsOutdated(connection, "claims", "status",
                            CLAIM_STATUSES, LEGACY_STATUS_REPLACEMENTS);
                    migrateLegacySecrets(connection);
                    migrateLegacyClaimStatuses(connection, statement);
                    statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS lost_item_secret_lost_item_unique ON lost_item_secret (lost_item_id)");
                    statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS claim_attempt_number_unique ON claim_verification_attempt (claim_id, attempt_number)");
                    rebuildClaimIndexes(statement);
                    if (tableExists(connection, "user")) {
                        statement.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS user_email_unique ON \"user\" (email)");
                    }
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
            throw new IllegalStateException("Could not initialize claim workflow storage.", exception);
        }
    }

    private void ensureReportOwnershipColumns(Connection connection, Statement statement) throws SQLException {
        if (tableExists(connection, "lost_item") && !hasColumn(connection, "lost_item", "reporter_id")) {
            statement.executeUpdate("ALTER TABLE lost_item ADD COLUMN reporter_id BIGINT");
        }
        if (tableExists(connection, "found_item") && !hasColumn(connection, "found_item", "reporter_id")) {
            statement.executeUpdate("ALTER TABLE found_item ADD COLUMN reporter_id BIGINT");
        }
    }

    private void createClaimTables(Statement statement) throws SQLException {
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS lost_item_secret (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    lost_item_id BIGINT NOT NULL,
                    interior_color_or_lining VARCHAR(255),
                    unique_marks VARCHAR(255),
                    contents VARCHAR(255)
                )
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS claims (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    version BIGINT NOT NULL DEFAULT 0,
                    lost_item_id BIGINT NOT NULL,
                    found_item_id BIGINT NOT NULL,
                    claimant_id BIGINT NOT NULL,
                    finder_id BIGINT NOT NULL,
                    status VARCHAR(64) NOT NULL,
                    match_score DOUBLE NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    verified_at TIMESTAMP,
                    resolved_at TIMESTAMP,
                    cancelled_at TIMESTAMP,
                    handover_code_hash VARCHAR(255),
                    handover_code_expires_at TIMESTAMP
                )
                """);
        statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS claim_verification_attempt (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    claim_id BIGINT NOT NULL,
                    attempt_number INTEGER NOT NULL,
                    score DOUBLE NOT NULL,
                    passed BOOLEAN NOT NULL,
                    attempted_at TIMESTAMP NOT NULL
                )
                """);
    }

    /** Adds review audit fields without dropping an existing claims table. */
    private void ensureStaffReviewColumns(Connection connection, Statement statement) throws SQLException {
        ensureColumn(connection, statement, "claims", "reviewed_by_id", "BIGINT");
        ensureColumn(connection, statement, "claims", "reviewed_at", "TIMESTAMP");
        ensureColumn(connection, statement, "claims", "staff_review_note", "VARCHAR(2000)");
        ensureColumn(connection, statement, "claims", "completed_by_id", "BIGINT");
        ensureColumn(connection, statement, "claims", "completed_at", "TIMESTAMP");
    }

    /**
     * Old claims used a self-service verification lifecycle. Upgrade their
     * state before the active-claim indexes are rebuilt, but keep enum aliases
     * in Java as a safeguard for a partially completed legacy migration.
     */
    private void migrateLegacyClaimStatuses(Connection connection, Statement statement) throws SQLException {
        if (!tableExists(connection, "claims")) return;
        statement.executeUpdate("UPDATE claims SET status = 'PENDING_STAFF_REVIEW' WHERE status = 'PENDING_VERIFICATION'");
        statement.executeUpdate("""
                UPDATE claims
                SET status = 'APPROVED_FOR_HANDOVER',
                    reviewed_at = COALESCE(reviewed_at, verified_at)
                WHERE status = 'VERIFIED'
                """);
        // Statuses copied from an old table may already have been translated
        // by the enum-CHECK rebuild. Preserve the old verification timestamp
        // as the staff-review timestamp in that case as well.
        statement.executeUpdate("""
                UPDATE claims
                SET reviewed_at = COALESCE(reviewed_at, verified_at)
                WHERE status = 'APPROVED_FOR_HANDOVER'
                  AND verified_at IS NOT NULL
                """);
    }

    private void rebuildClaimIndexes(Statement statement) throws SQLException {
        // Hibernate's schema updater will usually make these, but a SQLite
        // table replacement drops every table-owned index. Recreate them here
        // so lookup performance never depends on startup ordering.
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_claim_claimant ON claims (claimant_id)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_claim_finder ON claims (finder_id)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_claim_lost_item ON claims (lost_item_id)");
        statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_claim_found_item ON claims (found_item_id)");
        statement.executeUpdate("DROP INDEX IF EXISTS claims_one_active_lost_item");
        statement.executeUpdate("DROP INDEX IF EXISTS claims_one_active_found_item");
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS claims_one_active_lost_item
                ON claims (lost_item_id)
                WHERE status IN ('PENDING_STAFF_REVIEW', 'APPROVED_FOR_HANDOVER', 'PENDING_VERIFICATION', 'VERIFIED')
                """);
        statement.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS claims_one_active_found_item
                ON claims (found_item_id)
                WHERE status IN ('PENDING_STAFF_REVIEW', 'APPROVED_FOR_HANDOVER', 'PENDING_VERIFICATION', 'VERIFIED')
                """);
    }

    /**
     * Moves complete legacy values into the private table, then clears every old
     * verification column in the same transaction. Keeping a copied plaintext
     * secret in lost_item would defeat the separation introduced by this model.
     */
    private void migrateLegacySecrets(Connection connection) throws SQLException {
        if (!tableExists(connection, "lost_item")
                || !hasColumn(connection, "lost_item", "interior_color_or_lining")
                || !hasColumn(connection, "lost_item", "unique_marks")
                || !hasColumn(connection, "lost_item", "contents")) return;
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT id, interior_color_or_lining, unique_marks, contents
                FROM lost_item
                """);
             PreparedStatement insert = connection.prepareStatement("""
                INSERT OR IGNORE INTO lost_item_secret
                (lost_item_id, interior_color_or_lining, unique_marks, contents)
                VALUES (?, ?, ?, ?)
                 """);
             PreparedStatement clearLegacy = connection.prepareStatement("""
                 UPDATE lost_item
                 SET interior_color_or_lining = NULL,
                     unique_marks = NULL,
                     contents = NULL
                 WHERE interior_color_or_lining IS NOT NULL
                    OR unique_marks IS NOT NULL
                    OR contents IS NOT NULL
                 """)) {
            try (ResultSet rows = select.executeQuery()) {
                while (rows.next()) {
                    String interior = rows.getString("interior_color_or_lining");
                    String marks = rows.getString("unique_marks");
                    String contents = rows.getString("contents");
                    if (!meaningful(interior) || !meaningful(marks) || !meaningful(contents)) continue;
                    insert.setLong(1, rows.getLong("id"));
                    insert.setString(2, interior);
                    insert.setString(3, marks);
                    insert.setString(4, contents);
                    insert.addBatch();
                }
            }
            insert.executeBatch();
            clearLegacy.executeUpdate();
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            query.setString(1, table);
            try (ResultSet result = query.executeQuery()) { return result.next(); }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("PRAGMA table_info(\"" + table + "\")")) {
            while (columns.next()) if (column.equalsIgnoreCase(columns.getString("name"))) return true;
            return false;
        }
    }

    private void ensureColumn(Connection connection, Statement statement, String table, String column, String type) throws SQLException {
        if (!hasColumn(connection, table, column)) {
            statement.executeUpdate("ALTER TABLE \"" + table + "\" ADD COLUMN \"" + column + "\" " + type);
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

    private boolean meaningful(String value) { return value != null && value.matches(".*[\\p{L}\\p{N}].*"); }
}
