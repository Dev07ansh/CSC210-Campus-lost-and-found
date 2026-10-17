package com.csc210.backend.model;

/**
 * Describes a user-visible event. The category is deliberately part of the
 * type so a user can turn a whole group of alerts on or off without exposing
 * any private report or verification data.
 */
public enum NotificationType {
    REPORT_SUBMITTED(NotificationCategory.REPORT),
    POSSIBLE_MATCH(NotificationCategory.MATCH),
    CLAIM_CREATED(NotificationCategory.CLAIM),
    CLAIM_VERIFIED(NotificationCategory.CLAIM),
    CLAIM_APPROVED_FOR_HANDOVER(NotificationCategory.CLAIM),
    CLAIM_REJECTED(NotificationCategory.CLAIM),
    CLAIM_RESOLVED(NotificationCategory.CLAIM),
    CLAIM_CANCELLED(NotificationCategory.CLAIM);

    private final NotificationCategory category;

    NotificationType(NotificationCategory category) {
        this.category = category;
    }

    public NotificationCategory category() {
        return category;
    }

    public enum NotificationCategory {
        REPORT,
        MATCH,
        CLAIM
    }
}
