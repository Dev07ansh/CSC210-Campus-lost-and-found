package com.csc210.backend.repository;

import com.csc210.backend.model.Notification;
import com.csc210.backend.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);
    List<Notification> findByRecipientIdAndReadFalseOrderByCreatedAtDesc(Long recipientId);
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);
    long countByRecipientIdAndReadFalse(Long recipientId);
    boolean existsByRecipientIdAndDedupeKey(Long recipientId, String dedupeKey);
}
