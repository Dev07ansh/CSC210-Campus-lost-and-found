package com.csc210.backend.model;

public enum ClaimStatus {
    /** A staff member must compare the saved private details in person. */
    PENDING_STAFF_REVIEW,
    /** Staff approved the claim; the item can now be handed over in person. */
    APPROVED_FOR_HANDOVER,
    RESOLVED,
    REJECTED,
    CANCELLED,

    /**
     * Kept only so an older SQLite row can still be read if migration was
     * interrupted. ClaimSchemaMigrator upgrades it to PENDING_STAFF_REVIEW.
     */
    @Deprecated PENDING_VERIFICATION,
    /**
     * Kept only so an older SQLite row can still be read if migration was
     * interrupted. ClaimSchemaMigrator upgrades it to APPROVED_FOR_HANDOVER.
     */
    @Deprecated VERIFIED
}
