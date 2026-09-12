package com.csc210.backend.dsa;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class TextNormalizer {
    public List<String> extractKeywords(String text) {
        if (text == null || text.trim().isEmpty()) return new ArrayList<>();
        String normalized = text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        return normalized.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(normalized.split("\\s+")));
    }
}
