package com.csc210.backend.service;

import com.csc210.backend.model.LostItem;
import com.csc210.backend.model.VerificationAttempt;
import com.csc210.backend.repository.LostItemRepository;
import com.csc210.backend.repository.VerificationAttemptRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class OwnershipVerificationService {
    public static final double PASS_THRESHOLD = 0.60;
    private final LostItemRepository lostItems;
    private final VerificationAttemptRepository attempts;

    public OwnershipVerificationService(LostItemRepository lostItems, VerificationAttemptRepository attempts) {
        this.lostItems = lostItems;
        this.attempts = attempts;
    }

    @Transactional
    public synchronized VerificationResult verify(Long lostItemId, VerificationRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification answers are required.");
        LostItem lost = lostItems.findById(lostItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lost report not found."));
        if (!lost.isVerificationAvailable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This older report has no private verification details.");
        }
        String claimerEmail = normalizedEmail(request.claimerEmail());
        VerificationAttempt attempt = attempts.findByClaimerEmail(claimerEmail)
                .orElseGet(() -> new VerificationAttempt(lostItemId, claimerEmail));
        if (attempt.isSuspended()) return new VerificationResult(false, true, attempt.getFailedAttempts());

        double interiorScore = jaccard(lost.getInteriorColorOrLining(), request.interiorColorOrLining());
        double marksScore = jaccard(lost.getUniqueMarks(), request.uniqueMarks());
        double contentsScore = jaccard(lost.getContents(), request.contents());
        boolean passed = interiorScore >= PASS_THRESHOLD && marksScore >= PASS_THRESHOLD && contentsScore >= PASS_THRESHOLD;

        if (!passed) {
            attempt.setFailedAttempts(attempt.getFailedAttempts() + 1);
            attempt.setSuspended(attempt.getFailedAttempts() >= 3);
            attempts.save(attempt);
        }
        return new VerificationResult(passed, attempt.isSuspended(), attempt.getFailedAttempts());
    }

    private String normalizedEmail(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please sign in before verifying ownership.");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private double jaccard(String expected, String answer) {
        Set<String> expectedWords = words(expected);
        Set<String> answerWords = words(answer);
        if (expectedWords.isEmpty() || answerWords.isEmpty()) return 0;
        Set<String> shared = new HashSet<>(expectedWords);
        shared.retainAll(answerWords);
        Set<String> combined = new HashSet<>(expectedWords);
        combined.addAll(answerWords);
        return (double) shared.size() / combined.size();
    }

    private Set<String> words(String value) {
        if (value == null) return Set.of();
        return new HashSet<>(Arrays.stream(value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s]", " ").split("\\s+"))
                .filter(word -> !word.isBlank()).toList());
    }

    public record VerificationRequest(String claimerEmail, String interiorColorOrLining, String uniqueMarks, String contents) {}
    /** Deliberately omits per-answer scores so private report details cannot be inferred from API responses. */
    public record VerificationResult(boolean passed, boolean suspended, int failedAttempts) {}
}
