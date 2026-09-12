package com.csc210.backend.dsa;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FoundItemHashTable {
    private static final int SIZE=257;
    private final List<Entry>[] table;
    private static class Entry {String category;List<Long> ids=new ArrayList<>();Entry(String category){this.category=category;}}
    @SuppressWarnings("unchecked") public FoundItemHashTable(){table=new ArrayList[SIZE];for(int i=0;i<SIZE;i++)table[i]=new ArrayList<>();}
    private int hash(String category){return Math.floorMod(category.toLowerCase().hashCode(),SIZE);}
    public void insert(String category,Long id){if(category==null||category.trim().isEmpty()||id==null)return;String key=category.trim().toLowerCase();for(Entry entry:table[hash(key)])if(entry.category.equals(key)){if(!entry.ids.contains(id))entry.ids.add(id);return;}Entry entry=new Entry(key);entry.ids.add(id);table[hash(key)].add(entry);}
    public List<Long> search(String category){if(category==null||category.trim().isEmpty())return new ArrayList<>();String key=category.trim().toLowerCase();for(Entry entry:table[hash(key)])if(entry.category.equals(key))return new ArrayList<>(entry.ids);return new ArrayList<>();}
}
