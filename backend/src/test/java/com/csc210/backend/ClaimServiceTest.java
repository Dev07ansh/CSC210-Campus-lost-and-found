package com.csc210.backend;

import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.model.*;
import com.csc210.backend.repository.*;
import com.csc210.backend.service.ClaimService;
import com.csc210.backend.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {
    private static final long LOST_ID = 41L;
    private static final long FOUND_ID = 52L;
    private static final long CLAIM_ID = 63L;

    @Mock private ClaimRepository claims;
    @Mock private ClaimVerificationAttemptRepository attempts;
    @Mock private LostItemRepository lostItems;
    @Mock private FoundItemRepository foundItems;
    @Mock private LostItemSecretRepository secrets;
    @Mock private UserRepository users;
    private ClaimService service;
    private User claimant;
    private User finder;
    private LostItem lost;
    private FoundItem found;
    private LostItemSecret secret;

    @BeforeEach
    void setUp() {
        claimant = user(1L, "claimant@example.edu");
        finder = user(2L, "finder@example.edu");
        lost = new LostItem();
        setId(lost, LOST_ID);
        lost.setItemName("Black wallet");
        lost.setColor("Black");
        lost.setLocation("Library");
        lost.setDescription("Leather wallet");
        lost.setStatus("Active");
        lost.setReporter(claimant);
        found = new FoundItem();
        setId(found, FOUND_ID);
        found.setItemName("Black wallet");
        found.setColor("Black");
        found.setLocation("Library");
        found.setDescription("Leather wallet");
        found.setStatus("Active");
        found.setReporter(finder);
        secret = new LostItemSecret("red lining", "scratch lower corner", "college id and cash");
        secret.setLostItem(lost);
        service = new ClaimService(claims, attempts, lostItems, foundItems, secrets,
                new SimilarityScorer(), new CurrentUserService(users));
        when(claims.save(any(Claim.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsOnlyForLostReportOwnerAndCalculatesScoreServerSide() {
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        when(foundItems.findById(FOUND_ID)).thenReturn(Optional.of(found));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.of(secret));
        when(claims.existsByLostItemIdAndStatusIn(eq(LOST_ID), any())).thenReturn(false);
        when(claims.existsByFoundItemIdAndStatusIn(eq(FOUND_ID), any())).thenReturn(false);

        var response = service.create(claimant, new ClaimService.CreateClaimRequest(LOST_ID, FOUND_ID));

        ArgumentCaptor<Claim> saved = ArgumentCaptor.forClass(Claim.class);
        verify(claims).save(saved.capture());
        assertEquals(ClaimStatus.PENDING_VERIFICATION, saved.getValue().getStatus());
        assertSame(claimant, saved.getValue().getClaimant());
        assertSame(finder, saved.getValue().getFinder());
        assertEquals(1.0, response.matchScore());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(finder, new ClaimService.CreateClaimRequest(LOST_ID, FOUND_ID)));
        assertEquals(403, error.getStatusCode().value());
    }

    @Test
    void refusesToCreateAnUnverifiableClaimWhenPrivateDetailsAreMissing() {
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        when(foundItems.findById(FOUND_ID)).thenReturn(Optional.of(found));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(claimant, new ClaimService.CreateClaimRequest(LOST_ID, FOUND_ID)));

        assertEquals(409, error.getStatusCode().value());
        verify(claims, never()).save(any(Claim.class));
    }

    @Test
    void successfulVerificationIssuesCodeButStoresNoRawAnswers() {
        Claim claim = pendingClaim();
        when(claims.findById(CLAIM_ID)).thenReturn(Optional.of(claim));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.of(secret));
        when(attempts.countByClaimId(CLAIM_ID)).thenReturn(0);

        var result = service.verify(CLAIM_ID, claimant,
                new ClaimService.VerificationRequest("red lining", "scratch lower corner", "college id cash"));

        assertTrue(result.passed());
        assertFalse(result.rejected());
        assertEquals(ClaimStatus.VERIFIED, claim.getStatus());
        assertNotNull(result.handoverCode());
        assertTrue(result.handoverCode().matches("\\d{6}"));
        ArgumentCaptor<ClaimVerificationAttempt> saved = ArgumentCaptor.forClass(ClaimVerificationAttempt.class);
        verify(attempts).save(saved.capture());
        assertTrue(saved.getValue().isPassed());
        assertEquals(1, saved.getValue().getAttemptNumber());
        assertFalse(java.util.Arrays.stream(ClaimVerificationAttempt.class.getDeclaredFields())
                .map(Field::getName).anyMatch(name -> name.toLowerCase().contains("answer")));
    }

    @Test
    void thirdFailedAttemptRejectsAndOnlyFinderCanResolveCorrectCode() {
        Claim rejected = pendingClaim();
        when(claims.findById(CLAIM_ID)).thenReturn(Optional.of(rejected));
        when(secrets.findByLostItemId(LOST_ID)).thenReturn(Optional.of(secret));
        when(attempts.countByClaimId(CLAIM_ID)).thenReturn(2);

        var failed = service.verify(CLAIM_ID, claimant,
                new ClaimService.VerificationRequest("blue", "nothing", "nothing"));
        assertFalse(failed.passed());
        assertTrue(failed.rejected());
        assertEquals(ClaimStatus.REJECTED, rejected.getStatus());

        Claim verified = pendingClaim();
        when(claims.findById(CLAIM_ID)).thenReturn(Optional.of(verified));
        when(attempts.countByClaimId(CLAIM_ID)).thenReturn(0);
        var passed = service.verify(CLAIM_ID, claimant,
                new ClaimService.VerificationRequest("red lining", "scratch lower corner", "college id cash"));
        assertThrows(ResponseStatusException.class,
                () -> service.resolve(CLAIM_ID, claimant, new ClaimService.ResolveRequest(passed.handoverCode())));

        var resolved = service.resolve(CLAIM_ID, finder, new ClaimService.ResolveRequest(passed.handoverCode()));
        assertEquals(ClaimStatus.RESOLVED, verified.getStatus());
        assertEquals("Resolved", lost.getStatus());
        assertEquals("Resolved", found.getStatus());
        assertEquals(ClaimStatus.RESOLVED, resolved.status());
    }

    private Claim pendingClaim() {
        Claim claim = new Claim();
        setId(claim, CLAIM_ID);
        claim.setLostItem(lost);
        claim.setFoundItem(found);
        claim.setClaimant(claimant);
        claim.setFinder(finder);
        claim.setStatus(ClaimStatus.PENDING_VERIFICATION);
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
}
