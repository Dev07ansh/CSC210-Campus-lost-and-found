package com.csc210.backend.repository;

import com.csc210.backend.model.LostItemSecret;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LostItemSecretRepository extends JpaRepository<LostItemSecret, Long> {
    Optional<LostItemSecret> findByLostItemId(Long lostItemId);
}
