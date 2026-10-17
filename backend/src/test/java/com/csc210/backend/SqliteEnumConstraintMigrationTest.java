package com.csc210.backend;

import com.csc210.backend.config.ClaimSchemaMigrator;
import com.csc210.backend.config.NotificationSchemaMigrator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteEnumConstraintMigrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rebuildsLegacyEnumChecksWithoutLosingRowsColumnsOrIndexes() throws Exception {
        DataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + temporaryDirectory.resolve("legacy.db"));
        seedLegacyTables(dataSource);

        new ClaimSchemaMigrator(dataSource).migrate();
        new NotificationSchemaMigrator(dataSource).migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet claim = statement.executeQuery("""
                    SELECT id, status, reviewed_by_id, completed_at
                    FROM claims WHERE id = 41
                    """)) {
                assertTrue(claim.next());
                assertEquals(41L, claim.getLong("id"));
                assertEquals("PENDING_STAFF_REVIEW", claim.getString("status"));
                assertEquals(7L, claim.getLong("reviewed_by_id"));
                assertNotNull(claim.getString("completed_at"));
            }
            try (ResultSet claim = statement.executeQuery("""
                    SELECT id, status, reviewed_at
                    FROM claims WHERE id = 42
                    """)) {
                assertTrue(claim.next());
                assertEquals(42L, claim.getLong("id"));
                assertEquals("APPROVED_FOR_HANDOVER", claim.getString("status"));
                assertEquals("2026-10-05T10:00:00", claim.getString("reviewed_at"));
            }
            try (ResultSet notification = statement.executeQuery("SELECT id, type FROM notifications WHERE id = 9")) {
                assertTrue(notification.next());
                assertEquals(9L, notification.getLong("id"));
                assertEquals("CLAIM_VERIFIED", notification.getString("type"));
            }

            statement.executeUpdate("""
                    INSERT INTO claims (version, lost_item_id, found_item_id, claimant_id, finder_id, status, match_score, created_at)
                    VALUES (0, 51, 52, 53, 54, 'PENDING_STAFF_REVIEW', 0.91, '2026-10-17T10:00:00')
                    """);
            statement.executeUpdate("""
                    INSERT INTO notifications (recipient_id, type, title, message, created_at, is_read)
                    VALUES (1, 'CLAIM_APPROVED_FOR_HANDOVER', 'Approved', 'Staff approved the claim.', '2026-10-17T10:00:00', 0)
                    """);

            assertIndexExists(statement, "legacy_claim_score_index");
            assertIndexExists(statement, "legacy_notification_title_index");
            assertIndexExists(statement, "idx_claim_claimant");
            assertIndexExists(statement, "idx_claim_finder");
            assertIndexExists(statement, "idx_claim_lost_item");
            assertIndexExists(statement, "idx_claim_found_item");
            assertIndexExists(statement, "idx_notification_recipient_created");
            assertIndexExists(statement, "idx_notification_recipient_unread");
        }
    }

    private void seedLegacyTables(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE claims (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        version BIGINT NOT NULL DEFAULT 0,
                        lost_item_id BIGINT NOT NULL,
                        found_item_id BIGINT NOT NULL,
                        claimant_id BIGINT NOT NULL,
                        finder_id BIGINT NOT NULL,
                        status VARCHAR(255) NOT NULL CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'RESOLVED', 'REJECTED', 'CANCELLED')),
                        match_score DOUBLE NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        verified_at TIMESTAMP,
                        resolved_at TIMESTAMP,
                        cancelled_at TIMESTAMP,
                        handover_code_hash VARCHAR(255),
                        handover_code_expires_at TIMESTAMP,
                        reviewed_by_id BIGINT,
                        reviewed_at TIMESTAMP,
                        staff_review_note VARCHAR(2000),
                        completed_by_id BIGINT,
                        completed_at TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE INDEX legacy_claim_score_index ON claims (match_score)");
            statement.executeUpdate("""
                    INSERT INTO claims (id, version, lost_item_id, found_item_id, claimant_id, finder_id, status, match_score,
                                        created_at, verified_at, reviewed_by_id, completed_at)
                    VALUES (41, 0, 11, 12, 13, 14, 'PENDING_VERIFICATION', 0.75,
                            '2026-10-01T10:00:00', '2026-10-02T10:00:00', 7, '2026-10-03T10:00:00')
                    """);
            statement.executeUpdate("""
                    INSERT INTO claims (id, version, lost_item_id, found_item_id, claimant_id, finder_id, status, match_score,
                                        created_at, verified_at)
                    VALUES (42, 0, 21, 22, 23, 24, 'VERIFIED', 0.80,
                            '2026-10-04T10:00:00', '2026-10-05T10:00:00')
                    """);

            statement.executeUpdate("""
                    CREATE TABLE notifications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        recipient_id BIGINT NOT NULL,
                        type VARCHAR(64) NOT NULL CHECK (type IN ('REPORT_SUBMITTED', 'POSSIBLE_MATCH', 'CLAIM_CREATED', 'CLAIM_VERIFIED', 'CLAIM_REJECTED', 'CLAIM_RESOLVED', 'CLAIM_CANCELLED')),
                        title VARCHAR(160) NOT NULL,
                        message VARCHAR(1000) NOT NULL,
                        action_url VARCHAR(255),
                        dedupe_key VARCHAR(255),
                        created_at TIMESTAMP NOT NULL,
                        is_read BOOLEAN NOT NULL DEFAULT 0,
                        read_at TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE INDEX legacy_notification_title_index ON notifications (title)");
            statement.executeUpdate("""
                    INSERT INTO notifications (id, recipient_id, type, title, message, created_at, is_read)
                    VALUES (9, 1, 'CLAIM_VERIFIED', 'Claim verified', 'Old notification', '2026-10-01T10:00:00', 0)
                    """);
        }
    }

    private void assertIndexExists(Statement statement, String name) throws Exception {
        try (ResultSet rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'index' AND name = '" + name + "'")) {
            assertTrue(rows.next(), () -> "Expected index " + name + " to be retained.");
        }
    }
}
