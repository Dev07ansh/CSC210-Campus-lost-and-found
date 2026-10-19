package com.csc210.backend.dsa;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared deterministic text policy for search, matching, and evaluation.
 *
 * <p>The policy lowercases with {@link Locale#ROOT}, turns every run of
 * punctuation or whitespace into one space, trims the result, and tokenizes on
 * spaces. It preserves Unicode letters and numbers. It deliberately uses no
 * stop-word removal or stemming so every matching strategy receives the same
 * literal normalized tokens.</p>
 */
@Component
public class TextNormalizer {
    private static final Set<String> NON_EVIDENCE_STRUCTURED_VALUES = Set.of("other", "not specified");

    /** Returns canonical text with punctuation removed and whitespace collapsed. */
    public String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Returns normalized tokens in their original normalized order, including repeated tokens. */
    public List<String> extractKeywords(String text) {
        String normalized = normalize(text);
        if (normalized.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(normalized.split(" ")));
    }

    /**
     * Returns a deterministic set of normalized tokens across several fields.
     * Sets are used by Jaccard and every strategy that needs document terms.
     */
    public Set<String> extractKeywordSet(String... values) {
        Set<String> result = new LinkedHashSet<>();
        if (values == null) {
            return result;
        }
        for (String value : values) {
            result.addAll(extractKeywords(value));
        }
        return result;
    }

    /**
     * A blank value, "Other", or "Not specified" is not evidence for a
     * structured-field agreement. This method applies only to category, color,
     * and location—not free-text descriptions.
     */
    public boolean isMeaningfulStructuredValue(String value) {
        String normalized = normalize(value);
        return !normalized.isEmpty() && !NON_EVIDENCE_STRUCTURED_VALUES.contains(normalized);
    }

    /** True only when both structured values are meaningful and canonical-equal. */
    public boolean sameMeaningfulStructuredValue(String left, String right) {
        return isMeaningfulStructuredValue(left)
                && isMeaningfulStructuredValue(right)
                && normalize(left).equals(normalize(right));
    }
}
