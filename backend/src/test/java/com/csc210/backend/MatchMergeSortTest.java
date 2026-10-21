package com.csc210.backend;

import com.csc210.backend.dsa.MatchMergeSort;
import com.csc210.backend.dsa.MaxHeap;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import com.csc210.backend.dsa.MaxHeap;
import java.util.ArrayList;

public class MatchMergeSortTest {

    @Test
    void testDescendingOrder() {

        // Sample similarity scores
        List<Integer> scores =
            Arrays.asList(65, 95, 80, 45, 90);

        // Apply our custom Merge Sort
        List<Integer> sorted =
            MatchMergeSort.sortDescending(scores);

        // Verify highest scores come first
        assertEquals(
            Arrays.asList(95, 90, 80, 65, 45),
            sorted
        );

        // Original list should remain unchanged
        assertEquals(
            Arrays.asList(65, 95, 80, 45, 90),
            scores
        );
    }

    @Test
    void testEqualScoresAndDuplicates() {

        List<Integer> scores =
            Arrays.asList(80, 95, 80, 65, 95);

        List<Integer> sorted =
            MatchMergeSort.sortDescending(scores);

        assertEquals(
            Arrays.asList(95, 95, 80, 80, 65),
            sorted
        );

        // Original list remains unchanged
        assertEquals(
            Arrays.asList(80, 95, 80, 65, 95),
            scores
        );
    }

    @Test
    void testMergeSortMatchesMaxHeap() {

        List<Integer> scores =
            Arrays.asList(65, 95, 80, 45, 90, 80);

        // Sort using our custom Merge Sort
        List<Integer> mergeResult =
            MatchMergeSort.sortDescending(scores);

        // Rank the same scores using Max-Heap
        MaxHeap<Integer> heap = new MaxHeap<>();

        for (Integer score : scores) {
            heap.offer(score);
        }

        List<Integer> heapResult = new ArrayList<>();

        while (!heap.isEmpty()) {
            heapResult.add(heap.poll());
        }

        // Both algorithms must produce identical rankings
        assertEquals(mergeResult, heapResult);
    }

    @Test
    void testScoreAndIdTieBreaking() {

        // Represents a ranked match: score + item ID
        record RankedMatch(long id, int score)
                implements Comparable<RankedMatch> {

            @Override
            public int compareTo(RankedMatch other) {
                int scoreComparison =
                    Integer.compare(score, other.score);

                if (scoreComparison != 0) {
                    return scoreComparison;
                }

                // Smaller ID ranks higher for equal scores
                return Long.compare(other.id, id);
            }
        }

        List<RankedMatch> matches = Arrays.asList(
            new RankedMatch(3, 90),
            new RankedMatch(1, 90),
            new RankedMatch(5, 75),
            new RankedMatch(2, 95)
        );

        List<RankedMatch> sorted =
            MatchMergeSort.sortDescending(matches);

        assertEquals(
            Arrays.asList(
                new RankedMatch(2, 95),
                new RankedMatch(1, 90),
                new RankedMatch(3, 90),
                new RankedMatch(5, 75)
            ),
            sorted
        );

        // Verify Max-Heap produces the same order
        MaxHeap<RankedMatch> heap = new MaxHeap<>();

        for (RankedMatch match : matches) {
            heap.offer(match);
        }

        for (RankedMatch expected : sorted) {
            assertEquals(expected, heap.poll());
        }
    }


}
