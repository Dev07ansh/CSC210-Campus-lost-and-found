package com.csc210.backend.model;

import jakarta.persistence.*;

@Entity
@Table(name = "verification_attempt", uniqueConstraints = @UniqueConstraint(columnNames = "claimer_email"))
public class VerificationAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "lost_item_id") private Long lostItemId;
    @Column(name = "claimer_email") private String claimerEmail;
    @Column(name = "failed_attempts") private int failedAttempts;
    private boolean suspended;

    public VerificationAttempt() {}
    public VerificationAttempt(Long lostItemId, String claimerEmail) { this.lostItemId = lostItemId; this.claimerEmail = claimerEmail; }
    public Long getId(){return id;}
    public Long getLostItemId(){return lostItemId;} public void setLostItemId(Long value){lostItemId=value;}
    public String getClaimerEmail(){return claimerEmail;} public void setClaimerEmail(String value){claimerEmail=value;}
    public int getFailedAttempts(){return failedAttempts;} public void setFailedAttempts(int value){failedAttempts=value;}
    public boolean isSuspended(){return suspended;} public void setSuspended(boolean value){suspended=value;}
}
