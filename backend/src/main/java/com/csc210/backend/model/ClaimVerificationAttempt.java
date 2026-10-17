package com.csc210.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Legacy audit records from the retired self-service verification flow. New
 * claims never create these records; staff review metadata lives on Claim.
 */
@Entity
@Table(name = "claim_verification_attempt", uniqueConstraints = @UniqueConstraint(columnNames = {"claim_id", "attempt_number"}))
public class ClaimVerificationAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id", nullable = false) private Claim claim;
    @Column(name = "attempt_number", nullable = false) private int attemptNumber;
    /** Minimum of the three answer scores; this is the score used for the all-answers threshold. */
    @Column(nullable = false) private double score;
    @Column(nullable = false) private boolean passed;
    @Column(nullable = false) private LocalDateTime attemptedAt;

    public ClaimVerificationAttempt() {}
    public ClaimVerificationAttempt(Claim claim, int attemptNumber, double score, boolean passed, LocalDateTime attemptedAt) {
        this.claim=claim; this.attemptNumber=attemptNumber; this.score=score; this.passed=passed; this.attemptedAt=attemptedAt;
    }
    public Long getId(){return id;}
    public Claim getClaim(){return claim;} public void setClaim(Claim value){claim=value;}
    public int getAttemptNumber(){return attemptNumber;} public void setAttemptNumber(int value){attemptNumber=value;}
    public double getScore(){return score;} public void setScore(double value){score=value;}
    public boolean isPassed(){return passed;} public void setPassed(boolean value){passed=value;}
    public LocalDateTime getAttemptedAt(){return attemptedAt;} public void setAttemptedAt(LocalDateTime value){attemptedAt=value;}
}
