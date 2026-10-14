package com.csc210.backend.controller;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.model.LostItemSecret;
import com.csc210.backend.model.User;
import com.csc210.backend.repository.LostItemSecretRepository;
import com.csc210.backend.service.CurrentUserService;
import com.csc210.backend.service.FoundItemService;
import com.csc210.backend.service.ImageStorageService;
import com.csc210.backend.service.LostItemService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {
    private final LostItemService lost;
    private final FoundItemService found;
    private final ImageStorageService images;
    private final CurrentUserService currentUsers;
    private final LostItemSecretRepository secrets;

    public LostItemController(LostItemService lost, FoundItemService found, ImageStorageService images,
                              CurrentUserService currentUsers, LostItemSecretRepository secrets) {
        this.lost = lost; this.found = found; this.images = images; this.currentUsers = currentUsers; this.secrets = secrets;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public LostItem create(@RequestHeader(value = "X-User-Email", required = false) String userEmail,
                           @RequestBody LostItemCreateRequest request) {
        User reporter = currentUsers.requireUser(userEmail);
        return lost.create(item(request), secret(request), reporter);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LostItem createWithImage(@RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                    @RequestPart("item") LostItemCreateRequest request,
                                    @RequestPart(value = "image", required = false) MultipartFile image) {
        User reporter = currentUsers.requireUser(userEmail);
        LostItem item = item(request);
        item.setImageUrl(images.store(image, false));
        return lost.create(item, secret(request), reporter);
    }

    @GetMapping public List<LostItem> all(){return lost.all();}
    @GetMapping("/{id}/matches")
    public List<FoundItemService.FoundItemMatch> matches(@PathVariable Long id,
                                                           @RequestParam(defaultValue="5") int limit,
                                                           @RequestHeader(value = "X-User-Email", required = false) String userEmail){
        boolean canCreateClaim = false;
        User currentUser = null;
        if (userEmail != null && !userEmail.isBlank()) {
            currentUser = currentUsers.requireUser(userEmail);
            User viewer = currentUser;
            canCreateClaim = lost.find(id).map(item -> currentUsers.same(viewer, item.getReporter())
                    && isActive(item.getStatus()) && hasCompleteSecret(item.getId())).orElse(false);
        }
        final boolean permitted = canCreateClaim;
        final User viewer = currentUser;
        return found.matches(id, limit).stream()
                .map(match -> new FoundItemService.FoundItemMatch(match.item(), match.score(),
                        permitted && match.item().getReporter() != null && !currentUsers.same(viewer, match.item().getReporter())))
                .toList();
    }

    private LostItem item(LostItemCreateRequest request) {
        if (request == null) throw badRequest("Lost-item report details are required.");
        LostItem item = new LostItem();
        item.setItemName(request.itemName());
        item.setCategory(request.category());
        item.setColor(request.color());
        item.setDescription(request.description());
        item.setLocation(request.location());
        item.setImageUrl(request.imageUrl());
        item.setDateTime(request.dateTime());
        return item;
    }

    private LostItemSecret secret(LostItemCreateRequest request) {
        if (request == null) throw badRequest("Lost-item report details are required.");
        LostItemSecret secret = new LostItemSecret(request.interiorColorOrLining(), request.uniqueMarks(), request.contents());
        if (!secret.isComplete()) throw badRequest("Complete all three private verification details for a lost-item report.");
        return secret;
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private boolean isActive(String status) { return status != null && "active".equalsIgnoreCase(status); }
    private boolean hasCompleteSecret(Long lostItemId) {
        return lostItemId != null && secrets.findByLostItemId(lostItemId).map(LostItemSecret::isComplete).orElse(false);
    }

    /** The private fields are accepted at write time but persist only in LostItemSecret. */
    public record LostItemCreateRequest(String itemName, String category, String color, String description, String location,
                                        String imageUrl, LocalDateTime dateTime, String interiorColorOrLining,
                                        String uniqueMarks, String contents) {}
}
