package com.csc210.backend;
import com.csc210.backend.dsa.TextNormalizer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
class TextNormalizerTest {@Test void normalizesKeywords(){assertEquals(java.util.List.of("black","leather","wallet"),new TextNormalizer().extractKeywords("Black Leather-Wallet!"));}}
