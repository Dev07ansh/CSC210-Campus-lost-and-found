package com.csc210.backend.model;

import jakarta.persistence.*;

/** Per-user delivery choices. Email remains opt-in both here and in server configuration. */
@Entity
@Table(name = "user_notification_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "user_notification_preferences_user_unique", columnNames = "user_id")
})
public class UserNotificationPreferences {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "match_alerts", nullable = false)
    private boolean matchAlerts = true;

    @Column(name = "report_updates", nullable = false)
    private boolean reportUpdates = true;

    @Column(name = "claim_updates", nullable = false)
    private boolean claimUpdates = true;

    public UserNotificationPreferences() {
    }

    public UserNotificationPreferences(User user) {
        this.user = user;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User value) { user = value; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(boolean value) { inAppEnabled = value; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public void setEmailEnabled(boolean value) { emailEnabled = value; }
    public boolean isMatchAlerts() { return matchAlerts; }
    public void setMatchAlerts(boolean value) { matchAlerts = value; }
    public boolean isReportUpdates() { return reportUpdates; }
    public void setReportUpdates(boolean value) { reportUpdates = value; }
    public boolean isClaimUpdates() { return claimUpdates; }
    public void setClaimUpdates(boolean value) { claimUpdates = value; }
}
