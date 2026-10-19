package com.csc210.backend;

import com.csc210.backend.dsa.TextNormalizer;
import com.csc210.backend.dsa.TextSimilarityStrategies;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextSimilarityStrategiesTest {
    private static final double TOLERANCE = 1e-12;
    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void jaccardReturnsHalfForPartialOverlap() {
        var query = document("Q", "black wallet leather");
        var candidate = document("C", "wallet leather chain");

        // {black,wallet,leather} ∩ {wallet,leather,chain} has 2 tokens; union has 4.
        // Jaccard = 2/4 = 0.5.
        assertAndPrint(TextSimilarityStrategies.jaccard(), query, candidate, List.of(candidate), 0.5);
    }

    @Test
    void jaccardIgnoresRepeatedTerms() {
        var query = document("Q", "wallet wallet leather");
        var candidate = document("C", "wallet leather");

        // Both unique-token sets are {wallet,leather}; Jaccard = 2/2 = 1.
        assertAndPrint(TextSimilarityStrategies.jaccard(), query, candidate, List.of(candidate), 1.0);
    }

    @Test
    void tfIdfUsesOnlyTheCandidatePoolForIdf() {
        var c1 = document("C1", "wallet leather");
        var c2 = document("C2", "wallet keys");
        var c3 = document("C3", "keys");
        var query = document("Q", "wallet wallet leather");
        List<TextSimilarityStrategies.TextDocument> pool = List.of(c1, c2, c3);

        // N=3; a=idf(wallet)=1+ln(4/3), b=idf(leather)=1+ln(4/2)=1+ln(2).
        // q=(2a/3,b/3), c1=(a/2,b/2).
        // cosine=(2a²+b²)/sqrt((4a²+b²)(a²+b²)) = 0.943085996661427.
        assertAndPrint(TextSimilarityStrategies.tfIdfCosine(), query, c1, pool, 0.943085996661427);
    }

    @Test
    void tfIdfIsOneForSameNormalizedDocument() {
        var query = document("Q", "Wallet, Leather!");
        var candidate = document("C", "wallet leather");

        // Normalized vectors are proportional and nonempty, so cosine = 1.
        assertAndPrint(TextSimilarityStrategies.tfIdfCosine(), query, candidate, List.of(candidate), 1.0);
    }

    @Test
    void tfIdfKeepsAQueryOnlyTermInTheVector() {
        var c1 = document("C1", "wallet");
        var c2 = document("C2", "wallet");
        var query = document("Q", "unseen wallet");

        // N=2. df(wallet)=2, so idf(wallet)=1+ln(3/3)=1.
        // df(unseen)=0, so idf(unseen)=1+ln(3)=2.09861228866811.
        // q=(idf(unseen)/2, 1/2), c1=(0,1).
        // cosine=(1/2)/sqrt((idf(unseen)/2)^2+(1/2)^2)=0.430165282498796.
        assertAndPrint(TextSimilarityStrategies.tfIdfCosine(), query, c1, List.of(c1, c2), 0.430165282498796);
    }

    @Test
    void tfIdfDoesNotChangeWhenCandidatePoolOrderChanges() {
        var c1 = document("C1", "wallet leather");
        var c2 = document("C2", "wallet keys");
        var c3 = document("C3", "keys");
        var query = document("Q", "wallet wallet leather");
        var strategy = TextSimilarityStrategies.tfIdfCosine();

        double forward = strategy.score(query, c1, List.of(c1, c2, c3));
        double reversed = strategy.score(query, c1, List.of(c3, c2, c1));
        System.out.printf(Locale.ROOT, "P2.2 tf_idf_cosine pool_order forward=%.12f reversed=%.12f%n", forward, reversed);
        assertEquals(forward, reversed, TOLERANCE);
        assertEquals(0.943085996661427, forward, TOLERANCE);
    }

    @Test
    void fuzzyLevenshteinRecognizesOneCharacterTypo() {
        var query = document("Q", "wallet keys");
        var candidate = document("C", "walet keys");

        // keys↔keys: 1. wallet↔walet: distance 1, max length 6, similarity 1-1/6=5/6.
        // Fuzzy score = (1 + 5/6) / max(2,2) = 11/12 = 0.916666666666667.
        assertAndPrint(TextSimilarityStrategies.levenshteinTokenFuzzy(), query, candidate, List.of(candidate), 11.0 / 12.0);
    }

    @Test
    void fuzzyLevenshteinIsTokenOrderIndependent() {
        var query = document("Q", "keys wallet");
        var candidate = document("C", "walet keys");

        // The same two one-to-one token pairs are selected as in the typo example: 11/12.
        assertAndPrint(TextSimilarityStrategies.levenshteinTokenFuzzy(), query, candidate, List.of(candidate), 11.0 / 12.0);
    }

    @Test
    void allStrategiesUseTheSharedNormalizerAndRejectBlankText() {
        var normalizedQuery = document("Q", "Wallet, LEATHER!");
        var normalizedCandidate = document("C", "wallet leather");
        var blank = document("B", null);

        for (TextSimilarityStrategies.Strategy strategy : TextSimilarityStrategies.defaultStrategies()) {
            assertAndPrint(strategy, normalizedQuery, normalizedCandidate, List.of(normalizedCandidate), 1.0);
            assertAndPrint(strategy, blank, normalizedCandidate, List.of(normalizedCandidate), 0.0);
        }
    }

    private TextSimilarityStrategies.TextDocument document(String id, String text) {
        return TextSimilarityStrategies.TextDocument.fromReport(id, text, null, normalizer);
    }

    private void assertAndPrint(TextSimilarityStrategies.Strategy strategy,
                                TextSimilarityStrategies.TextDocument query,
                                TextSimilarityStrategies.TextDocument candidate,
                                List<TextSimilarityStrategies.TextDocument> pool,
                                double expected) {
        double actual = strategy.score(query, candidate, pool);
        System.out.printf(Locale.ROOT, "P2.2 %s query=%s candidate=%s score=%.12f%n",
                strategy.id(), query.id(), candidate.id(), actual);
        assertEquals(expected, actual, TOLERANCE);
    }
}
