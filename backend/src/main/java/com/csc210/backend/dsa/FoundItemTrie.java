package com.csc210.backend.dsa;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class FoundItemTrie {
    private static class Node { Map<Character,Node> children=new HashMap<>(); List<Long> ids=new ArrayList<>(); boolean end; }
    private final Node root=new Node();
    public void insert(String word,Long id){if(word==null||word.trim().isEmpty()||id==null)return;Node node=root;for(char c:word.trim().toLowerCase().toCharArray())node=node.children.computeIfAbsent(c,k->new Node());node.end=true;if(!node.ids.contains(id))node.ids.add(id);}
    public List<Long> search(String word){Node node=find(word);return node==null||!node.end?new ArrayList<>():new ArrayList<>(node.ids);}
    public List<Long> startsWith(String prefix){Node node=find(prefix);if(node==null)return new ArrayList<>();Set<Long> result=new HashSet<>();collect(node,result);return new ArrayList<>(result);}
    private Node find(String text){if(text==null||text.trim().isEmpty())return null;Node node=root;for(char c:text.trim().toLowerCase().toCharArray()){node=node.children.get(c);if(node==null)return null;}return node;}
    private void collect(Node node,Set<Long> result){if(node.end)result.addAll(node.ids);for(Node child:node.children.values())collect(child,result);}
}
