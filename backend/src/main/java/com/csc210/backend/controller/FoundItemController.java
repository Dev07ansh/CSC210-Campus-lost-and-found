package com.csc210.backend.controller;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.service.FoundItemService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
@RestController @RequestMapping("/api/found-items") public class FoundItemController {private final FoundItemService service;public FoundItemController(FoundItemService service){this.service=service;}@PostMapping public FoundItem create(@RequestBody FoundItem item){return service.save(item);}@GetMapping public List<FoundItem> all(){return service.all();}@GetMapping("/category/{category}") public List<FoundItem> category(@PathVariable String category){return service.category(category);}@GetMapping("/search") public List<FoundItem> search(@RequestParam String query,@RequestParam(defaultValue="true") boolean prefix){return service.search(query,prefix);}@GetMapping("/date-range") public List<FoundItem> dateRange(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime end){return service.dateRange(start,end);}}
