package com.csc210.backend.controller;
import com.csc210.backend.model.FoundItem;
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

    public FoundItemController(FoundItemService service, ImageStorageService images) { this.service = service; this.images = images; }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public FoundItem create(@RequestBody FoundItem item) {
        if (item.getImageUrl() == null || item.getImageUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An image is required for a found-item report.");
        }
        return service.save(item);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FoundItem createWithImage(@RequestPart("item") FoundItem item, @RequestPart(value = "image", required = false) MultipartFile image) {
        item.setImageUrl(images.store(image, true));
        return service.save(item);
    }

    @GetMapping public List<FoundItem> all(){return service.all();}
    @GetMapping("/category/{category}") public List<FoundItem> category(@PathVariable String category){return service.category(category);}
    @GetMapping("/search") public List<FoundItem> search(@RequestParam String query,@RequestParam(defaultValue="true") boolean prefix){return service.search(query,prefix);}
    @GetMapping("/date-range") public List<FoundItem> dateRange(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end){return service.dateRange(start,end);}
}
