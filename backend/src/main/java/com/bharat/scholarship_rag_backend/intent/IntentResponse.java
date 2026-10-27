package com.bharat.scholarship_rag_backend.intent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IntentResponse {

    private IntentType type;

    /**
     * Meaningful only for {@link IntentType#SCHOLARSHIP}: {@code true} when the
     * user is asking the knowledge base for a fact about a scheme (documents,
     * amounts, deadlines, rules), {@code false} when the user is asking to be
     * assessed or is still answering profile questions.
     *
     * <p>It is a wrapper rather than a primitive because Jackson 3 refuses to
     * map a missing key onto a primitive, which would reject every older model
     * response that omits the field. A missing key stays {@code null} and reads
     * as {@code false}, so such a response keeps the eligibility-first
     * behaviour instead of silently dropping into retrieval.
     */
    @Getter(AccessLevel.NONE)
    private Boolean  informational;

    /** Null-safe read of {@link #informational}. */
    public boolean isInformational() {
        return Boolean.TRUE.equals(informational);
    }

    public static IntentResponse forType(IntentType type) {
        return new IntentResponse(type, false);
    }

    public static IntentResponse of(IntentType type, boolean informational) {
        return new IntentResponse(type, informational);
    }
}
