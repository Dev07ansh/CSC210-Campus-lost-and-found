
package com.csc210.backend;

import com.csc210.backend.dsa.FoundItemBST;
import com.csc210.backend.dsa.FoundItemBinarySearch;
import com.csc210.backend.model.FoundItem;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BSTvsBinarySearchBenchmarkTest {

    private List<FoundItem> generateItems(int size) {
        Random random = new Random(210);
        List<FoundItem> items = new ArrayList<>();

        LocalDateTime base =
            LocalDateTime.of(2026, 1, 1, 0, 0);

        for (int i = 0; i < size; i++) {
            FoundItem item = mock(FoundItem.class);

            long id = i + 1L;
            LocalDateTime date =
                base.plusDays(random.nextInt(365));

            when(item.getId()).thenReturn(id);
            when(item.getDateTime()).thenReturn(date);

            items.add(item);
        }

        return items;
    }

    @Test
    void benchmarkBSTvsBinarySearch() {

        int[] sizes = {100, 1000, 10000};

        LocalDateTime start =
            LocalDateTime.of(2026, 4, 1, 0, 0);

        LocalDateTime end =
            LocalDateTime.of(2026, 6, 30, 23, 59);

        FoundItemBinarySearch binarySearch =
            new FoundItemBinarySearch();

        for (int size : sizes) {

            List<FoundItem> items = generateItems(size);

            // Binary Search: prepare sorted list
            List<FoundItem> sorted =
                new ArrayList<>(items);

            long binaryStart = System.nanoTime();

            sorted.sort(
                Comparator.comparing(
                    FoundItem::getDateTime
                ).thenComparing(FoundItem::getId)
            );

            List<FoundItem> binaryResults =
                binarySearch.searchByDateRange(
                    sorted, start, end
                );

            long binaryTotal =
                System.nanoTime() - binaryStart;

            // BST: build tree and search date range
            long bstStart = System.nanoTime();

            FoundItemBST bst = new FoundItemBST();

            for (FoundItem item : items) {
                bst.insert(item);
            }

            List<FoundItem> bstResults =
                bst.rangeSearch(start, end);

            long bstTotal =
                System.nanoTime() - bstStart;

            // Verify the same items are returned
            List<Long> binaryIds =
                binaryResults.stream()
                    .map(FoundItem::getId)
                    .collect(Collectors.toList());

            List<Long> bstIds =
                bstResults.stream()
                    .map(FoundItem::getId)
                    .collect(Collectors.toList());

            assertEquals(binaryIds, bstIds);

            System.out.printf(
                "n=%d | BinarySearch=%.4f ms | BST=%.4f ms | Matches=%d%n",
                size,
                binaryTotal / 1_000_000.0,
                bstTotal / 1_000_000.0,
                bstResults.size()
            );
        }
    }
}
