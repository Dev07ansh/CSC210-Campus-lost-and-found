package com.csc210.backend;

import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.model.Claim;
import com.csc210.backend.model.ClaimStatus;
import com.csc210.backend.model.CollectionDesk;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.model.LostItemSecret;
import com.csc210.backend.model.User;
import com.csc210.backend.repository.ClaimRepository;
import com.csc210.backend.repository.FoundItemRepository;
import com.csc210.backend.repository.LostItemRepository;
import com.csc210.backend.repository.LostItemSecretRepository;
import com.csc210.backend.repository.UserRepository;
import com.csc210.backend.service.ClaimService;
import com.csc210.backend.service.CurrentUserService;
import com.csc210.backend.service.NotificationService;
import com.csc210.backend.service.StaffAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {
    private static final long LOST_ID = 41L;
    private static final long FOUND_ID = 52L;
    private static final long CLAIM_ID = 63L;

    @Mock private ClaimRepository claims;
    @Mock private LostItemRepository lostItems;
    @Mock private FoundItemRepository foundItems;
    @Mock private LostItemSecretRepository secrets;
    @Mock private UserRepository users;
    @Mock private NotificationService notifications;
    private ClaimService service;
    private User claimant;
    private User finder;
    private User staff;
    private LostItem lost;
    private FoundItem found;
    private LostItemSecret secret;

    @BeforeEach
    void setUp() {
        claimant = user(1L, "claimant@example.edu");
        finder = user(2L, "finder@example.edu");
        staff = user(3L, "staff@example.edu");
        lost = new LostItem();
        setId(lost, LOST_ID);
        lost.setItemName("Black wallet");
        lost.setCategory("Wallet");
        lost.setColor("Black");
        lost.setLocation("Library");
        lost.setDescription("Leather wallet");
        lost.setStatus("Active");
        lost.setReporter(claimant);
        found = new FoundItem();
        setId(found, FOUND_ID);
        found.setItemName("Black wallet");
        found.setCategory("Wallet");
        found.setColor("Black");
        found.setLocation("Library");
        found.setDescription("Leather wallet");
        found.setStatus("Active");
        found.setCollectionDesk(CollectionDesk.UNIVERSITY_CENTRE_RECEPTION.label());
        found.setReporter(finder);
        secret = new LostItemSecret("red lining", "scratch lower corner", "college id and cash");
        secret.setLostItem(lost);
        service = new ClaimService(claims, lostItems, foundItems, secrets,
                new SimilarityScorer(), new CurrentUserService(users),
                new StaffAuthorizationService("staff@example.edu"), notifications);
        lenient().when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOnlyForLostReportOwnerAndQueuesStaffReview() {
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        when(foundItems.findById(FOUND_ID)).thenReturn(Optional.of(found));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.of(secret));
        when(claims.existsByLostItemIdAndStatusIn(eq(LOST_ID), any())).thenReturn(false);
        when(claims.existsByFoundItemIdAndStatusIn(eq(FOUND_ID), any())).thenReturn(false);

        var response = service.create(claimant, new ClaimService.CreateClaimRequest(LOST_ID, FOUND_ID));

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claims).save(saved.capture());
        assertEquals(ClaimStatus.PENDING_STAFF_REVIEW, saved.getValue().getStatus());
        assertSame(claimant, saved.getValue().getClaimant());
        assertSame(finder, saved.getValue().getFinder());
        assertEquals(1.0, response.matchScore(), 0.000001);
        assertNull(response.collectionDesk());
        assertFalse(response.canReview());
        assertFalse(response.canCompleteHandover());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(finder, new ClaimService.CreateClaimRequest(LOST_ID, FOUND_ID)));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void refusesToCreateAClaimWhenSavedPrivateDetailsAreMissing() {
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        when(foundItems.findById(FOUND_ID)).thenReturn(Optional.of(found));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(claimant, new ClaimService.CreateClaimRequest(LOST_ID, FOUND_ID)));

        assertEquals(409, error.getStatusCode().value());
        verify(claims, never()).save(any(Claim.class));
    }

    @Test
    void refusesToCreateAClaimForALegacyFoundReportWithoutACollectionDesk() {
        found.setCollectionDesk(null);
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        when(foundItems.findById(FOUND_ID)).thenReturn(Optional.of(found));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(claimant, new ClaimService.CreateClaimRequest(LOST_ID, FOUND_ID)));

        assertEquals(409, error.getStatusCode().value());
        verify(claims, never()).save(any(Claim.class));
    }

    @Test
    void exposesCollectionDeskOnlyToAnApprovedClaimant() {
        Claim claim = pendingClaim();
        when(claims.findByClaimantIdOrFinderIdOrderByCreatedAtDesc(1L, 1L)).thenReturn(List.of(claim));
        when(claims.findByClaimantIdOrFinderIdOrderByCreatedAtDesc(2L, 2L)).thenReturn(List.of(claim));

        assertNull(service.mine(claimant).getFirst().collectionDesk());
        assertNull(service.mine(finder).getFirst().collectionDesk());

        claim.setStatus(ClaimStatus.APPROVED_FOR_HANDOVER);
        assertEquals("University Centre Reception", service.mine(claimant).getFirst().collectionDesk());
        assertNull(service.mine(finder).getFirst().collectionDesk());

        claim.setStatus(ClaimStatus.RESOLVED);
        assertEquals("University Centre Reception", service.mine(claimant).getFirst().collectionDesk());
    }

    @Test
    void onlyIndependentStaffCanSeeSavedPrivateDetailsOrReview() {
        Claim claim = pendingClaim();
        when(claims.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.of(secret));
        when(claims.findByStatusInOrderByCreatedAtDesc(any())).thenReturn(List.of(claim));

        ResponseStatusException nonStaff = assertThrows(ResponseStatusException.class,
                () -> service.reviewQueue(finder));
        assertEquals(403, nonStaff.getStatusCode().value());

        var queue = service.reviewQueue(staff);
        assertEquals(1, queue.size());
        assertEquals("red lining", queue.getFirst().privateDetails().interiorColorOrLining());
        assertEquals("claimant@example.edu", queue.getFirst().claimant().email());

        ResponseStatusException participant = assertThrows(ResponseStatusException.class,
                () -> service.review(CLAIM_ID, claimant, new ClaimService.ReviewRequest("APPROVE", "looks right")));
        assertEquals(403, participant.getStatusCode().value());

        assertFalse(java.util.Arrays.stream(ClaimService.ClaimResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .anyMatch(name -> name.contains("private") || name.contains("secret") || name.contains("note")));
    }

    @Test
    void staffApprovalThenStaffCompletionResolvesBothReportsWithoutAnswersOrCode() {
        Claim claim = pendingClaim();
        when(claims.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.of(secret));

        var approved = service.review(CLAIM_ID, staff,
                new ClaimService.ReviewRequest("approve", "Details matched at desk."));

        assertEquals(ClaimStatus.APPROVED_FOR_HANDOVER, claim.getStatus());
        assertEquals(ClaimStatus.APPROVED_FOR_HANDOVER, approved.status());
        assertEquals("Details matched at desk.", claim.getStaffReviewNote());
        assertEquals(staff, claim.getReviewedBy());
        assertNotNull(claim.getReviewedAt());
        verify(notifications).claimApprovedForHandover(claim);

        ResponseStatusException finderBlocked = assertThrows(ResponseStatusException.class,
                () -> service.completeHandover(CLAIM_ID, finder));
        assertEquals(403, finderBlocked.getStatusCode().value());

        var resolved = service.completeHandover(CLAIM_ID, staff);
        assertEquals(ClaimStatus.RESOLVED, claim.getStatus());
        assertEquals("Resolved", lost.getStatus());
        assertEquals("Resolved", found.getStatus());
        assertEquals(ClaimStatus.RESOLVED, resolved.status());
        assertEquals(staff, claim.getCompletedBy());
        assertNotNull(claim.getCompletedAt());
        verify(notifications).claimResolved(claim);
        assertNull(claim.getHandoverCodeHash());
    }

    @Test
    void staffCanRejectButCannotApproveWhenPrivateDetailsAreIncomplete() {
        Claim claim = pendingClaim();
        when(claims.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.of(new LostItemSecret("", "", "")));

        ResponseStatusException incomplete = assertThrows(ResponseStatusException.class,
                () -> service.review(CLAIM_ID, staff, new ClaimService.ReviewRequest("APPROVE", null)));
        assertEquals(409, incomplete.getStatusCode().value());
        assertEquals(ClaimStatus.PENDING_STAFF_REVIEW, claim.getStatus());

        var rejected = service.review(CLAIM_ID, staff, new ClaimService.ReviewRequest("REJECT", "No matching details."));
        assertEquals(ClaimStatus.REJECTED, rejected.status());
        verify(notifications).claimRejected(claim);
    }

    private Claim pendingClaim() {
        Claim claim = new Claim();
        setId(claim, CLAIM_ID);
        claim.setLostItem(lost);
        claim.setFoundItem(found);
        claim.setClaimant(claimant);
        claim.setFinder(finder);
        claim.setStatus(ClaimStatus.PENDING_STAFF_REVIEW);
        claim.setCreatedAt(java.time.LocalDateTime.now());
        return claim;
    }

    private User user(Long id, String email) {
        User user = new User(email.substring(0, email.indexOf('@')), email);
        setId(user, id);
        return user;
    }

    private void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertSame(Object expected, Object actual) {
        assertTrue(expected == actual);
    }
}
