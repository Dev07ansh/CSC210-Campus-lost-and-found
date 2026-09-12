package com.csc210.backend.dsa;

import com.csc210.backend.model.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SimilarityScorer {
    public double score(LostItem lost,FoundItem found){if(lost==null||found==null)return 0;return .35*equals(lost.getColor(),found.getColor())+.30*equals(lost.getLocation(),found.getLocation())+.35*keywords(lost,found);}
    private double equals(String left,String right){return left!=null&&right!=null&&left.trim().equalsIgnoreCase(right.trim())?1:0;}
    private double keywords(LostItem lost,FoundItem found){Set<String> a=words(lost.getItemName(),lost.getDescription()),b=words(found.getItemName(),found.getDescription());if(a.isEmpty()||b.isEmpty())return 0;Set<String> common=new HashSet<>(a);common.retainAll(b);Set<String> all=new HashSet<>(a);all.addAll(b);return (double)common.size()/all.size();}
    private Set<String> words(String... values){Set<String> result=new HashSet<>();for(String value:values)if(value!=null)for(String word:value.toLowerCase().replaceAll("[^a-z0-9\\s]"," ").split("\\s+"))if(!word.isEmpty())result.add(word);return result;}
}
