package com.csc210.backend.service;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.repository.LostItemRepository;
import org.springframework.stereotype.Service;
import java.util.List;
@Service public class LostItemService {private final LostItemRepository repository;public LostItemService(LostItemRepository repository){this.repository=repository;}public LostItem save(LostItem item){return repository.save(item);}public List<LostItem> all(){return repository.findAll();}}
