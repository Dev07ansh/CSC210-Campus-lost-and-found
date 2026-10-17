package com.csc210.backend.service;
import com.csc210.backend.model.LostItem;
import com.csc210.backend.model.LostItemSecret;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.model.User;
import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.repository.FoundItemRepository;
import com.csc210.backend.repository.LostItemRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
@Service public class LostItemService {
    private final LostItemRepository repository;
    private final FoundItemRepository foundItems;
    private final SimilarityScorer similarityScorer;
    private final NotificationService notifications;
    public LostItemService(LostItemRepository repository, FoundItemRepository foundItems,
                           SimilarityScorer similarityScorer, NotificationService notifications){
        this.repository=repository;
        this.foundItems=foundItems;
        this.similarityScorer=similarityScorer;
        this.notifications=notifications;
    }
    @Transactional public LostItem create(LostItem item, LostItemSecret secret, User reporter){
        item.setReporter(reporter);
        item.setStatus("Active");
        item.setSecret(secret);
        LostItem saved = repository.save(item);
        notifications.reportSubmitted(reporter, true, saved.getItemName());
        notifyPossibleMatches(saved);
        return saved;
    }
    public LostItem save(LostItem item){return repository.save(item);}
    public List<LostItem> all(){return repository.findAll();}
    public Optional<LostItem> find(Long id){return repository.findById(id);}
    /** Lets a new lost report alert its owner when an already-reported found item is a strong candidate. */
    private void notifyPossibleMatches(LostItem lost){
        if(lost.getReporter()==null)return;
        for(FoundItem found:foundItems.findAll()){
            if(!isActive(found.getStatus())||found.getReporter()==null||sameUser(lost.getReporter(),found.getReporter()))continue;
            double score=similarityScorer.score(lost,found);
            if(score>=0.60)notifications.possibleMatch(lost.getReporter(),lost.getId(),found.getId(),lost.getItemName(),score);
        }
    }
    private boolean isActive(String status){return status!=null&&"active".equalsIgnoreCase(status);}
    private boolean sameUser(User first,User second){
        if(first==null||second==null)return false;
        if(first.getId()!=null&&second.getId()!=null)return first.getId().equals(second.getId());
        return first.getEmail()!=null&&first.getEmail().equalsIgnoreCase(second.getEmail());
    }
}
