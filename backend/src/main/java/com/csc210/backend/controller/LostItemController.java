package com.csc210.backend.controller;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.service.FoundItemService;
import com.csc210.backend.service.ImageStorageService;
import com.csc210.backend.service.LostItemService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {
    private final LostItemService lost;
    private final FoundItemService found;
    private final ImageStorageService images;

    public LostItemController(LostItemService lost, FoundItemService found, ImageStorageService images) {
        this.lost = lost; this.found = found; this.images = images;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public LostItem create(@RequestBody LostItem item) { return lost.save(item); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LostItem createWithImage(@RequestPart("item") LostItem item, @RequestPart(value = "image", required = false) MultipartFile image) {
        item.setImageUrl(images.store(image, false));
        return lost.save(item);
    }

    @GetMapping public List<LostItem> all(){return lost.all();}
    @GetMapping("/{id}/matches") public List<FoundItemService.FoundItemMatch> matches(@PathVariable Long id,@RequestParam(defaultValue="5") int limit){return found.matches(id,limit);}
}
