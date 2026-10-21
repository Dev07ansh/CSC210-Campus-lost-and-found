
package com.csc210.backend;

import com.csc210.backend.dsa.MaxHeap;
import com.csc210.backend.dsa.MatchMergeSort;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class HeapVsMergeSortBenchmarkTest {

    // Generate identical, reproducible scores
    private List<Integer> generateScores(int size) {
        Random random = new Random(210);
        List<Integer> scores = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            scores.add(random.nextInt(101));
        }

        return scores;
    }

    private List<Integer> mergeSortTopK(
            List<Integer> scores, int k) {

        List<Integer> sorted =
            MatchMergeSort.sortDescending(scores);

        return new ArrayList<>(
            sorted.subList(0, Math.min(k, sorted.size()))
        );
    }

    private List<Integer> heapTopK(
            List<Integer> scores, int k) {

        MaxHeap<Integer> heap = new MaxHeap<>();

        for (int score : scores) {
            heap.offer(score);
        }

        List<Integer> result = new ArrayList<>();

        while (!heap.isEmpty() && result.size() < k) {
            result.add(heap.poll());
        }

        return result;
    }

    @Test
    void benchmarkHeapVsMergeSort() {

        int[] sizes = {100, 1000, 10000};
        int[] topKValues = {5, 10};

        for (int size : sizes) {
            List<Integer> scores = generateScores(size);

            for (int k : topKValues) {

                List<Integer> expected =
                    mergeSortTopK(scores, k);

                List<Integer> actual =
                    heapTopK(scores, k);

                // Both must return identical top-k scores
                assertEquals(expected, actual);

                // Warm up both algorithms
                for (int i = 0; i < 5; i++) {
                    mergeSortTopK(scores, k);
                    heapTopK(scores, k);
                }

                int iterations = 15;
                long mergeTotal = 0;
                long heapTotal = 0;

                for (int i = 0; i < iterations; i++) {

                    long start = System.nanoTime();
                    mergeSortTopK(scores, k);
                    mergeTotal += System.nanoTime() - start;

                    start = System.nanoTime();
                    heapTopK(scores, k);
                    heapTotal += System.nanoTime() - start;
                }

                double mergeMs =
                    mergeTotal / (iterations * 1_000_000.0);

                double heapMs =
                    heapTotal / (iterations * 1_000_000.0);

                System.out.printf(
                    "n=%d k=%d | MergeSort=%.4f ms | MaxHeap=%.4f ms%n",
                    size, k, mergeMs, heapMs
                );
            }
        }
    }
}
