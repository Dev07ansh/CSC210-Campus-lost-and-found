package com.csc210.backend.service;

import com.csc210.backend.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Small, explicit staff allow-list for this demo's X-User-Email identity bridge.
 * Production deployments should replace the bridge with authenticated roles.
 */
@Service
public class StaffAuthorizationService {
    private final Set<String> staffEmails;

    public StaffAuthorizationService(@Value("${claims.staff-emails:}") String configuredEmails) {
        staffEmails = Arrays.stream(configuredEmails == null ? new String[0] : configuredEmails.split("[,;\\s]+"))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isStaff(User user) {
        return user != null && user.getEmail() != null
                && staffEmails.contains(user.getEmail().trim().toLowerCase(Locale.ROOT));
    }

    public void requireStaff(User user) {
        if (!isStaff(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only a configured staff member can review or complete a claim.");
        }
    }
}
