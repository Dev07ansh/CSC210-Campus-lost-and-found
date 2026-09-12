package com.csc210.backend.controller;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/lost-items") public class LostItemController {private final LostItemService lost;private final FoundItemService found;public LostItemController(LostItemService lost,FoundItemService found){this.lost=lost;this.found=found;}@PostMapping public LostItem create(@RequestBody LostItem item){return lost.save(item);}@GetMapping public List<LostItem> all(){return lost.all();}@GetMapping("/{id}/matches") public List<FoundItemService.FoundItemMatch> matches(@PathVariable Long id,@RequestParam(defaultValue="5") int limit){return found.matches(id,limit);}}
