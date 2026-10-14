package com.csc210.backend.service;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.model.LostItemSecret;
import com.csc210.backend.model.User;
import com.csc210.backend.repository.LostItemRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
@Service public class LostItemService {
    private final LostItemRepository repository;
    public LostItemService(LostItemRepository repository){this.repository=repository;}
    @Transactional public LostItem create(LostItem item, LostItemSecret secret, User reporter){
        item.setReporter(reporter);
        item.setStatus("Active");
        item.setSecret(secret);
        return repository.save(item);
    }
    public LostItem save(LostItem item){return repository.save(item);}
    public List<LostItem> all(){return repository.findAll();}
    public Optional<LostItem> find(Long id){return repository.findById(id);}
}
