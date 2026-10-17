package com.csc210.backend.controller;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.service.CurrentUserService;
import com.csc210.backend.service.FoundItemService;
import com.csc210.backend.service.ImageStorageService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/found-items")
public class FoundItemController {
    private final FoundItemService service;
    private final ImageStorageService images;
    private final CurrentUserService currentUsers;

    public FoundItemController(FoundItemService service, ImageStorageService images, CurrentUserService currentUsers) { this.service = service; this.images = images; this.currentUsers = currentUsers; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public FoundItem create(@RequestHeader(value = "X-User-Email", required = false) String userEmail,
                            @RequestBody FoundItemCreateRequest request) {
        FoundItem item = item(request);
        if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An image is required for a found-item report.");
        }
        return service.create(item, currentUsers.requireUser(userEmail));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FoundItem createWithImage(@RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                     @RequestPart("item") FoundItemCreateRequest request,
                                     @RequestPart(value = "image", required = false) MultipartFile image) {
        FoundItem item = item(request);
        // Reject an invalid desk before writing an otherwise orphaned upload.
        service.validateForCreate(item);
        item.setImageUrl(images.store(image, true));
        return service.create(item, currentUsers.requireUser(userEmail));
    }

    @GetMapping public List<FoundItem> all(){return service.all();}
    @GetMapping("/category/{category}") public List<FoundItem> category(@PathVariable String category){return service.category(category);}
    @GetMapping("/search") public List<FoundItem> search(@RequestParam String query,@RequestParam(defaultValue="true") boolean prefix){return service.search(query,prefix);}
    @GetMapping("/date-range") public List<FoundItem> dateRange(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end){return service.dateRange(start,end);}

    private FoundItem item(FoundItemCreateRequest request) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Found-item report details are required.");
        FoundItem item = new FoundItem();
        item.setItemName(request.itemName());
        item.setCategory(request.category());
        item.setColor(request.color());
        item.setDescription(request.description());
        item.setLocation(request.location());
        item.setImageUrl(request.imageUrl());
        item.setDateTime(request.dateTime());
        item.setCollectionDesk(request.collectionDesk());
        return item;
    }

    /** Prevents clients from assigning report status or a different reporter. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FoundItemCreateRequest(String itemName, String category, String color, String description,
                                         String location, String imageUrl, LocalDateTime dateTime,
                                         String collectionDesk) {}
}
