package com.csc210.backend;

import com.csc210.backend.model.LostItem;
import com.csc210.backend.model.VerificationAttempt;
import com.csc210.backend.repository.LostItemRepository;
import com.csc210.backend.repository.VerificationAttemptRepository;
import com.csc210.backend.service.OwnershipVerificationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnershipVerificationServiceTest {
    private static final long LOST_ID = 71L;

    @Mock private LostItemRepository lostItems;
    @Mock private VerificationAttemptRepository attempts;
    private OwnershipVerificationService service;
    private LostItem lost;

    @BeforeEach
    void setUp() {
        lost = lostWithPrivateDetails();
        service = new OwnershipVerificationService(lostItems, attempts);
    }

    @Test
    void acceptsAnswersAtOrAboveTheJaccardThreshold() {
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        when(attempts.findByClaimerEmail("claimer@example.com")).thenReturn(Optional.empty());

        var result = service.verify(LOST_ID, new OwnershipVerificationService.VerificationRequest(
                "CLAIMER@example.com", "red lining inside", "scratch bottom left", "college id cash"));

        assertTrue(result.passed());
        assertFalse(result.suspended());
        assertEquals(0, result.failedAttempts());
        verify(attempts, never()).save(any());
    }

    @Test
    void suspendsAnAccountOnItsThirdIncorrectAttempt() {
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        VerificationAttempt attempt = new VerificationAttempt(LOST_ID, "claimer@example.com");
        when(attempts.findByClaimerEmail("claimer@example.com")).thenReturn(Optional.of(attempt));
        var request = new OwnershipVerificationService.VerificationRequest(
                "claimer@example.com", "blue", "none", "nothing");

        assertFalse(service.verify(LOST_ID, request).suspended());
        assertFalse(service.verify(LOST_ID, request).suspended());
        var third = service.verify(LOST_ID, request);

        assertFalse(third.passed());
        assertTrue(third.suspended());
        assertEquals(3, third.failedAttempts());
        verify(attempts, times(3)).save(attempt);
    }

    @Test
    void rejectsMissingClaimantIdentityAndLegacyReports() {
        when(lostItems.findById(LOST_ID)).thenReturn(Optional.of(lost));
        var noEmail = assertThrows(ResponseStatusException.class, () -> service.verify(LOST_ID,
                new OwnershipVerificationService.VerificationRequest("", "red", "scratch", "id")));
        assertEquals(400, noEmail.getStatusCode().value());

        LostItem legacy = new LostItem();
        when(lostItems.findById(99L)).thenReturn(Optional.of(legacy));
        var legacyError = assertThrows(ResponseStatusException.class, () -> service.verify(99L,
                new OwnershipVerificationService.VerificationRequest("claimer@example.com", "red", "scratch", "id")));
        assertEquals(400, legacyError.getStatusCode().value());

        var nullAnswers = assertThrows(ResponseStatusException.class, () -> service.verify(LOST_ID, null));
        assertEquals(400, nullAnswers.getStatusCode().value());
    }

    @Test
    void marksPrivateAnswersWriteOnlyForTheApi() throws Exception {
        JsonProperty annotation = LostItem.class.getDeclaredField("interiorColorOrLining").getAnnotation(JsonProperty.class);

        assertEquals(JsonProperty.Access.WRITE_ONLY, annotation.access());
        assertTrue(lost.isVerificationAvailable());
    }

    private LostItem lostWithPrivateDetails() {
        LostItem item = new LostItem();
        item.setInteriorColorOrLining("red lining inside");
        item.setUniqueMarks("scratch bottom left");
        item.setContents("college id cash");
        return item;
    }
}
