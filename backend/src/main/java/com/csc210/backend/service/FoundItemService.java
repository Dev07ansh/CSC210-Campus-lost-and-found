package com.csc210.backend.service;

import com.csc210.backend.dsa.*;
import com.csc210.backend.model.*;
import com.csc210.backend.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FoundItemService {
    private final FoundItemRepository foundRepo; private final LostItemRepository lostRepo; private final FoundItemHashTable hash; private final FoundItemTrie trie; private final FoundItemBinarySearch binary; private final TextNormalizer normalizer; private final SimilarityScorer scorer; private final NotificationService notifications;
    public FoundItemService(FoundItemRepository foundRepo,LostItemRepository lostRepo,FoundItemHashTable hash,FoundItemTrie trie,FoundItemBinarySearch binary,TextNormalizer normalizer,SimilarityScorer scorer,NotificationService notifications){this.foundRepo=foundRepo;this.lostRepo=lostRepo;this.hash=hash;this.trie=trie;this.binary=binary;this.normalizer=normalizer;this.scorer=scorer;this.notifications=notifications;}
    @PostConstruct void indexExisting(){foundRepo.findAll().forEach(this::index);}
    public FoundItem save(FoundItem item){FoundItem saved=foundRepo.save(item);index(saved);return saved;}

    /** Validates and normalizes the desk before a file is stored or a report is saved. */
    public void validateForCreate(FoundItem item){
        if(item==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Found-item report details are required.");
        String desk=CollectionDesk.normalize(item.getCollectionDesk());
        if(desk==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Select University Centre Reception or Gate 12 as the collection desk.");
        item.setCollectionDesk(desk);
    }
    @Transactional public FoundItem create(FoundItem item, User reporter){
        validateForCreate(item);
        item.setReporter(reporter);
        item.setStatus("Active");
        FoundItem saved=save(item);
        notifications.reportSubmitted(reporter, false, saved.getItemName());
        notifyPossibleMatches(saved);
        return saved;
    }
    /** A newly submitted found report is the trigger for owner-facing candidate alerts. */
    private void notifyPossibleMatches(FoundItem found){
        if(found.getReporter()==null)return;
        for(LostItem lost:lostRepo.findAll()){
            if(!isActive(lost.getStatus())||lost.getReporter()==null||sameUser(lost.getReporter(),found.getReporter()))continue;
            double score=scorer.score(lost,found);
            if(score>=0.60)notifications.possibleMatch(lost.getReporter(),lost.getId(),found.getId(),lost.getItemName(),score);
        }
    }
    private boolean sameUser(User first,User second){
        if(first==null||second==null)return false;
        if(first.getId()!=null&&second.getId()!=null)return first.getId().equals(second.getId());
        return first.getEmail()!=null&&first.getEmail().equalsIgnoreCase(second.getEmail());
    }
    private void index(FoundItem item){hash.insert(item.getCategory(),item.getId());for(String word:normalizer.extractKeywords(item.getItemName()))trie.insert(word,item.getId());for(String word:normalizer.extractKeywords(item.getDescription()))trie.insert(word,item.getId());}
    public List<FoundItem> all(){return foundRepo.findAll();}
    public List<FoundItem> category(String category){return foundRepo.findAllById(hash.search(category));}
    public List<FoundItem> dateRange(LocalDateTime start,LocalDateTime end){List<FoundItem> values=new ArrayList<>();for(FoundItem item:foundRepo.findAll())if(item.getDateTime()!=null)values.add(item);values.sort(Comparator.comparing(FoundItem::getDateTime));return binary.searchByDateRange(values,start,end);}
    public List<FoundItem> search(String query,boolean prefix){List<String> words=normalizer.extractKeywords(query);if(words.isEmpty())return List.of();List<Long> ids=null;for(String word:words){List<Long> next=prefix?trie.startsWith(word):trie.search(word);if(ids==null)ids=new ArrayList<>(next);else ids.retainAll(next);if(ids.isEmpty())return List.of();}return foundRepo.findAllById(ids);}
    public List<FoundItemMatch> matches(Long lostId,int limit){LostItem lost=lostRepo.findById(lostId).orElseThrow(()->new IllegalArgumentException("Lost item not found"));MaxHeap<Ranked> heap=new MaxHeap<>();for(FoundItem item:foundRepo.findAll())if(isActive(item.getStatus()))heap.offer(new Ranked(item,scorer.score(lost,item)));List<FoundItemMatch> result=new ArrayList<>();while(!heap.isEmpty()&&result.size()<Math.max(1,Math.min(limit,20))){Ranked ranked=heap.poll();result.add(new FoundItemMatch(ranked.item,ranked.score,false,CollectionDesk.isSupported(ranked.item.getCollectionDesk())));}return result;}
    /** The desk label stays private; this flag only lets the UI explain why an older report cannot be claimed. */
    public record FoundItemMatch(FoundItem item,double score,boolean canCreateClaim,boolean hasCollectionDesk){} private static class Ranked implements Comparable<Ranked>{FoundItem item;double score;Ranked(FoundItem item,double score){this.item=item;this.score=score;}public int compareTo(Ranked other){return Double.compare(score,other.score);}}
    private boolean isActive(String status){return status!=null&&"active".equalsIgnoreCase(status);}
}
