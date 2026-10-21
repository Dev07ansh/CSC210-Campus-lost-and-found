
package com.csc210.backend.dsa;

import java.util.ArrayList;
import java.util.List;

public class MatchMergeSort {

    // Sort items from highest to lowest using natural ordering
    public static <T extends Comparable<? super T>>
            List<T> sortDescending(List<T> items) {

        if (items == null) {
            throw new IllegalArgumentException(
                "Items cannot be null");
        }

        List<T> result = new ArrayList<>(items);

        mergeSort(result, 0, result.size());

        return result;
    }

    // Recursively divide the list into smaller halves
    private static <T extends Comparable<? super T>>
            void mergeSort(List<T> items, int start, int end) {

        if (end - start <= 1) {
            return;
        }

        int mid = start + (end - start) / 2;

        mergeSort(items, start, mid);
        mergeSort(items, mid, end);

        merge(items, start, mid, end);
    }

    // Combine two sorted halves in descending order
    private static <T extends Comparable<? super T>>
            void merge(List<T> items, int start,
                       int mid, int end) {

        List<T> merged = new ArrayList<>();

        int left = start;
        int right = mid;

        while (left < mid && right < end) {
            T leftItem = items.get(left);
            T rightItem = items.get(right);

            // Higher-ranked item comes first
            if (leftItem.compareTo(rightItem) >= 0) {
                merged.add(leftItem);
                left++;
            } else {
                merged.add(rightItem);
                right++;
            }
        }

        // Add remaining items from the left half
        while (left < mid) {
            merged.add(items.get(left++));
        }

        // Add remaining items from the right half
        while (right < end) {
            merged.add(items.get(right++));
        }

        // Copy merged items into the original list
        for (int i = 0; i < merged.size(); i++) {
            items.set(start + i, merged.get(i));
        }
    }
}
