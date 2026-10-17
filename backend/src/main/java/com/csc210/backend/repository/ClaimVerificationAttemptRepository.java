package com.csc210.backend.repository;

import com.csc210.backend.model.ClaimVerificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimVerificationAttemptRepository extends JpaRepository<ClaimVerificationAttempt, Long> {
    int countByClaimId(Long claimId);
}
