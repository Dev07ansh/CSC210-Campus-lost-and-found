package com.csc210.backend.repository;

import com.csc210.backend.model.VerificationAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VerificationAttemptRepository extends JpaRepository<VerificationAttempt, Long> {
    Optional<VerificationAttempt> findByClaimerEmail(String claimerEmail);
}
