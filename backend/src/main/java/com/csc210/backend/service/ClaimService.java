package com.csc210.backend.service;

import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.model.*;
import com.csc210.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/** Keeps candidate matching and proof-of-ownership verification as two separate operations. */
@Service
public class ClaimService {
    public static final double VERIFICATION_THRESHOLD = 0.60;
    public static final int MAX_VERIFICATION_ATTEMPTS = 3;
    private static final EnumSet<ClaimStatus> ACTIVE_CLAIM_STATES = EnumSet.of(ClaimStatus.PENDING_VERIFICATION, ClaimStatus.VERIFIED);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClaimRepository claims;
    private final ClaimVerificationAttemptRepository verificationAttempts;
    private final LostItemRepository lostItems;
    private final FoundItemRepository foundItems;
    private final LostItemSecretRepository secrets;
    private final SimilarityScorer similarityScorer;
    private final CurrentUserService currentUsers;

    public ClaimService(ClaimRepository claims, ClaimVerificationAttemptRepository verificationAttempts,
                        LostItemRepository lostItems, FoundItemRepository foundItems,
                        LostItemSecretRepository secrets, SimilarityScorer similarityScorer,
                        CurrentUserService currentUsers) {
        this.claims = claims;
        this.verificationAttempts = verificationAttempts;
        this.lostItems = lostItems;
        this.foundItems = foundItems;
        this.secrets = secrets;
        this.similarityScorer = similarityScorer;
        this.currentUsers = currentUsers;
    }

    @Transactional
    public ClaimResponse create(User currentUser, CreateClaimRequest request) {
        if (request == null || request.lostItemId() == null || request.foundItemId() == null) {
            throw badRequest("A lost report and a found report are required.");
        }
        LostItem lost = lostItems.findById(request.lostItemId()).orElseThrow(() -> notFound("Lost report not found."));
        FoundItem found = foundItems.findById(request.foundItemId()).orElseThrow(() -> notFound("Found report not found."));
        if (lost.getReporter() == null || found.getReporter() == null) {
            throw conflict("Legacy reports without a reporter cannot be claimed.");
        }
        if (!currentUsers.same(currentUser, lost.getReporter())) {
            throw forbidden("Only the owner of the lost report can claim a candidate.");
        }
        if (currentUsers.same(currentUser, found.getReporter())) {
            throw badRequest("You cannot claim your own found-item report.");
        }
        if (!isActive(lost.getStatus()) || !isActive(found.getStatus())) {
            throw conflict("Both reports must still be active before a claim can begin.");
        }
        LostItemSecret secret = secrets.findByLostItemId(lost.getId()).orElse(null);
        if (secret == null || !secret.isComplete()) {
            throw conflict("The lost report needs all three private verification details before a claim can begin.");
        }
        if (claims.existsByLostItemIdAndStatusIn(lost.getId(), ACTIVE_CLAIM_STATES)
                || claims.existsByFoundItemIdAndStatusIn(found.getId(), ACTIVE_CLAIM_STATES)) {
            throw conflict("One of these reports already has an active claim. Cancel or finish it first.");
        }

        Claim claim = new Claim();
        claim.setLostItem(lost);
        claim.setFoundItem(found);
        claim.setClaimant(lost.getReporter());
        claim.setFinder(found.getReporter());
        claim.setStatus(ClaimStatus.PENDING_VERIFICATION);
        claim.setMatchScore(similarityScorer.score(lost, found));
        claim.setCreatedAt(LocalDateTime.now());
        Claim saved = claims.save(claim);
        return toResponse(saved == null ? claim : saved, currentUser);
    }

    @Transactional(readOnly = true)
    public List<ClaimResponse> mine(User currentUser) {
        if (currentUser.getId() == null) return List.of();
        return claims.findByClaimantIdOrFinderIdOrderByCreatedAtDesc(currentUser.getId(), currentUser.getId()).stream()
                .map(claim -> toResponse(claim, currentUser)).toList();
    }

    @Transactional
    public VerificationResponse verify(Long claimId, User currentUser, VerificationRequest request) {
        if (request == null) throw badRequest("Verification answers are required.");
        Claim claim = claim(claimId);
        requireClaimant(claim, currentUser);
        requireStatus(claim, ClaimStatus.PENDING_VERIFICATION, "This claim is not awaiting verification.");

        LostItemSecret secret = secrets.findByLostItemId(claim.getLostItem().getId())
                .orElseThrow(() -> badRequest("This lost report has no private verification details."));
        if (!secret.isComplete()) throw badRequest("This lost report has incomplete private verification details.");

        double interiorScore = jaccard(secret.getInteriorColorOrLining(), request.interiorColorOrLining());
        double marksScore = jaccard(secret.getUniqueMarks(), request.uniqueMarks());
        double contentsScore = jaccard(secret.getContents(), request.contents());
        double decisionScore = Math.min(interiorScore, Math.min(marksScore, contentsScore));
        boolean passed = interiorScore >= VERIFICATION_THRESHOLD
                && marksScore >= VERIFICATION_THRESHOLD
                && contentsScore >= VERIFICATION_THRESHOLD;

        int attemptNumber = verificationAttempts.countByClaimId(claim.getId()) + 1;
        verificationAttempts.save(new ClaimVerificationAttempt(claim, attemptNumber, decisionScore, passed, LocalDateTime.now()));

        String handoverCode = null;
        boolean rejected = false;
        if (passed) {
            claim.setStatus(ClaimStatus.VERIFIED);
            claim.setVerifiedAt(LocalDateTime.now());
            handoverCode = newSixDigitCode();
            claim.setHandoverCodeHash(hash(handoverCode));
            claim.setHandoverCodeExpiresAt(LocalDateTime.now().plusMinutes(30));
        } else if (attemptNumber >= MAX_VERIFICATION_ATTEMPTS) {
            claim.setStatus(ClaimStatus.REJECTED);
            rejected = true;
        }
        claims.save(claim);
        int remaining = passed || rejected ? 0 : Math.max(0, MAX_VERIFICATION_ATTEMPTS - attemptNumber);
        return new VerificationResponse(toResponse(claim, currentUser), passed, rejected, remaining, handoverCode);
    }

    @Transactional
    public ClaimResponse resolve(Long claimId, User currentUser, ResolveRequest request) {
        if (request == null || request.handoverCode() == null || request.handoverCode().isBlank()) {
            throw badRequest("The finder must enter the one-time handover code.");
        }
        Claim claim = claim(claimId);
        requireFinder(claim, currentUser);
        requireStatus(claim, ClaimStatus.VERIFIED, "Only a verified claim can be resolved.");
        if (claim.getHandoverCodeExpiresAt() == null || LocalDateTime.now().isAfter(claim.getHandoverCodeExpiresAt())) {
            throw conflict("The handover code has expired.");
        }
        if (claim.getHandoverCodeHash() == null || !MessageDigest.isEqual(
                hash(request.handoverCode().trim()).getBytes(StandardCharsets.UTF_8),
                claim.getHandoverCodeHash().getBytes(StandardCharsets.UTF_8))) {
            throw badRequest("The handover code is incorrect.");
        }
        claim.setStatus(ClaimStatus.RESOLVED);
        claim.setResolvedAt(LocalDateTime.now());
        claim.setHandoverCodeHash(null);
        claim.setHandoverCodeExpiresAt(null);
        claim.getLostItem().setStatus("Resolved");
        claim.getFoundItem().setStatus("Resolved");
        lostItems.save(claim.getLostItem());
        foundItems.save(claim.getFoundItem());
        claims.save(claim);
        return toResponse(claim, currentUser);
    }

    @Transactional
    public ClaimResponse cancel(Long claimId, User currentUser) {
        Claim claim = claim(claimId);
        requireParticipant(claim, currentUser);
        if (!ACTIVE_CLAIM_STATES.contains(claim.getStatus())) {
            throw conflict("Only an active claim can be cancelled.");
        }
        claim.setStatus(ClaimStatus.CANCELLED);
        claim.setCancelledAt(LocalDateTime.now());
        claim.setHandoverCodeHash(null);
        claim.setHandoverCodeExpiresAt(null);
        claims.save(claim);
        return toResponse(claim, currentUser);
    }

    private Claim claim(Long claimId) {
        if (claimId == null) throw badRequest("A claim id is required.");
        return claims.findById(claimId).orElseThrow(() -> notFound("Claim not found."));
    }

    private void requireClaimant(Claim claim, User currentUser) {
        if (!currentUsers.same(currentUser, claim.getClaimant())) throw forbidden("Only the claimant can verify ownership.");
    }

    private void requireFinder(Claim claim, User currentUser) {
        if (!currentUsers.same(currentUser, claim.getFinder())) throw forbidden("Only the finder can confirm the handover code.");
    }

    private void requireParticipant(Claim claim, User currentUser) {
        if (!currentUsers.same(currentUser, claim.getClaimant()) && !currentUsers.same(currentUser, claim.getFinder())) {
            throw forbidden("Only a claim participant can perform this action.");
        }
    }

    private void requireStatus(Claim claim, ClaimStatus status, String message) {
        if (claim.getStatus() != status) throw conflict(message);
    }

    private ClaimResponse toResponse(Claim claim, User currentUser) {
        int attempts = claim.getId() == null ? 0 : verificationAttempts.countByClaimId(claim.getId());
        boolean claimant = currentUsers.same(currentUser, claim.getClaimant());
        boolean finder = currentUsers.same(currentUser, claim.getFinder());
        return new ClaimResponse(
                claim.getId(),
                report(claim.getLostItem()),
                report(claim.getFoundItem()),
                claim.getStatus(),
                claim.getMatchScore(),
                claim.getCreatedAt(),
                claim.getVerifiedAt(),
                claim.getResolvedAt(),
                claimant ? "CLAIMANT" : finder ? "FINDER" : "",
                Math.max(0, MAX_VERIFICATION_ATTEMPTS - attempts),
                claimant && claim.getStatus() == ClaimStatus.PENDING_VERIFICATION,
                finder && claim.getStatus() == ClaimStatus.VERIFIED,
                (claimant || finder) && ACTIVE_CLAIM_STATES.contains(claim.getStatus())
        );
    }

    private ReportSummary report(LostItem item) {
        return new ReportSummary(item.getId(), item.getItemName(), item.getCategory(), item.getColor(), item.getLocation(), item.getImageUrl(), item.getStatus());
    }

    private ReportSummary report(FoundItem item) {
        return new ReportSummary(item.getId(), item.getItemName(), item.getCategory(), item.getColor(), item.getLocation(), item.getImageUrl(), item.getStatus());
    }

    private boolean isActive(String status) { return status != null && "active".equalsIgnoreCase(status); }

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
        return new HashSet<>(Arrays.stream(value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}\\s]", " ").split("\\s+"))
                .filter(word -> !word.isBlank()).toList());
    }

    private String newSixDigitCode() { return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000)); }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException forbidden(String message) { return new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public record CreateClaimRequest(Long lostItemId, Long foundItemId) {}
    public record VerificationRequest(String interiorColorOrLining, String uniqueMarks, String contents) {}
    public record ResolveRequest(String handoverCode) {}
    public record ReportSummary(Long id, String itemName, String category, String color, String location, String imageUrl, String status) {}
    public record ClaimResponse(Long id, ReportSummary lostItem, ReportSummary foundItem, ClaimStatus status,
                                double matchScore, LocalDateTime createdAt, LocalDateTime verifiedAt, LocalDateTime resolvedAt,
                                String role, int verificationAttemptsRemaining, boolean canVerify, boolean canResolve, boolean canCancel) {}
    /** The plaintext handover code is populated only on the successful verification response. */
    public record VerificationResponse(ClaimResponse claim, boolean passed, boolean rejected, int attemptsRemaining, String handoverCode) {}
}
