package com.csc210.backend.dsa;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Classical, deterministic, non-ML text similarity strategies for matching
 * reports. Each strategy receives the same query, candidate, and fixed
 * candidate pool. Ground-truth labels never enter this API.
 */
public final class TextSimilarityStrategies {
    private TextSimilarityStrategies() {
    }

    /** Common strategy contract used by tests and the benchmark. */
    public interface Strategy {
        String id();

        /**
         * Computes a score in [0, 1]. The pool contains every candidate that
         * may be ranked for the query, including {@code candidate}.
         */
        double score(TextDocument query, TextDocument candidate, List<TextDocument> candidatePool);
    }

    /** A normalized document token list; repeated terms are retained for TF-IDF term frequency. */
    public record TextDocument(String id, List<String> tokens) {
        public TextDocument {
            id = Objects.requireNonNull(id, "id");
            tokens = tokens == null ? List.of() : List.copyOf(tokens);
        }

        public static TextDocument fromReport(String id, String itemName, String description,
                                              TextNormalizer normalizer) {
            Objects.requireNonNull(normalizer, "normalizer");
            List<String> tokens = new ArrayList<>();
            tokens.addAll(normalizer.extractKeywords(itemName));
            tokens.addAll(normalizer.extractKeywords(description));
            return new TextDocument(id, tokens);
        }
    }

    public static Strategy jaccard() {
        return JaccardStrategy.INSTANCE;
    }

    public static Strategy tfIdfCosine() {
        return TfIdfCosineStrategy.INSTANCE;
    }

    public static Strategy levenshteinTokenFuzzy() {
        return LevenshteinTokenFuzzyStrategy.INSTANCE;
    }

    public static List<Strategy> defaultStrategies() {
        return List.of(jaccard(), tfIdfCosine(), levenshteinTokenFuzzy());
    }

    /** Exact set overlap: |query ∩ candidate| / |query ∪ candidate|. */
    private enum JaccardStrategy implements Strategy {
        INSTANCE;

        @Override
        public String id() {
            return "jaccard";
        }

        @Override
        public double score(TextDocument query, TextDocument candidate, List<TextDocument> candidatePool) {
            Set<String> queryTerms = new LinkedHashSet<>(query.tokens());
            Set<String> candidateTerms = new LinkedHashSet<>(candidate.tokens());
            if (queryTerms.isEmpty() || candidateTerms.isEmpty()) {
                return 0.0;
            }
            Set<String> intersection = new HashSet<>(queryTerms);
            intersection.retainAll(candidateTerms);
            Set<String> union = new HashSet<>(queryTerms);
            union.addAll(candidateTerms);
            return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
        }
    }

    /**
     * TF-IDF cosine with normalized term frequency and smoothed inverse document frequency:
     * tf(t,d) = count(t,d) / tokenCount(d)
     * idf(t) = ln((N + 1) / (df(t) + 1)) + 1
     * weight(t,d) = tf(t,d) * idf(t)
     * cosine(q,c) = dot(q,c) / (||q|| * ||c||)
     */
    private enum TfIdfCosineStrategy implements Strategy {
        INSTANCE;

        @Override
        public String id() {
            return "tf_idf_cosine";
        }

        @Override
        public double score(TextDocument query, TextDocument candidate, List<TextDocument> candidatePool) {
            List<TextDocument> pool = stablePool(candidatePool);
            if (query.tokens().isEmpty() || candidate.tokens().isEmpty() || pool.isEmpty()) {
                return 0.0;
            }

            Map<String, Double> inverseDocumentFrequency = inverseDocumentFrequency(pool);
            double unseenTermIdf = smoothedIdf(pool.size(), 0);
            Set<String> dimensions = new TreeSet<>();
            dimensions.addAll(query.tokens());
            dimensions.addAll(candidate.tokens());
            if (dimensions.isEmpty()) {
                return 0.0;
            }

            Map<String, Double> queryVector = tfIdfVector(query.tokens(), dimensions, inverseDocumentFrequency, unseenTermIdf);
            Map<String, Double> candidateVector = tfIdfVector(candidate.tokens(), dimensions, inverseDocumentFrequency, unseenTermIdf);
            double dot = 0.0;
            double queryMagnitudeSquared = 0.0;
            double candidateMagnitudeSquared = 0.0;
            for (String term : dimensions) {
                double queryWeight = queryVector.getOrDefault(term, 0.0);
                double candidateWeight = candidateVector.getOrDefault(term, 0.0);
                dot += queryWeight * candidateWeight;
                queryMagnitudeSquared += queryWeight * queryWeight;
                candidateMagnitudeSquared += candidateWeight * candidateWeight;
            }
            if (queryMagnitudeSquared == 0.0 || candidateMagnitudeSquared == 0.0) {
                return 0.0;
            }
            return clamp(dot / Math.sqrt(queryMagnitudeSquared * candidateMagnitudeSquared));
        }

        private Map<String, Double> inverseDocumentFrequency(List<TextDocument> pool) {
            Map<String, Integer> documentFrequency = new HashMap<>();
            for (TextDocument document : pool) {
                for (String term : new HashSet<>(document.tokens())) {
                    documentFrequency.merge(term, 1, Integer::sum);
                }
            }
            int documentCount = pool.size();
            Map<String, Double> idf = new HashMap<>();
            for (Map.Entry<String, Integer> entry : documentFrequency.entrySet()) {
                idf.put(entry.getKey(), smoothedIdf(documentCount, entry.getValue()));
            }
            return idf;
        }

        private double smoothedIdf(int documentCount, int documentFrequency) {
            return Math.log((documentCount + 1.0) / (documentFrequency + 1.0)) + 1.0;
        }

        private Map<String, Double> tfIdfVector(List<String> tokens, Set<String> dimensions,
                                                 Map<String, Double> inverseDocumentFrequency,
                                                 double unseenTermIdf) {
            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokens) {
                counts.merge(token, 1, Integer::sum);
            }
            Map<String, Double> vector = new HashMap<>();
            for (String term : dimensions) {
                double termFrequency = (double) counts.getOrDefault(term, 0) / tokens.size();
                vector.put(term, termFrequency * inverseDocumentFrequency.getOrDefault(term, unseenTermIdf));
            }
            return vector;
        }
    }

    /**
     * Greedy one-to-one fuzzy token matching for typographical errors. For
     * each lexicographically sorted query token, it selects the unused
     * candidate token with greatest normalized character-edit similarity;
     * lexical order resolves equal similarities. The sum is divided by the
     * larger unique-token count.
     */
    private enum LevenshteinTokenFuzzyStrategy implements Strategy {
        INSTANCE;

        @Override
        public String id() {
            return "levenshtein_token_fuzzy";
        }

        @Override
        public double score(TextDocument query, TextDocument candidate, List<TextDocument> candidatePool) {
            List<String> queryTerms = sortedDistinct(query.tokens());
            List<String> candidateTerms = sortedDistinct(candidate.tokens());
            if (queryTerms.isEmpty() || candidateTerms.isEmpty()) {
                return 0.0;
            }

            Set<String> unusedCandidates = new LinkedHashSet<>(candidateTerms);
            double total = 0.0;
            for (String queryTerm : queryTerms) {
                String bestCandidate = null;
                double bestSimilarity = -1.0;
                for (String candidateTerm : unusedCandidates) {
                    double similarity = normalizedEditSimilarity(queryTerm, candidateTerm);
                    if (similarity > bestSimilarity
                            || (Double.compare(similarity, bestSimilarity) == 0
                            && bestCandidate != null && candidateTerm.compareTo(bestCandidate) < 0)) {
                        bestSimilarity = similarity;
                        bestCandidate = candidateTerm;
                    }
                }
                if (bestCandidate != null) {
                    total += bestSimilarity;
                    unusedCandidates.remove(bestCandidate);
                }
            }
            return clamp(total / Math.max(queryTerms.size(), candidateTerms.size()));
        }

        private double normalizedEditSimilarity(String left, String right) {
            int maximumLength = Math.max(left.length(), right.length());
            return maximumLength == 0 ? 0.0 : 1.0 - ((double) levenshteinDistance(left, right) / maximumLength);
        }

        private int levenshteinDistance(String left, String right) {
            int[] previous = new int[right.length() + 1];
            int[] current = new int[right.length() + 1];
            for (int index = 0; index <= right.length(); index++) {
                previous[index] = index;
            }
            for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
                current[0] = leftIndex;
                for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                    int substitutionCost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1) ? 0 : 1;
                    current[rightIndex] = Math.min(
                            Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                            previous[rightIndex - 1] + substitutionCost);
                }
                int[] swap = previous;
                previous = current;
                current = swap;
            }
            return previous[right.length()];
        }
    }

    private static List<TextDocument> stablePool(List<TextDocument> candidatePool) {
        if (candidatePool == null) {
            return List.of();
        }
        return candidatePool.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<String> sortedDistinct(Collection<String> tokens) {
        return tokens.stream().filter(Objects::nonNull).distinct().sorted().toList();
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
