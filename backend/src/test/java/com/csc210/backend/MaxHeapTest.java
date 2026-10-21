
package com.csc210.backend;

import com.csc210.backend.dsa.MaxHeap;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

public class MaxHeapTest {

    @Test
    void testInsertAndRemoveHighest() {

        MaxHeap<Integer> heap = new MaxHeap<>();

        // Insert similarity scores
        heap.offer(65);
        heap.offer(95);
        heap.offer(80);
        heap.offer(45);
        heap.offer(90);

        // Verify the number of items
        assertEquals(5, heap.size());

        // Highest score must be removed first
        assertEquals(95, heap.poll());
        assertEquals(90, heap.poll());
        assertEquals(80, heap.poll());
        assertEquals(65, heap.poll());
        assertEquals(45, heap.poll());

        // Heap should now be empty
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());
    }

    @Test
    void testEmptyHeap() {

        MaxHeap<Integer> heap = new MaxHeap<>();

        // A new heap should be empty
        assertTrue(heap.isEmpty());
        assertEquals(0, heap.size());

        // Removing from an empty heap should throw an exception
        assertThrows(
            NoSuchElementException.class,
            () -> heap.poll()
        );

        // Null values should not be accepted
        assertThrows(
            IllegalArgumentException.class,
            () -> heap.offer(null)
        );
    }

    @Test
    void testDuplicateScores() {

        MaxHeap<Integer> heap = new MaxHeap<>();

        heap.offer(90);
        heap.offer(75);
        heap.offer(90);
        heap.offer(60);
        heap.offer(75);

        // All five values should be stored
        assertEquals(5, heap.size());

        // Duplicate scores should not disappear
        assertEquals(90, heap.poll());
        assertEquals(90, heap.poll());
        assertEquals(75, heap.poll());
        assertEquals(75, heap.poll());
        assertEquals(60, heap.poll());

        assertTrue(heap.isEmpty());
    }

    @Test
    void testTopKBoundaryConditions() {

        java.util.List<Integer> scores =
            java.util.Arrays.asList(65, 95, 80);

        int[] kValues = {0, 2, 10};
        int[][] expected = {
            {},
            {95, 80},
            {95, 80, 65}
        };

        for (int i = 0; i < kValues.length; i++) {

            MaxHeap<Integer> heap = new MaxHeap<>();

            for (int score : scores) {
                heap.offer(score);
            }

            java.util.List<Integer> results =
                new java.util.ArrayList<>();

            while (!heap.isEmpty()
                    && results.size() < kValues[i]) {
                results.add(heap.poll());
            }

            assertEquals(expected[i].length, results.size());

            for (int j = 0; j < expected[i].length; j++) {
                assertEquals(expected[i][j], results.get(j));
            }
        }
    }


}
