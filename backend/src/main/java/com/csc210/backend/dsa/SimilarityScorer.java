package com.csc210.backend.dsa;

import com.csc210.backend.model.FoundItem;
import com.csc210.backend.model.LostItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic baseline report scorer. Its output ranks possible candidates;
 * it never proves ownership or approves a claim.
 */
@Component
public class SimilarityScorer {
    public static final ScoreWeights BASELINE_WEIGHTS = new ScoreWeights(0.35, 0.30, 0.35);

    private final TextNormalizer normalizer;

    /** Preserves the previous direct-construction API for small unit tests and tools. */
    public SimilarityScorer() {
        this(new TextNormalizer());
    }

    @Autowired
    public SimilarityScorer(TextNormalizer normalizer) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
    }

    /** Scores with the documented 35% color, 30% location, 35% keyword baseline. */
    public double score(LostItem lost, FoundItem found) {
        return explain(lost, found, BASELINE_WEIGHTS).score();
    }

    /** Scores with a validated weight set, which lets the benchmark sweep only weights. */
    public double score(LostItem lost, FoundItem found, ScoreWeights weights) {
        return explain(lost, found, weights).score();
    }

    /**
     * Produces the component values used by the baseline. This makes the
     * hand-computable formula inspectable without exposing any claim decision.
     */
    public ScoreBreakdown explain(LostItem lost, FoundItem found, ScoreWeights weights) {
        Objects.requireNonNull(weights, "weights");
        if (lost == null || found == null || !sameMeaningfulCategory(lost, found)) {
            return ScoreBreakdown.categoryRejected();
        }

        double color = structuredAgreement(lost.getColor(), found.getColor());
        double location = structuredAgreement(lost.getLocation(), found.getLocation());
        double keyword = keywordJaccard(lost, found);
        double score = clamp(weights.color() * color + weights.location() * location + weights.keyword() * keyword);
        return new ScoreBreakdown(true, color, location, keyword, score);
    }

    /** Category is a hard gate: unknown or different categories are exactly zero. */
    public boolean sameMeaningfulCategory(LostItem lost, FoundItem found) {
        return lost != null
                && found != null
                && normalizer.sameMeaningfulStructuredValue(lost.getCategory(), found.getCategory());
    }

    /** Exact normalized Jaccard over the item name and description token sets. */
    public double keywordJaccard(LostItem lost, FoundItem found) {
        if (lost == null || found == null) {
            return 0.0;
        }
        return jaccard(
                normalizer.extractKeywordSet(lost.getItemName(), lost.getDescription()),
                normalizer.extractKeywordSet(found.getItemName(), found.getDescription()));
    }

    /**
     * Applies the same structured-field/category policy while accepting a
     * separately calculated keyword score. P2.2 strategies use this to change
     * only text similarity, not candidate pool or structured components.
     */
    public double scoreWithKeywordSimilarity(LostItem lost, FoundItem found, ScoreWeights weights,
                                             double keywordSimilarity) {
        Objects.requireNonNull(weights, "weights");
        if (lost == null || found == null || !sameMeaningfulCategory(lost, found)) {
            return 0.0;
        }
        double color = structuredAgreement(lost.getColor(), found.getColor());
        double location = structuredAgreement(lost.getLocation(), found.getLocation());
        return clamp(weights.color() * color
                + weights.location() * location
                + weights.keyword() * clamp(keywordSimilarity));
    }

    /** Returns zero for either empty set so there is no empty-set auto-match or division by zero. */
    public double jaccard(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new LinkedHashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 0.0 : clamp((double) intersection.size() / union.size());
    }

    private double structuredAgreement(String left, String right) {
        return normalizer.sameMeaningfulStructuredValue(left, right) ? 1.0 : 0.0;
    }

    private double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Validated, nonnegative weights whose sum is exactly one within floating-point tolerance. */
    public record ScoreWeights(double color, double location, double keyword) {
        private static final double SUM_TOLERANCE = 1e-12;

        public ScoreWeights {
            if (!Double.isFinite(color) || !Double.isFinite(location) || !Double.isFinite(keyword)
                    || color < 0.0 || location < 0.0 || keyword < 0.0
                    || Math.abs(color + location + keyword - 1.0) > SUM_TOLERANCE) {
                throw new IllegalArgumentException("Similarity weights must be finite, nonnegative, and sum to 1.0.");
            }
        }
    }

    public record ScoreBreakdown(boolean categoryMatched, double color, double location,
                                 double keyword, double score) {
        private static ScoreBreakdown categoryRejected() {
            return new ScoreBreakdown(false, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
