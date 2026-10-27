package com.bharat.scholarship_rag_backend.eligibility;

/**
 * Overall result of matching a profile against one scheme's criteria.
 *
 * <ul>
 *   <li>{@link #ELIGIBLE} — every applicable criterion passes.</li>
 *   <li>{@link #POSSIBLY_ELIGIBLE} — nothing fails, but some criteria are
 *       still unknown; more details would settle it.</li>
 *   <li>{@link #NOT_ELIGIBLE} — at least one criterion fails.</li>
 *   <li>{@link #UNKNOWN} — nothing could be evaluated.</li>
 * </ul>
 *
 * <p>These are advisory. Official rules live with the awarding body; the
 * answer always asks the student to verify on the National Scholarship Portal.
 */
public enum EligibilityVerdict {
    ELIGIBLE,
    POSSIBLY_ELIGIBLE,
    NOT_ELIGIBLE,
    UNKNOWN;

    public boolean isDefinite() {
        return this == ELIGIBLE || this == NOT_ELIGIBLE;
    }
}
