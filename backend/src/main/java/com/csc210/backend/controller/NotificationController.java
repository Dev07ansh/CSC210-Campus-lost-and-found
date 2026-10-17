package com.csc210.backend.controller;

import com.csc210.backend.model.User;
import com.csc210.backend.service.CurrentUserService;
import com.csc210.backend.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    private final CurrentUserService currentUsers;

    public NotificationController(NotificationService notifications, CurrentUserService currentUsers) {
        this.notifications = notifications;
        this.currentUsers = currentUsers;
    }

    @GetMapping
    public List<NotificationService.NotificationResponse> list(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "50") int limit) {
        return notifications.list(currentUser(userEmail), unreadOnly, limit);
    }

    @GetMapping("/unread-count")
    public NotificationService.UnreadCountResponse unreadCount(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return notifications.unreadCount(currentUser(userEmail));
    }

    @PostMapping("/{id}/read")
    public NotificationService.NotificationResponse markRead(@PathVariable Long id,
                                                              @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return notifications.markRead(currentUser(userEmail), id);
    }

    @PostMapping("/read-all")
    public NotificationService.UnreadCountResponse markAllRead(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return notifications.markAllRead(currentUser(userEmail));
    }

    @GetMapping("/preferences")
    public NotificationService.PreferencesResponse preferences(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return notifications.preferences(currentUser(userEmail));
    }

    @PutMapping("/preferences")
    public NotificationService.PreferencesResponse updatePreferences(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestBody NotificationService.UpdatePreferencesRequest request) {
        return notifications.updatePreferences(currentUser(userEmail), request);
    }

    private User currentUser(String email) {
        return currentUsers.requireUser(email);
    }
}
