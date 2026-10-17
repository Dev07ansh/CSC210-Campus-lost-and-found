package com.csc210.backend.repository;

import com.csc210.backend.model.Claim;
import com.csc210.backend.model.ClaimStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    List<Claim> findByClaimantIdOrFinderIdOrderByCreatedAtDesc(Long claimantId, Long finderId);
    List<Claim> findByStatusInOrderByCreatedAtDesc(Collection<ClaimStatus> statuses);
    boolean existsByLostItemIdAndStatusIn(Long lostItemId, Collection<ClaimStatus> statuses);
    boolean existsByFoundItemIdAndStatusIn(Long foundItemId, Collection<ClaimStatus> statuses);
}
