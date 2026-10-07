package com.csc210.backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Ensures SQLite enforces one account-level verification-attempt record per claimant email. */
@Component
public class VerificationAttemptSchemaMigrator {
    private final DataSource dataSource;

    public VerificationAttemptSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void enforceUniqueClaimantEmail() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            try {
                statement.executeUpdate("""
                        UPDATE verification_attempt AS retained
                        SET failed_attempts = (
                            SELECT MAX(candidate.failed_attempts)
                            FROM verification_attempt AS candidate
                            WHERE candidate.claimer_email = retained.claimer_email
                        ),
                        suspended = (
                            SELECT MAX(candidate.suspended)
                            FROM verification_attempt AS candidate
                            WHERE candidate.claimer_email = retained.claimer_email
                        )
                        WHERE retained.id IN (
                            SELECT MAX(id) FROM verification_attempt GROUP BY claimer_email
                        )
                        """);
                statement.executeUpdate("""
                        DELETE FROM verification_attempt
                        WHERE id NOT IN (
                            SELECT MAX(id) FROM verification_attempt GROUP BY claimer_email
                        )
                        """);
                statement.executeUpdate("""
                        CREATE UNIQUE INDEX IF NOT EXISTS verification_attempt_claimer_email_unique
                        ON verification_attempt (claimer_email)
                        """);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not initialize ownership-verification storage.", exception);
        }
    }
}
