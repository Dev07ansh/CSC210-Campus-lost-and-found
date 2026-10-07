package com.csc210.backend.controller;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.service.FoundItemService;
import com.csc210.backend.service.ImageStorageService;
import com.csc210.backend.service.LostItemService;
import com.csc210.backend.service.OwnershipVerificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {
    private final LostItemService lost;
    private final FoundItemService found;
    private final ImageStorageService images;
    private final OwnershipVerificationService ownershipVerification;

    public LostItemController(LostItemService lost, FoundItemService found, ImageStorageService images, OwnershipVerificationService ownershipVerification) {
        this.lost = lost; this.found = found; this.images = images; this.ownershipVerification = ownershipVerification;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public LostItem create(@RequestBody LostItem item) { validateVerificationDetails(item); return lost.save(item); }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public LostItem createWithImage(@RequestPart("item") LostItem item, @RequestPart(value = "image", required = false) MultipartFile image) {
        validateVerificationDetails(item);
        item.setImageUrl(images.store(image, false));
        return lost.save(item);
    }

    @GetMapping public List<LostItem> all(){return lost.all();}
    @GetMapping("/{id}/matches") public List<FoundItemService.FoundItemMatch> matches(@PathVariable Long id,@RequestParam(defaultValue="5") int limit){return found.matches(id,limit);}
    @PostMapping("/{id}/verify-ownership")
    public OwnershipVerificationService.VerificationResult verifyOwnership(@PathVariable Long id, @RequestBody OwnershipVerificationService.VerificationRequest request) {
        return ownershipVerification.verify(id, request);
    }

    private void validateVerificationDetails(LostItem item) {
        if (!meaningful(item.getInteriorColorOrLining()) || !meaningful(item.getUniqueMarks()) || !meaningful(item.getContents())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Complete all three private verification details for a lost-item report.");
        }
    }

    private boolean meaningful(String value) { return value != null && value.matches(".*[\\p{L}\\p{N}].*"); }
}
