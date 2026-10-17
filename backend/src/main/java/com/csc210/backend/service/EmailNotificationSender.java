package com.csc210.backend.service;

import com.csc210.backend.model.Notification;
import com.csc210.backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sends mail only when the server administrator explicitly enables and configures it.
 * A missing SMTP configuration never prevents an in-app notification from being saved.
 */
@Service
public class EmailNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final boolean enabled;
    private final String from;
    private final String host;
    private final AtomicBoolean warnedAboutMissingConfiguration = new AtomicBoolean(false);

    public EmailNotificationSender(ObjectProvider<JavaMailSender> mailSenders,
                                   @Value("${notifications.email.enabled:false}") boolean enabled,
                                   @Value("${notifications.email.from:}") String from,
                                   @Value("${spring.mail.host:}") String host) {
        this.mailSenders = mailSenders;
        this.enabled = enabled;
        this.from = from;
        this.host = host;
    }

    public void send(User recipient, Notification notification) {
        if (!enabled) return;

        JavaMailSender sender = mailSenders.getIfAvailable();
        if (!hasText(host) || !hasText(from) || sender == null) {
            if (warnedAboutMissingConfiguration.compareAndSet(false, true)) {
                log.warn("Email notifications are enabled but SMTP host/from details are incomplete; in-app notifications will continue normally.");
            }
            return;
        }
        if (recipient == null || !hasText(recipient.getEmail())) return;

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient.getEmail());
            message.setFrom(from.trim());
            message.setSubject("Lost & Found: " + notification.getTitle());
            message.setText(notification.getMessage() + "\n\nOpen Lost & Found to view this update.");
            sender.send(message);
        } catch (RuntimeException exception) {
            // Do not expose SMTP exceptions to API callers or make a report/claim fail.
            log.warn("Could not deliver a notification email for user id {}: {}", recipient.getId(), exception.getMessage());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
