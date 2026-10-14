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

    @PostMapping("/{id}/verify")
    public ClaimService.VerificationResponse verify(@PathVariable Long id,
                                                     @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                                     @RequestBody ClaimService.VerificationRequest request) {
        return claims.verify(id, currentUsers.requireUser(userEmail), request);
    }

    @PostMapping("/{id}/resolve")
    public ClaimService.ClaimResponse resolve(@PathVariable Long id,
                                               @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                               @RequestBody ClaimService.ResolveRequest request) {
        return claims.resolve(id, currentUsers.requireUser(userEmail), request);
    }

    @PostMapping("/{id}/cancel")
    public ClaimService.ClaimResponse cancel(@PathVariable Long id,
                                              @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return claims.cancel(id, currentUsers.requireUser(userEmail));
    }
}
