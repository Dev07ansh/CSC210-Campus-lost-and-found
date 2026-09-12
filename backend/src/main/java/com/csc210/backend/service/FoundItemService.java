package com.csc210.backend.service;

import com.csc210.backend.dsa.*;
import com.csc210.backend.model.*;
import com.csc210.backend.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class FoundItemService {
    private final FoundItemRepository foundRepo; private final LostItemRepository lostRepo; private final FoundItemHashTable hash; private final FoundItemTrie trie; private final FoundItemBinarySearch binary; private final TextNormalizer normalizer; private final SimilarityScorer scorer;
    public FoundItemService(FoundItemRepository foundRepo,LostItemRepository lostRepo,FoundItemHashTable hash,FoundItemTrie trie,FoundItemBinarySearch binary,TextNormalizer normalizer,SimilarityScorer scorer){this.foundRepo=foundRepo;this.lostRepo=lostRepo;this.hash=hash;this.trie=trie;this.binary=binary;this.normalizer=normalizer;this.scorer=scorer;}
    @PostConstruct void indexExisting(){foundRepo.findAll().forEach(this::index);}
    public FoundItem save(FoundItem item){FoundItem saved=foundRepo.save(item);index(saved);return saved;}
    private void index(FoundItem item){hash.insert(item.getCategory(),item.getId());for(String word:normalizer.extractKeywords(item.getItemName()))trie.insert(word,item.getId());for(String word:normalizer.extractKeywords(item.getDescription()))trie.insert(word,item.getId());}
    public List<FoundItem> all(){return foundRepo.findAll();}
    public List<FoundItem> category(String category){return foundRepo.findAllById(hash.search(category));}
    public List<FoundItem> dateRange(LocalDateTime start,LocalDateTime end){List<FoundItem> values=new ArrayList<>();for(FoundItem item:foundRepo.findAll())if(item.getDateTime()!=null)values.add(item);values.sort(Comparator.comparing(FoundItem::getDateTime));return binary.searchByDateRange(values,start,end);}
    public List<FoundItem> search(String query,boolean prefix){List<String> words=normalizer.extractKeywords(query);if(words.isEmpty())return List.of();List<Long> ids=null;for(String word:words){List<Long> next=prefix?trie.startsWith(word):trie.search(word);if(ids==null)ids=new ArrayList<>(next);else ids.retainAll(next);if(ids.isEmpty())return List.of();}return foundRepo.findAllById(ids);}
    public List<FoundItemMatch> matches(Long lostId,int limit){LostItem lost=lostRepo.findById(lostId).orElseThrow(()->new IllegalArgumentException("Lost item not found"));MaxHeap<Ranked> heap=new MaxHeap<>();for(FoundItem item:foundRepo.findAll())heap.offer(new Ranked(item,scorer.score(lost,item)));List<FoundItemMatch> result=new ArrayList<>();while(!heap.isEmpty()&&result.size()<Math.max(1,Math.min(limit,20))){Ranked ranked=heap.poll();result.add(new FoundItemMatch(ranked.item,ranked.score));}return result;}
    public record FoundItemMatch(FoundItem item,double score){} private static class Ranked implements Comparable<Ranked>{FoundItem item;double score;Ranked(FoundItem item,double score){this.item=item;this.score=score;}public int compareTo(Ranked other){return Double.compare(score,other.score);}}
}
