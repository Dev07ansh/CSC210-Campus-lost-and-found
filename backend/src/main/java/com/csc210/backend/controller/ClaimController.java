package com.csc210.backend.controller;

import com.csc210.backend.model.User;
import com.csc210.backend.service.ClaimService;
import com.csc210.backend.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/claims")
public class ClaimController {
    private final ClaimService claims;
    private final CurrentUserService currentUsers;

    public ClaimController(ClaimService claims, CurrentUserService currentUsers) {
        this.claims = claims;
        this.currentUsers = currentUsers;
    }

    @PostMapping
    public ResponseEntity<ClaimService.ClaimResponse> create(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestBody ClaimService.CreateClaimRequest request) {
        User currentUser = currentUsers.requireUser(userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(claims.create(currentUser, request));
    }

    @GetMapping("/me")
    public List<ClaimService.ClaimResponse> mine(@RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return claims.mine(currentUsers.requireUser(userEmail));
    }

    /**
     * The claimant does not re-answer the private details after claiming. Only
     * configured staff can reach the separate review queue, which is also the
     * sole API that exposes those saved values.
     */
    @GetMapping("/review-queue")
    public List<ClaimService.StaffReviewClaimResponse> reviewQueue(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return claims.reviewQueue(currentUsers.requireUser(userEmail));
    }

    @PostMapping("/{id}/review")
    public ClaimService.StaffReviewClaimResponse review(@PathVariable Long id,
                                                          @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                                          @RequestBody ClaimService.ReviewRequest request) {
        return claims.review(id, currentUsers.requireUser(userEmail), request);
    }

    @PostMapping("/{id}/complete-handover")
    public ClaimService.StaffReviewClaimResponse completeHandover(@PathVariable Long id,
                                                                    @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return claims.completeHandover(id, currentUsers.requireUser(userEmail));
    }

    @GetMapping("/staff-access")
    public ClaimService.StaffAccessResponse staffAccess(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return claims.staffAccess(currentUsers.requireUser(userEmail));
    }

    @PostMapping("/{id}/cancel")
    public ClaimService.ClaimResponse cancel(@PathVariable Long id,
                                              @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return claims.cancel(id, currentUsers.requireUser(userEmail));
    }
}
