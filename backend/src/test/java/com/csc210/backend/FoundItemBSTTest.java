
package com.csc210.backend;

import com.csc210.backend.dsa.FoundItemBST;
import com.csc210.backend.model.FoundItem;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FoundItemBSTTest {

    @Test
    void testInsertAndSearch() {

        FoundItemBST bst = new FoundItemBST();

        // Create a sample found item
        FoundItem item = mock(FoundItem.class);

        LocalDateTime date =
            LocalDateTime.of(2026, 10, 15, 10, 30);

        when(item.getId()).thenReturn(1L);
        when(item.getDateTime()).thenReturn(date);

        // Insert into BST
        bst.insert(item);

        // Search for the inserted item
        FoundItem result = bst.search(date, 1L);

        // Verify the correct item was found
        assertSame(item, result);
    }

    @Test
    void testDuplicateDates() {

        FoundItemBST bst = new FoundItemBST();

        LocalDateTime date =
            LocalDateTime.of(2026, 10, 15, 10, 30);

        FoundItem item1 = mock(FoundItem.class);
        when(item1.getId()).thenReturn(1L);
        when(item1.getDateTime()).thenReturn(date);

        FoundItem item2 = mock(FoundItem.class);
        when(item2.getId()).thenReturn(2L);
        when(item2.getDateTime()).thenReturn(date);

        bst.insert(item1);
        bst.insert(item2);

        assertSame(item1, bst.search(date, 1L));
        assertSame(item2, bst.search(date, 2L));
    }

    @Test
    void testDateRangeSearch() {

        FoundItemBST bst = new FoundItemBST();

        LocalDateTime d1 =
            LocalDateTime.of(2026, 10, 5, 10, 0);
        LocalDateTime d2 =
            LocalDateTime.of(2026, 10, 10, 10, 0);
        LocalDateTime d3 =
            LocalDateTime.of(2026, 10, 15, 10, 0);
        LocalDateTime d4 =
            LocalDateTime.of(2026, 10, 20, 10, 0);

        LocalDateTime[] dates = {d1, d2, d3, d4};

        for (int i = 0; i < dates.length; i++) {
            FoundItem item = mock(FoundItem.class);
            when(item.getId()).thenReturn((long) i + 1);
            when(item.getDateTime()).thenReturn(dates[i]);
            bst.insert(item);
        }

        var results = bst.rangeSearch(d2, d3);

        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).getId());
        assertEquals(3L, results.get(1).getId());
    }

    @Test
    void testDeletion() {

        FoundItemBST bst = new FoundItemBST();

        LocalDateTime date =
            LocalDateTime.of(2026, 10, 15, 10, 30);

        // Create three items with the same date
        for (long id = 1; id <= 3; id++) {
            FoundItem item = mock(FoundItem.class);
            when(item.getId()).thenReturn(id);
            when(item.getDateTime()).thenReturn(date);
            bst.insert(item);
        }

        // Delete item 2
        bst.delete(date, 2L);

        // Item 2 should no longer exist
        assertNull(bst.search(date, 2L));

        // Other items should remain
        assertNotNull(bst.search(date, 1L));
        assertNotNull(bst.search(date, 3L));
    }

    @Test
    void testDeleteNodeWithTwoChildren() {

        FoundItemBST bst = new FoundItemBST();

        LocalDateTime date =
            LocalDateTime.of(2026, 10, 15, 10, 30);

        long[] ids = {5, 3, 7, 2, 4, 6, 8};

        for (long id : ids) {
            FoundItem item = mock(FoundItem.class);
            when(item.getId()).thenReturn(id);
            when(item.getDateTime()).thenReturn(date);
            bst.insert(item);
        }

        // Root (ID 5) has two children
        bst.delete(date, 5L);

        // Deleted item must be gone
        assertNull(bst.search(date, 5L));

        // All other items must remain
        for (long id : new long[]{2, 3, 4, 6, 7, 8}) {
            assertNotNull(bst.search(date, id));
        }

        // All six remaining items must appear
        assertEquals(6, bst.rangeSearch(date, date).size());
    }


}
