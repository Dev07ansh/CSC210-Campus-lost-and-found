package com.csc210.backend;

import com.csc210.backend.dsa.FoundItemBinarySearch;
import com.csc210.backend.dsa.FoundItemHashTable;
import com.csc210.backend.dsa.FoundItemTrie;
import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.dsa.TextNormalizer;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.repository.FoundItemRepository;
import com.csc210.backend.repository.LostItemRepository;
import com.csc210.backend.service.FoundItemService;
import com.csc210.backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FoundItemCollectionDeskTest {

    private final FoundItemService service = new FoundItemService(
            mock(FoundItemRepository.class), mock(LostItemRepository.class),
            new FoundItemHashTable(), new FoundItemTrie(), new FoundItemBinarySearch(),
            new TextNormalizer(), new SimilarityScorer(), mock(NotificationService.class));

    @Test
    void acceptsOnlyTheTwoCanonicalCollectionDesks() {
        FoundItem item = new FoundItem();
        item.setCollectionDesk("  Gate 12  ");
        service.validateForCreate(item);
        assertEquals("Gate 12", item.getCollectionDesk());

        item.setCollectionDesk("University Centre Reception");
        service.validateForCreate(item);
        assertEquals("University Centre Reception", item.getCollectionDesk());

        item.setCollectionDesk("Library reception");
        ResponseStatusException unsupported = assertThrows(ResponseStatusException.class,
                () -> service.validateForCreate(item));
        assertEquals(HttpStatus.BAD_REQUEST, unsupported.getStatusCode());

        item.setCollectionDesk(null);
        ResponseStatusException missing = assertThrows(ResponseStatusException.class,
                () -> service.validateForCreate(item));
        assertEquals(HttpStatus.BAD_REQUEST, missing.getStatusCode());
    }
}
