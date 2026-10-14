package com.csc210.backend.service;

import com.csc210.backend.model.User;
import com.csc210.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/**
 * Temporary identity bridge for the existing browser app.  Controllers never accept a
 * claimant/finder identity in their JSON body; they resolve it only from X-User-Email.
 */
@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) { this.users = users; }

    public User requireUser(String suppliedEmail) {
        String email = normalize(suppliedEmail);
        return users.findByEmailIgnoreCase(email).orElseGet(() -> {
            String localPart = email.substring(0, email.indexOf('@'));
            return users.save(new User(localPart, email));
        });
    }

    public boolean same(User first, User second) {
        if (first == null || second == null) return false;
        if (first.getId() != null && second.getId() != null) return first.getId().equals(second.getId());
        return first.getEmail() != null && first.getEmail().equalsIgnoreCase(second.getEmail());
    }

    private String normalize(String suppliedEmail) {
        if (suppliedEmail == null || suppliedEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in before using this action.");
        }
        String email = suppliedEmail.trim().toLowerCase(Locale.ROOT);
        if (!email.contains("@") || email.startsWith("@") || email.endsWith("@")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid signed-in email is required.");
        }
        return email;
    }
}
