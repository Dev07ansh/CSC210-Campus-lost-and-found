package com.csc210.backend;
import com.csc210.backend.dsa.FoundItemTrie;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FoundItemTrieTest {@Test void findsPrefix(){FoundItemTrie trie=new FoundItemTrie();trie.insert("wallet",17L);trie.insert("watch",23L);assertEquals(2,trie.startsWith("wa").size());}}
