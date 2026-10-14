package com.csc210.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A handover request. A similarity score is context only, never an approval. */
@Entity
@Table(name = "claims", indexes = {
        @Index(name = "idx_claim_claimant", columnList = "claimant_id"),
        @Index(name = "idx_claim_finder", columnList = "finder_id"),
        @Index(name = "idx_claim_lost_item", columnList = "lost_item_id"),
        @Index(name = "idx_claim_found_item", columnList = "found_item_id")
})
public class Claim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lost_item_id", nullable = false) private LostItem lostItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "found_item_id", nullable = false) private FoundItem foundItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claimant_id", nullable = false) private User claimant;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "finder_id", nullable = false) private User finder;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ClaimStatus status;
    @Column(nullable = false) private double matchScore;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime verifiedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime cancelledAt;
    @JsonIgnore private String handoverCodeHash;
    @JsonIgnore private LocalDateTime handoverCodeExpiresAt;

    public Claim() {}
    public Long getId(){return id;}
    public long getVersion(){return version;}
    public LostItem getLostItem(){return lostItem;} public void setLostItem(LostItem value){lostItem=value;}
    public FoundItem getFoundItem(){return foundItem;} public void setFoundItem(FoundItem value){foundItem=value;}
    public User getClaimant(){return claimant;} public void setClaimant(User value){claimant=value;}
    public User getFinder(){return finder;} public void setFinder(User value){finder=value;}
    public ClaimStatus getStatus(){return status;} public void setStatus(ClaimStatus value){status=value;}
    public double getMatchScore(){return matchScore;} public void setMatchScore(double value){matchScore=value;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime value){createdAt=value;}
    public LocalDateTime getVerifiedAt(){return verifiedAt;} public void setVerifiedAt(LocalDateTime value){verifiedAt=value;}
    public LocalDateTime getResolvedAt(){return resolvedAt;} public void setResolvedAt(LocalDateTime value){resolvedAt=value;}
    public LocalDateTime getCancelledAt(){return cancelledAt;} public void setCancelledAt(LocalDateTime value){cancelledAt=value;}
    @JsonIgnore public String getHandoverCodeHash(){return handoverCodeHash;} public void setHandoverCodeHash(String value){handoverCodeHash=value;}
    @JsonIgnore public LocalDateTime getHandoverCodeExpiresAt(){return handoverCodeExpiresAt;} public void setHandoverCodeExpiresAt(LocalDateTime value){handoverCodeExpiresAt=value;}
}
