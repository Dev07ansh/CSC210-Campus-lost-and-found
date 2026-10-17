package com.csc210.backend.service;

import com.csc210.backend.model.*;
import com.csc210.backend.repository.NotificationRepository;
import com.csc210.backend.repository.UserNotificationPreferencesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Central notification policy. This service receives only public report/claim
 * summaries; secret verification details and raw answers are never supplied to it.
 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserNotificationPreferencesRepository preferences;
    private final EmailNotificationSender email;

    public NotificationService(NotificationRepository notifications,
                               UserNotificationPreferencesRepository preferences,
                               EmailNotificationSender email) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.email = email;
    }

    @Transactional
    public void reportSubmitted(User recipient, boolean lostReport, String itemName) {
        String reportKind = lostReport ? "lost" : "found";
        publish(recipient, NotificationType.REPORT_SUBMITTED,
                (lostReport ? "Lost" : "Found") + " report submitted",
                "Your " + reportKind + " report for \"" + label(itemName) + "\" was recorded.",
                "reports", null);
    }

    @Transactional
    public void possibleMatch(User recipient, Long lostItemId, Long foundItemId, String lostItemName, double score) {
        if (lostItemId == null || foundItemId == null) return;
        publish(recipient, NotificationType.POSSIBLE_MATCH,
                "Possible match found",
                "A newly reported found item may match your lost report \"" + label(lostItemName)
                        + "\" (similarity " + Math.round(score * 100) + "%).",
                "item-lost-" + lostItemId, "possible-match:" + lostItemId + ":" + foundItemId);
    }

    @Transactional
    public void claimCreated(Claim claim) {
        String route = claimRoute(claim);
        publish(claim.getClaimant(), NotificationType.CLAIM_CREATED,
                "Claim started",
                "Your claim for \"" + label(claim.getLostItem().getItemName())
                        + "\" is waiting for staff review.", route, null);
        publish(claim.getFinder(), NotificationType.CLAIM_CREATED,
                "New claim awaiting staff review",
                "Someone started a claim for your found report \"" + label(claim.getFoundItem().getItemName())
                        + "\". Staff will review the owner's saved private details.",
                route, null);
    }

    @Transactional
    public void claimApprovedForHandover(Claim claim) {
        String route = claimRoute(claim);
        publish(claim.getClaimant(), NotificationType.CLAIM_APPROVED_FOR_HANDOVER,
                "Claim approved for handover",
                "Staff approved your claim. Bring your item details to the in-person handover.",
                route, null);
        publish(claim.getFinder(), NotificationType.CLAIM_APPROVED_FOR_HANDOVER,
                "Claim approved for handover",
                "Staff approved a claim for your found report. Arrange the in-person handover with staff.",
                route, null);
    }

    @Transactional
    public void claimRejected(Claim claim) {
        String route = claimRoute(claim);
        publish(claim.getClaimant(), NotificationType.CLAIM_REJECTED,
                "Claim rejected",
                "Staff did not approve this claim after reviewing the saved private details.", route, null);
        publish(claim.getFinder(), NotificationType.CLAIM_REJECTED,
                "Claim not approved",
                "Staff did not approve the claim for your found report.", route, null);
    }

    @Transactional
    public void claimResolved(Claim claim) {
        String route = claimRoute(claim);
        publish(claim.getClaimant(), NotificationType.CLAIM_RESOLVED,
                "Handover confirmed",
                "The handover was confirmed. Both related reports are now resolved.", route, null);
        publish(claim.getFinder(), NotificationType.CLAIM_RESOLVED,
                "Handover confirmed",
                "The handover was confirmed. Both related reports are now resolved.", route, null);
    }

    @Transactional
    public void claimCancelled(Claim claim) {
        String route = claimRoute(claim);
        publish(claim.getClaimant(), NotificationType.CLAIM_CANCELLED,
                "Claim cancelled",
                "This claim was cancelled. Both reports remain active.", route, null);
        publish(claim.getFinder(), NotificationType.CLAIM_CANCELLED,
                "Claim cancelled",
                "This claim was cancelled. Both reports remain active.", route, null);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(User user, boolean unreadOnly, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        List<Notification> values = unreadOnly
                ? notifications.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(user.getId())
                : notifications.findByRecipientIdOrderByCreatedAtDesc(user.getId());
        return values.stream().limit(limit).map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(User user) {
        return new UnreadCountResponse(notifications.countByRecipientIdAndReadFalse(user.getId()));
    }

    @Transactional
    public NotificationResponse markRead(User user, Long notificationId) {
        Notification notification = notifications.findByIdAndRecipientId(notificationId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found."));
        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notifications.save(notification);
        }
        return response(notification);
    }

    @Transactional
    public UnreadCountResponse markAllRead(User user) {
        List<Notification> unread = notifications.findByRecipientIdAndReadFalseOrderByCreatedAtDesc(user.getId());
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(notification -> {
            notification.setRead(true);
            notification.setReadAt(now);
        });
        if (!unread.isEmpty()) notifications.saveAll(unread);
        return new UnreadCountResponse(0);
    }

    @Transactional
    public PreferencesResponse preferences(User user) {
        return preferencesResponse(preferencesFor(user));
    }

    @Transactional
    public PreferencesResponse updatePreferences(User user, UpdatePreferencesRequest update) {
        if (update == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification preferences are required.");
        UserNotificationPreferences value = preferencesFor(user);
        if (update.inAppEnabled() != null) value.setInAppEnabled(update.inAppEnabled());
        if (update.emailEnabled() != null) value.setEmailEnabled(update.emailEnabled());
        if (update.matchAlerts() != null) value.setMatchAlerts(update.matchAlerts());
        if (update.reportUpdates() != null) value.setReportUpdates(update.reportUpdates());
        if (update.claimUpdates() != null) value.setClaimUpdates(update.claimUpdates());
        return preferencesResponse(preferences.save(value));
    }

    private void publish(User recipient, NotificationType type, String title, String message, String actionUrl, String dedupeKey) {
        if (recipient == null || recipient.getId() == null) return;
        UserNotificationPreferences preference = preferencesFor(recipient);
        if (!allows(preference, type)) return;
        if (dedupeKey != null && notifications.existsByRecipientIdAndDedupeKey(recipient.getId(), dedupeKey)) return;

        Notification notification = new Notification(recipient, type, title, message, actionUrl, dedupeKey);
        if (preference.isInAppEnabled()) notifications.save(notification);
        if (preference.isEmailEnabled()) email.send(recipient, notification);
    }

    private UserNotificationPreferences preferencesFor(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("A persisted user is required for notification preferences.");
        }
        return preferences.findByUserId(user.getId())
                .orElseGet(() -> preferences.save(new UserNotificationPreferences(user)));
    }

    private boolean allows(UserNotificationPreferences preference, NotificationType type) {
        return switch (type.category()) {
            case MATCH -> preference.isMatchAlerts();
            case REPORT -> preference.isReportUpdates();
            case CLAIM -> preference.isClaimUpdates();
        };
    }

    private NotificationResponse response(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getCreatedAt(), notification.isRead(), notification.getReadAt(),
                notification.getActionUrl());
    }

    private PreferencesResponse preferencesResponse(UserNotificationPreferences value) {
        return new PreferencesResponse(value.isInAppEnabled(), value.isEmailEnabled(), value.isMatchAlerts(),
                value.isReportUpdates(), value.isClaimUpdates());
    }

    private String claimRoute(Claim claim) {
        return claim.getId() == null ? "claims" : "claim-" + claim.getId();
    }

    private String label(String itemName) {
        return itemName == null || itemName.isBlank() ? "your item" : itemName.trim();
    }

    public record NotificationResponse(Long id, NotificationType type, String title, String message,
                                       LocalDateTime createdAt, boolean read, LocalDateTime readAt, String actionUrl) {
    }

    public record UnreadCountResponse(long unreadCount) {
    }

    /** Nullable fields allow clients to update just the settings that changed. */
    public record UpdatePreferencesRequest(Boolean inAppEnabled, Boolean emailEnabled, Boolean matchAlerts,
                                           Boolean reportUpdates, Boolean claimUpdates) {
    }

    public record PreferencesResponse(boolean inAppEnabled, boolean emailEnabled, boolean matchAlerts,
                                      boolean reportUpdates, boolean claimUpdates) {
    }
}
