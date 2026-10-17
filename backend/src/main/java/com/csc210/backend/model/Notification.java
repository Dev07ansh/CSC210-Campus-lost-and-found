package com.csc210.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A persisted in-app notification. It never includes secret verification values or answers. */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_recipient_created", columnList = "recipient_id,created_at"),
        @Index(name = "idx_notification_recipient_unread", columnList = "recipient_id,is_read")
})
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "action_url", length = 255)
    private String actionUrl;

    /** Internal idempotency key; it is never returned to browser clients. */
    @JsonIgnore
    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public Notification() {
    }

    public Notification(User recipient, NotificationType type, String title, String message, String actionUrl, String dedupeKey) {
        this.recipient = recipient;
        this.type = type;
        this.title = title;
        this.message = message;
        this.actionUrl = actionUrl;
        this.dedupeKey = dedupeKey;
        this.createdAt = LocalDateTime.now();
        this.read = false;
    }

    public Long getId() { return id; }
    @JsonIgnore public User getRecipient() { return recipient; }
    public void setRecipient(User value) { recipient = value; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType value) { type = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public String getActionUrl() { return actionUrl; }
    public void setActionUrl(String value) { actionUrl = value; }
    @JsonIgnore public String getDedupeKey() { return dedupeKey; }
    public void setDedupeKey(String value) { dedupeKey = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { createdAt = value; }
    public boolean isRead() { return read; }
    public void setRead(boolean value) { read = value; }
    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime value) { readAt = value; }
}
