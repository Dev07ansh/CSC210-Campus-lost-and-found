package com.csc210.backend.service;

import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.model.Claim;
import com.csc210.backend.model.ClaimStatus;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.model.LostItemSecret;
import com.csc210.backend.model.User;
import com.csc210.backend.repository.ClaimRepository;
import com.csc210.backend.repository.FoundItemRepository;
import com.csc210.backend.repository.LostItemRepository;
import com.csc210.backend.repository.LostItemSecretRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * A match only suggests a candidate. The owner files one claim using their
 * already-saved private details; an independent staff member reviews those
 * details in person and controls the handover outcome.
 */
@Service
public class ClaimService {
    private static final EnumSet<ClaimStatus> ACTIVE_CLAIM_STATES = EnumSet.of(
            ClaimStatus.PENDING_STAFF_REVIEW, ClaimStatus.APPROVED_FOR_HANDOVER,
            ClaimStatus.PENDING_VERIFICATION, ClaimStatus.VERIFIED);
    private static final EnumSet<ClaimStatus> REVIEW_QUEUE_STATES = EnumSet.of(
            ClaimStatus.PENDING_STAFF_REVIEW, ClaimStatus.APPROVED_FOR_HANDOVER,
            ClaimStatus.PENDING_VERIFICATION, ClaimStatus.VERIFIED);

    private final ClaimRepository claims;
    private final LostItemRepository lostItems;
    private final FoundItemRepository foundItems;
    private final LostItemSecretRepository secrets;
    private final SimilarityScorer similarityScorer;
    private final CurrentUserService currentUsers;
    private final StaffAuthorizationService staffAccess;
    private final NotificationService notifications;

    public ClaimService(ClaimRepository claims, LostItemRepository lostItems,
                        FoundItemRepository foundItems, LostItemSecretRepository secrets,
                        SimilarityScorer similarityScorer, CurrentUserService currentUsers,
                        StaffAuthorizationService staffAccess, NotificationService notifications) {
        this.claims = claims;
        this.lostItems = lostItems;
        this.foundItems = foundItems;
        this.secrets = secrets;
        this.similarityScorer = similarityScorer;
        this.currentUsers = currentUsers;
        this.staffAccess = staffAccess;
        this.notifications = notifications;
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
        LostItemSecret secret = secretFor(lost);
        if (secret == null || !secret.isComplete()) {
            throw conflict("The lost report needs all three private details before a claim can begin.");
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
        claim.setStatus(ClaimStatus.PENDING_STAFF_REVIEW);
        claim.setMatchScore(similarityScorer.score(lost, found));
        claim.setCreatedAt(LocalDateTime.now());
        Claim saved = claims.save(claim);
        Claim persisted = saved == null ? claim : saved;
        notifications.claimCreated(persisted);
        return toResponse(persisted, currentUser);
    }

    /** Claimants and finders see status and public report summaries, never private details. */
    @Transactional(readOnly = true)
    public List<ClaimResponse> mine(User currentUser) {
        if (currentUser.getId() == null) return List.of();
        return claims.findByClaimantIdOrFinderIdOrderByCreatedAtDesc(currentUser.getId(), currentUser.getId()).stream()
                .map(claim -> toResponse(claim, currentUser)).toList();
    }

    /**
     * This is deliberately the only API path that returns the owner's saved
     * private details. The caller must be configured staff and independent of
     * the claim, so a finder cannot use staff access to inspect an owner's data.
     */
    @Transactional(readOnly = true)
    public List<StaffReviewClaimResponse> reviewQueue(User currentUser) {
        requireIndependentStaff(currentUser, null);
        return claims.findByStatusInOrderByCreatedAtDesc(REVIEW_QUEUE_STATES).stream()
                .filter(claim -> !isParticipant(claim, currentUser))
                .map(claim -> toStaffReviewResponse(claim, currentUser))
                .toList();
    }

    @Transactional
    public StaffReviewClaimResponse review(Long claimId, User currentUser, ReviewRequest request) {
        if (request == null || request.decision() == null || request.decision().isBlank()) {
            throw badRequest("A staff review decision is required.");
        }
        Claim claim = claim(claimId);
        requireIndependentStaff(currentUser, claim);
        if (!isPendingStaffReview(claim.getStatus())) {
            throw conflict("Only a claim awaiting staff review can be reviewed.");
        }

        ReviewDecision decision = ReviewDecision.parse(request.decision());
        if (decision == ReviewDecision.APPROVE) {
            LostItemSecret secret = secretFor(claim.getLostItem());
            if (secret == null || !secret.isComplete()) {
                throw conflict("This claim cannot be approved because its saved private details are incomplete.");
            }
            claim.setStatus(ClaimStatus.APPROVED_FOR_HANDOVER);
        } else {
            claim.setStatus(ClaimStatus.REJECTED);
        }
        claim.setReviewedBy(currentUser);
        claim.setReviewedAt(LocalDateTime.now());
        claim.setStaffReviewNote(normalizeNote(request.staffNote()));
        claims.save(claim);
        if (decision == ReviewDecision.APPROVE) notifications.claimApprovedForHandover(claim);
        else notifications.claimRejected(claim);
        return toStaffReviewResponse(claim, currentUser);
    }

    /** Staff confirms the physical handover; this resolves both related reports atomically. */
    @Transactional
    public StaffReviewClaimResponse completeHandover(Long claimId, User currentUser) {
        Claim claim = claim(claimId);
        requireIndependentStaff(currentUser, claim);
        if (!isApprovedForHandover(claim.getStatus())) {
            throw conflict("Only a staff-approved claim can be completed.");
        }
        claim.setStatus(ClaimStatus.RESOLVED);
        LocalDateTime completedAt = LocalDateTime.now();
        claim.setCompletedBy(currentUser);
        claim.setCompletedAt(completedAt);
        claim.setResolvedAt(completedAt);
        claim.setHandoverCodeHash(null);
        claim.setHandoverCodeExpiresAt(null);
        claim.getLostItem().setStatus("Resolved");
        claim.getFoundItem().setStatus("Resolved");
        lostItems.save(claim.getLostItem());
        foundItems.save(claim.getFoundItem());
        claims.save(claim);
        notifications.claimResolved(claim);
        return toStaffReviewResponse(claim, currentUser);
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
        notifications.claimCancelled(claim);
        return toResponse(claim, currentUser);
    }

    public StaffAccessResponse staffAccess(User currentUser) {
        return new StaffAccessResponse(staffAccess.isStaff(currentUser));
    }

    private Claim claim(Long claimId) {
        if (claimId == null) throw badRequest("A claim id is required.");
        return claims.findById(claimId).orElseThrow(() -> notFound("Claim not found."));
    }

    private void requireIndependentStaff(User currentUser, Claim claim) {
        staffAccess.requireStaff(currentUser);
        if (claim != null && isParticipant(claim, currentUser)) {
            throw forbidden("A claim participant cannot review their own claim.");
        }
    }

    private void requireParticipant(Claim claim, User currentUser) {
        if (!isParticipant(claim, currentUser)) {
            throw forbidden("Only a claim participant can perform this action.");
        }
    }

    private boolean isParticipant(Claim claim, User currentUser) {
        return currentUsers.same(currentUser, claim.getClaimant()) || currentUsers.same(currentUser, claim.getFinder());
    }

    private ClaimResponse toResponse(Claim claim, User currentUser) {
        boolean claimant = currentUsers.same(currentUser, claim.getClaimant());
        boolean finder = currentUsers.same(currentUser, claim.getFinder());
        boolean isStaff = staffAccess.isStaff(currentUser);
        boolean independentStaff = isStaff && !claimant && !finder;
        String role = claimant ? "CLAIMANT" : finder ? "FINDER" : isStaff ? "STAFF" : "";
        return new ClaimResponse(
                claim.getId(),
                report(claim.getLostItem()),
                report(claim.getFoundItem()),
                normalizeLegacyStatus(claim.getStatus()),
                claim.getMatchScore(),
                claim.getCreatedAt(),
                claim.getReviewedAt(),
                claim.getResolvedAt(),
                role,
                (claimant || finder) && ACTIVE_CLAIM_STATES.contains(claim.getStatus()),
                independentStaff && isPendingStaffReview(claim.getStatus()),
                independentStaff && isApprovedForHandover(claim.getStatus()),
                isStaff
        );
    }

    private StaffReviewClaimResponse toStaffReviewResponse(Claim claim, User currentUser) {
        requireIndependentStaff(currentUser, claim);
        LostItemSecret secret = secretFor(claim.getLostItem());
        return new StaffReviewClaimResponse(
                claim.getId(),
                staffReport(claim.getLostItem()),
                staffReport(claim.getFoundItem()),
                participant(claim.getClaimant()),
                participant(claim.getFinder()),
                normalizeLegacyStatus(claim.getStatus()),
                claim.getMatchScore(),
                claim.getCreatedAt(),
                claim.getReviewedAt(),
                claim.getCompletedAt(),
                claim.getStaffReviewNote(),
                privateDetails(secret),
                isPendingStaffReview(claim.getStatus()),
                isApprovedForHandover(claim.getStatus())
        );
    }

    private ReportSummary report(LostItem item) {
        return new ReportSummary(item.getId(), item.getItemName(), item.getCategory(), item.getColor(), item.getLocation(), item.getImageUrl(), item.getStatus());
    }

    private ReportSummary report(FoundItem item) {
        return new ReportSummary(item.getId(), item.getItemName(), item.getCategory(), item.getColor(), item.getLocation(), item.getImageUrl(), item.getStatus());
    }

    /** Extra report context is intentionally available only in the staff review DTO. */
    private StaffReportSummary staffReport(LostItem item) {
        return new StaffReportSummary(item.getId(), item.getItemName(), item.getCategory(), item.getColor(),
                item.getDescription(), item.getLocation(), item.getImageUrl(), item.getDateTime(), item.getStatus());
    }

    /** Extra report context is intentionally available only in the staff review DTO. */
    private StaffReportSummary staffReport(FoundItem item) {
        return new StaffReportSummary(item.getId(), item.getItemName(), item.getCategory(), item.getColor(),
                item.getDescription(), item.getLocation(), item.getImageUrl(), item.getDateTime(), item.getStatus());
    }

    private ParticipantSummary participant(User user) {
        return new ParticipantSummary(user == null ? null : user.getName(), user == null ? null : user.getEmail());
    }

    private PrivateDetails privateDetails(LostItemSecret secret) {
        return new PrivateDetails(
                secret == null ? null : secret.getInteriorColorOrLining(),
                secret == null ? null : secret.getUniqueMarks(),
                secret == null ? null : secret.getContents());
    }

    private LostItemSecret secretFor(LostItem item) {
        return item == null || item.getId() == null ? null : secrets.findByLostItemId(item.getId()).orElse(null);
    }

    private boolean isActive(String status) {
        return status != null && "active".equalsIgnoreCase(status);
    }

    private boolean isPendingStaffReview(ClaimStatus status) {
        return status == ClaimStatus.PENDING_STAFF_REVIEW || status == ClaimStatus.PENDING_VERIFICATION;
    }

    private boolean isApprovedForHandover(ClaimStatus status) {
        return status == ClaimStatus.APPROVED_FOR_HANDOVER || status == ClaimStatus.VERIFIED;
    }

    private ClaimStatus normalizeLegacyStatus(ClaimStatus status) {
        if (status == ClaimStatus.PENDING_VERIFICATION) return ClaimStatus.PENDING_STAFF_REVIEW;
        if (status == ClaimStatus.VERIFIED) return ClaimStatus.APPROVED_FOR_HANDOVER;
        return status;
    }

    private String normalizeNote(String value) {
        if (value == null || value.isBlank()) return null;
        String note = value.trim();
        if (note.length() > 2000) throw badRequest("A staff review note cannot exceed 2000 characters.");
        return note;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record CreateClaimRequest(Long lostItemId, Long foundItemId) {}
    public record ReviewRequest(String decision, String staffNote) {}
    public record ReportSummary(Long id, String itemName, String category, String color, String location, String imageUrl, String status) {}
    /** Includes report descriptions/timestamps only for the staff review workspace. */
    public record StaffReportSummary(Long id, String itemName, String category, String color, String description,
                                     String location, String imageUrl, LocalDateTime dateTime, String status) {}
    public record ParticipantSummary(String name, String email) {}
    /** Returned only from the staff-only review queue and review actions. */
    public record PrivateDetails(String interiorColorOrLining, String uniqueMarks, String contents) {}
    /** General claim response: deliberately contains no private owner details or staff notes. */
    public record ClaimResponse(Long id, ReportSummary lostItem, ReportSummary foundItem, ClaimStatus status,
                                double matchScore, LocalDateTime createdAt, LocalDateTime reviewedAt, LocalDateTime resolvedAt,
                                String role, boolean canCancel, boolean canReview, boolean canCompleteHandover, boolean isStaff) {}
    public record StaffReviewClaimResponse(Long id, StaffReportSummary lostItem, StaffReportSummary foundItem,
                                           ParticipantSummary claimant, ParticipantSummary finder, ClaimStatus status,
                                           double matchScore, LocalDateTime createdAt, LocalDateTime reviewedAt,
                                           LocalDateTime completedAt, String staffNote, PrivateDetails privateDetails,
                                           boolean canReview, boolean canCompleteHandover) {}
    public record StaffAccessResponse(boolean staff) {}

    private enum ReviewDecision {
        APPROVE, REJECT;

        static ReviewDecision parse(String input) {
            try {
                return valueOf(input.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Review decision must be APPROVE or REJECT.");
            }
        }
    }
}
