package com.csc210.backend;

import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.dsa.TextNormalizer;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.service.FoundItemService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityScorerTest {
    private static final double TOLERANCE = 1e-9;
    private final TextNormalizer normalizer = new TextNormalizer();
    private final SimilarityScorer scorer = new SimilarityScorer(normalizer);

    @Test
    void scoresIdenticalMeaningfulReportsAsOne() {
        LostItem lost = lost("Black leather wallet", "Wallet", "Black", "Library", "Campus ID inside");
        FoundItem found = found("black leather wallet", "wallet", " black ", "library", "campus id inside");

        // Color = 1, location = 1, Jaccard({black,leather,wallet,campus,id,inside}, same) = 6/6 = 1.
        // Score = 0.35(1) + 0.30(1) + 0.35(1) = 1.00.
        assertEquals(1.0, scorer.score(lost, found), TOLERANCE);
    }

    @Test
    void calculatesPartialScoreWithHandComputedJaccard() {
        LostItem lost = lost("Black wallet", "Wallet", "Black", "Library", null);
        FoundItem found = found("Black leather wallet", "wallet", "black", "Gate 12", null);

        // Color = 1, location = 0.
        // Tokens: lost={black,wallet}, found={black,leather,wallet}; Jaccard = |{black,wallet}|/3 = 2/3.
        // Score = 0.35(1) + 0.30(0) + 0.35(2/3) = 0.5833333333333333.
        assertEquals(0.35 + 0.35 * (2.0 / 3.0), scorer.score(lost, found), TOLERANCE);
    }

    @Test
    void scoresDisjointSameCategoryReportsAsZero() {
        LostItem lost = lost("Red backpack", "Bags", "Red", "Library", null);
        FoundItem found = found("Blue umbrella", "Bags", "Blue", "Gate 12", null);

        // Color = 0, location = 0, Jaccard({red,backpack},{blue,umbrella}) = 0/4 = 0.
        // Score = 0.35(0) + 0.30(0) + 0.35(0) = 0.
        assertEquals(0.0, scorer.score(lost, found), TOLERANCE);
    }

    @Test
    void treatsBlankAndUnknownStructuredValuesAsNoEvidence() {
        LostItem lost = lost("", "Wallet", " ", "Other", null);
        FoundItem found = found("", "wallet", "", "not specified", null);

        // Blank/Other/Not specified never agree; both token sets are empty so keyword = 0.
        assertEquals(0.0, scorer.score(lost, found), TOLERANCE);
        assertEquals(0.0, scorer.score(null, found), TOLERANCE);
    }

    @Test
    void rejectsCrossCategoryCandidatesExactly() {
        LostItem lost = lost("Black wallet", "Wallet", "Black", "Library", "leather");
        FoundItem found = found("Black wallet", "Keys", "Black", "Library", "leather");

        // The category gate is evaluated first, so all otherwise matching components are ignored.
        assertEquals(0.0, scorer.score(lost, found), 0.0);
        assertFalse(scorer.explain(lost, found, SimilarityScorer.BASELINE_WEIGHTS).categoryMatched());
    }

    @Test
    void normalizesCaseAndPunctuationBeforeComparing() {
        LostItem lost = lost("Blue, leather-wallet!", "Wallet!", "Navy-Blue", "University Centre", null);
        FoundItem found = found("blue leather wallet", " wallet ", " navy blue ", "university-centre", null);

        // All structured values canonicalize equal and tokens are the same three words.
        // Score = 0.35 + 0.30 + 0.35 = 1.00.
        assertEquals(1.0, scorer.score(lost, found), TOLERANCE);
    }

    @Test
    void rejectsOtherCategoryInsteadOfTreatingTwoUnknownsAsEqual() {
        LostItem lost = lost("Blue bag", "Other", "Blue", "Library", null);
        FoundItem found = found("Blue bag", "other", "Blue", "Library", null);

        assertEquals(0.0, scorer.score(lost, found), 0.0);
    }

    @Test
    void validatesWeightsAndKeepsScoresInTheClosedUnitInterval() {
        assertThrows(IllegalArgumentException.class, () -> new SimilarityScorer.ScoreWeights(0.5, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new SimilarityScorer.ScoreWeights(-0.1, 0.5, 0.6));

        double score = scorer.score(
                lost("Wallet", "Wallet", "Black", "Library", "leather"),
                found("Wallet", "Wallet", "Black", "Library", "leather"));
        assertTrue(score >= 0.0 && score <= 1.0);
    }

    @Test
    void ranksEqualScoresByCandidateIdAscending() {
        FoundItem higherId = found("Black wallet", "Wallet", "Black", "Library", "leather");
        FoundItem lowerId = found("Black wallet", "Wallet", "Black", "Library", "leather");
        setId(higherId, 20L);
        setId(lowerId, 10L);

        // Both candidates score 1.0. MaxHeap polls a positive comparison first, so ID 10 must precede ID 20.
        assertTrue(compareForMaxHeap(lowerId, 1.0, higherId, 1.0) > 0);
        assertTrue(compareForMaxHeap(higherId, 1.0, lowerId, 1.0) < 0);
    }

    private LostItem lost(String itemName, String category, String color, String location, String description) {
        LostItem item = new LostItem();
        item.setItemName(itemName);
        item.setCategory(category);
        item.setColor(color);
        item.setLocation(location);
        item.setDescription(description);
        return item;
    }

    private FoundItem found(String itemName, String category, String color, String location, String description) {
        FoundItem item = new FoundItem();
        item.setItemName(itemName);
        item.setCategory(category);
        item.setColor(color);
        item.setLocation(location);
        item.setDescription(description);
        return item;
    }

    private void setId(Object target, Long id) {
        try {
            Field field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private int compareForMaxHeap(FoundItem first, double firstScore, FoundItem second, double secondScore) {
        try {
            Method method = FoundItemService.class.getDeclaredMethod("compareForMaxHeap",
                    FoundItem.class, double.class, FoundItem.class, double.class);
            method.setAccessible(true);
            return (int) method.invoke(null, first, firstScore, second, secondScore);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
