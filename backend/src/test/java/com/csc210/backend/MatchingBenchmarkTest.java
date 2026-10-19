package com.csc210.backend;

import com.csc210.backend.dsa.FoundItemHashTable;
import com.csc210.backend.dsa.FoundItemTrie;
import com.csc210.backend.dsa.SimilarityScorer;
import com.csc210.backend.dsa.TextNormalizer;
import com.csc210.backend.dsa.TextSimilarityStrategies;
import com.csc210.backend.model.FoundItem;
import com.csc210.backend.model.LostItem;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproducible offline retrieval evaluation. It intentionally uses fixed CSV
 * reports and separately loaded labels; neither a ranker nor an index receives
 * a ground-truth label.
 */
class MatchingBenchmarkTest {
    private static final double THRESHOLD = 0.60;
    private static final long BENCHMARK_SEED = 20261019L;
    private static final int WARMUP_ITERATIONS = 5;
    private static final int MEASURED_ITERATIONS = 15;
    private static volatile long runtimeSink;
    private static final List<WeightConfig> WEIGHT_SWEEP = List.of(
            new WeightConfig("35_30_35", new SimilarityScorer.ScoreWeights(0.35, 0.30, 0.35)),
            new WeightConfig("25_25_50", new SimilarityScorer.ScoreWeights(0.25, 0.25, 0.50)),
            new WeightConfig("20_30_50", new SimilarityScorer.ScoreWeights(0.20, 0.30, 0.50)));

    private final TextNormalizer normalizer = new TextNormalizer();
    private final SimilarityScorer scorer = new SimilarityScorer(normalizer);

    @Test
    void runsFixedBenchmarkAndWritesCsvResults() throws Exception {
        BenchmarkDataset dataset = loadDataset();
        List<BenchmarkMethod> methods = methods(dataset);
        List<SummaryRow> summaries = new ArrayList<>();
        List<QueryRow> queryRows = new ArrayList<>();

        for (BenchmarkMethod method : methods) {
            List<WeightConfig> weights = method.usesWeightSweep() ? WEIGHT_SWEEP : List.of(WEIGHT_SWEEP.getFirst());
            for (WeightConfig weight : weights) {
                Evaluation evaluation = evaluate(method, dataset, weight);
                RuntimeStats runtime = time(method, dataset, weight);
                summaries.add(SummaryRow.from(method, weight, evaluation.metrics(), runtime));
                queryRows.addAll(QueryRow.from(method, weight, evaluation.outcomes()));
                System.out.printf(Locale.ROOT,
                        "P2.4 %s weights=%s P=%.6f R=%.6f F1=%.6f H@1=%.6f H@3=%.6f H@5=%.6f examined=%.2f median_ns=%d spread_ns=%d%n",
                        method.id(), weight.id(), evaluation.metrics().precision(), evaluation.metrics().recall(),
                        evaluation.metrics().f1(), evaluation.metrics().hitAt1(), evaluation.metrics().hitAt3(),
                        evaluation.metrics().hitAt5(), evaluation.metrics().averageCandidatesExamined(),
                        runtime.medianNs(), runtime.p90Ns() - runtime.p10Ns());
            }
        }

        Path resultsDirectory = resultsDirectory();
        Files.createDirectories(resultsDirectory);
        writeSummaryCsv(resultsDirectory.resolve("matching_benchmark_summary.csv"), summaries);
        writeQueryCsv(resultsDirectory.resolve("matching_benchmark_queries.csv"), queryRows);
        writeSvgPlot(resultsDirectory.resolve("matching_benchmark_plot.svg"), summaries);

        assertEquals(13, summaries.size(), "exact filtering plus four weighted methods across three weights");
        assertEquals(260, queryRows.size(), "13 configurations x 20 fixed queries");
        assertTrue(Files.size(resultsDirectory.resolve("matching_benchmark_summary.csv")) > 0);
        assertTrue(Files.size(resultsDirectory.resolve("matching_benchmark_queries.csv")) > 0);
        assertTrue(Files.size(resultsDirectory.resolve("matching_benchmark_plot.svg")) > 0);
    }

    @Test
    void metricsCountNoMatchFalsePositivesAndExcludeNoMatchesFromHitRates() {
        List<QueryOutcome> outcomes = List.of(
                new QueryOutcome("matching", 1, 1, 8, 1, 0, 0, true, true, true),
                new QueryOutcome("no-match", 0, 1, 8, 0, 1, 0, false, false, false));

        Metrics metrics = Metrics.from(outcomes);
        // TP=1, FP=1, FN=0: precision=1/(1+1)=0.5, recall=1/(1+0)=1,
        // F1=2(0.5)(1)/(0.5+1)=2/3. Only the one matching query is in Hit@k's denominator.
        assertEquals(0.5, metrics.precision(), 1e-12);
        assertEquals(1.0, metrics.recall(), 1e-12);
        assertEquals(2.0 / 3.0, metrics.f1(), 1e-12);
        assertEquals(1.0, metrics.hitAt1(), 1e-12);
        assertEquals(1, metrics.matchQueryCount());
        assertEquals(1, metrics.noMatchQueryCount());
    }

    @Test
    void weightedBaselineCountsTheControlledNoMatchAsAFalsePositive() throws Exception {
        BenchmarkDataset dataset = loadDataset();
        BenchmarkMethod baseline = methods(dataset).stream()
                .filter(method -> method.id().equals("weighted_full_scan"))
                .findFirst()
                .orElseThrow();
        QueryOutcome noMatch = evaluate(baseline, dataset, WEIGHT_SWEEP.getFirst()).outcomes().stream()
                .filter(outcome -> outcome.queryId().equals("L20"))
                .findFirst()
                .orElseThrow();

        // L20/F77: color=1 and location=1. Text sets intersect on {green,notebook}: 2 tokens.
        // Their union has 11 tokens, so score = .35 + .30 + .35(2/11) = .713636..., above .60.
        // L20 has no true candidate, so that accepted candidate must be one false positive, not a hit.
        assertEquals(1, noMatch.acceptedCount());
        assertEquals(0, noMatch.truePositives());
        assertEquals(1, noMatch.falsePositives());
        assertEquals(0, noMatch.falseNegatives());
        assertFalse(noMatch.hitAt1());
    }

    @Test
    void indexedJaccardRankingPreservesFullScanScoresAndCandidateIdTies() throws Exception {
        BenchmarkDataset dataset = loadDataset();
        for (WeightConfig weight : WEIGHT_SWEEP) {
            for (Query query : dataset.queries()) {
                List<ScoredCandidate> fullCategoryRanking = weightedFullScan(query, dataset, weight.weights()).ranked().stream()
                        .filter(candidate -> scorer.sameMeaningfulCategory(query.item(), candidate.candidate().item()))
                        .toList();
                List<ScoredCandidate> indexedRanking = indexedWeightedRanking(query, dataset, weight.weights()).ranked();

                assertEquals(fullCategoryRanking.size(), indexedRanking.size());
                for (int index = 0; index < indexedRanking.size(); index++) {
                    // The index changes only candidate access: exact scores and the ID secondary key must remain equal.
                    assertEquals(fullCategoryRanking.get(index).candidate().id(), indexedRanking.get(index).candidate().id());
                    assertEquals(fullCategoryRanking.get(index).score(), indexedRanking.get(index).score(), 1e-12);
                }
            }
        }
    }

    private List<BenchmarkMethod> methods(BenchmarkDataset dataset) {
        return List.of(
                new BenchmarkMethod("exact_filtering", false,
                        (query, weights) -> exactFiltering(query, dataset)),
                new BenchmarkMethod("weighted_full_scan", true,
                        (query, weights) -> weightedFullScan(query, dataset, weights)),
                new BenchmarkMethod("indexed_weighted_ranking", true,
                        (query, weights) -> indexedWeightedRanking(query, dataset, weights)),
                new BenchmarkMethod("enhanced_tf_idf_cosine", true,
                        (query, weights) -> enhancedTextRanking(query, dataset, weights,
                                TextSimilarityStrategies.tfIdfCosine())),
                new BenchmarkMethod("enhanced_levenshtein_token_fuzzy", true,
                        (query, weights) -> enhancedTextRanking(query, dataset, weights,
                                TextSimilarityStrategies.levenshteinTokenFuzzy())));
    }

    /**
     * Exact baseline: category, color, location, and unique keyword sets must
     * all match. It scans every candidate and emits 1.0 only for an exact hit.
     */
    private QueryRanking exactFiltering(Query query, BenchmarkDataset dataset) {
        List<ScoredCandidate> scores = new ArrayList<>();
        Set<String> queryTerms = new LinkedHashSet<>(query.text().tokens());
        for (Candidate candidate : dataset.candidates()) {
            boolean exact = scorer.sameMeaningfulCategory(query.item(), candidate.item())
                    && normalizer.sameMeaningfulStructuredValue(query.item().getColor(), candidate.item().getColor())
                    && normalizer.sameMeaningfulStructuredValue(query.item().getLocation(), candidate.item().getLocation())
                    && !queryTerms.isEmpty()
                    && queryTerms.equals(new LinkedHashSet<>(candidate.text().tokens()));
            scores.add(new ScoredCandidate(candidate, exact ? 1.0 : 0.0));
        }
        return new QueryRanking(sort(scores), scores.size());
    }

    /** Baseline score over all 80 candidates, including cross-category zero-score candidates. */
    private QueryRanking weightedFullScan(Query query, BenchmarkDataset dataset, SimilarityScorer.ScoreWeights weights) {
        List<ScoredCandidate> scores = new ArrayList<>();
        for (Candidate candidate : dataset.candidates()) {
            scores.add(new ScoredCandidate(candidate, scorer.score(query.item(), candidate.item(), weights)));
        }
        return new QueryRanking(sort(scores), scores.size());
    }

    /**
     * Uses the existing category hash table to retrieve category-qualified
     * candidates and the trie to calculate the exact Jaccard intersection
     * counts. It scores every category candidate, preserving the full-scan
     * score and candidate-ID tie policy while reducing examined candidates.
     */
    private QueryRanking indexedWeightedRanking(Query query, BenchmarkDataset dataset,
                                                SimilarityScorer.ScoreWeights weights) {
        CandidateIndex.IndexedCandidates indexedCandidates = dataset.index().categoryCandidates(query);
        List<ScoredCandidate> scores = new ArrayList<>();
        for (Candidate candidate : indexedCandidates.candidates()) {
            double keywordJaccard = indexedCandidates.keywordJaccard(candidate);
            scores.add(new ScoredCandidate(candidate,
                    scorer.scoreWithKeywordSimilarity(query.item(), candidate.item(), weights, keywordJaccard)));
        }
        return new QueryRanking(sort(scores), scores.size());
    }

    /** Changes only the text component; category, structured weights, threshold, and corpus remain fixed. */
    private QueryRanking enhancedTextRanking(Query query, BenchmarkDataset dataset,
                                             SimilarityScorer.ScoreWeights weights,
                                             TextSimilarityStrategies.Strategy strategy) {
        List<ScoredCandidate> scores = new ArrayList<>();
        for (Candidate candidate : dataset.candidates()) {
            double textScore = strategy.score(query.text(), candidate.text(), dataset.candidateDocuments());
            double total = scorer.scoreWithKeywordSimilarity(query.item(), candidate.item(), weights, textScore);
            scores.add(new ScoredCandidate(candidate, total));
        }
        return new QueryRanking(sort(scores), scores.size());
    }

    private List<ScoredCandidate> sort(List<ScoredCandidate> scores) {
        return scores.stream()
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(scored -> scored.candidate().id()))
                .toList();
    }

    private Evaluation evaluate(BenchmarkMethod method, BenchmarkDataset dataset, WeightConfig weight) {
        List<QueryOutcome> outcomes = new ArrayList<>();
        for (Query query : dataset.queries()) {
            QueryRanking ranking = method.ranker().rank(query, weight.weights());
            List<ScoredCandidate> accepted = ranking.ranked().stream()
                    .filter(candidate -> candidate.score() >= THRESHOLD)
                    .toList();
            Set<String> acceptedIds = accepted.stream().map(candidate -> candidate.candidate().id())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            Set<String> correct = new HashSet<>(acceptedIds);
            correct.retainAll(query.trueCandidateIds());
            int truePositives = correct.size();
            int falsePositives = acceptedIds.size() - truePositives;
            int falseNegatives = query.trueCandidateIds().size() - truePositives;
            outcomes.add(new QueryOutcome(query.id(), query.trueCandidateIds().size(), accepted.size(),
                    ranking.examined(), truePositives, falsePositives, falseNegatives,
                    hitAt(ranking.ranked(), query.trueCandidateIds(), 1),
                    hitAt(ranking.ranked(), query.trueCandidateIds(), 3),
                    hitAt(ranking.ranked(), query.trueCandidateIds(), 5)));
        }
        return new Evaluation(outcomes, Metrics.from(outcomes));
    }

    private RuntimeStats time(BenchmarkMethod method, BenchmarkDataset dataset, WeightConfig weight) {
        for (int iteration = 0; iteration < WARMUP_ITERATIONS; iteration++) {
            consumeRuntimeChecksum(rankAll(method, dataset, weight));
        }
        long[] samples = new long[MEASURED_ITERATIONS];
        for (int iteration = 0; iteration < MEASURED_ITERATIONS; iteration++) {
            long start = System.nanoTime();
            long checksum = rankAll(method, dataset, weight);
            samples[iteration] = System.nanoTime() - start;
            consumeRuntimeChecksum(checksum);
        }
        java.util.Arrays.sort(samples);
        return new RuntimeStats(percentile(samples, 0.10), percentile(samples, 0.50), percentile(samples, 0.90));
    }

    private long rankAll(BenchmarkMethod method, BenchmarkDataset dataset, WeightConfig weight) {
        long checksum = 1L;
        for (Query query : dataset.queries()) {
            QueryRanking ranking = method.ranker().rank(query, weight.weights());
            checksum = 31L * checksum + ranking.examined();
            if (!ranking.ranked().isEmpty()) {
                ScoredCandidate top = ranking.ranked().getFirst();
                checksum = 31L * checksum + top.candidate().id().hashCode();
                checksum = 31L * checksum + Double.doubleToLongBits(top.score());
            }
        }
        return checksum;
    }

    private void consumeRuntimeChecksum(long checksum) {
        runtimeSink ^= checksum;
    }

    private long percentile(long[] sortedValues, double percentile) {
        int index = (int) Math.round((sortedValues.length - 1) * percentile);
        return sortedValues[index];
    }

    private boolean hitAt(List<ScoredCandidate> ranked, Set<String> trueCandidateIds, int cutoff) {
        if (trueCandidateIds.isEmpty()) {
            return false;
        }
        return ranked.stream().limit(cutoff)
                .map(candidate -> candidate.candidate().id())
                .anyMatch(trueCandidateIds::contains);
    }

    private BenchmarkDataset loadDataset() throws IOException {
        Map<String, RawReport> reports = new LinkedHashMap<>();
        for (String[] row : readResourceRows("matching/evaluation_reports.csv", 10)) {
            RawReport report = new RawReport(row[0], row[1], row[2], row[3], row[4], row[5], row[6]);
            if (reports.put(report.id(), report) != null) {
                throw new IOException("Duplicate report ID " + report.id());
            }
        }

        Map<String, Set<String>> positivesByQuery = new HashMap<>();
        for (String[] row : readResourceRows("matching/evaluation_pairs.csv", 5)) {
            if ("1".equals(row[2])) {
                positivesByQuery.computeIfAbsent(row[0], ignored -> new LinkedHashSet<>()).add(row[1]);
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        List<Query> queries = new ArrayList<>();
        long candidateIndexId = 1L;
        for (RawReport report : reports.values()) {
            if ("FOUND".equals(report.type())) {
                FoundItem item = new FoundItem();
                apply(item, report);
                candidates.add(new Candidate(report.id(), candidateIndexId++, item,
                        TextSimilarityStrategies.TextDocument.fromReport(report.id(), report.itemName(), report.description(), normalizer)));
            }
        }
        candidates.sort(Comparator.comparing(Candidate::id));
        List<TextSimilarityStrategies.TextDocument> candidateDocuments = candidates.stream().map(Candidate::text).toList();
        for (RawReport report : reports.values()) {
            if ("LOST".equals(report.type())) {
                LostItem item = new LostItem();
                apply(item, report);
                queries.add(new Query(report.id(), item,
                        TextSimilarityStrategies.TextDocument.fromReport(report.id(), report.itemName(), report.description(), normalizer),
                        Set.copyOf(positivesByQuery.getOrDefault(report.id(), Set.of()))));
            }
        }
        Collections.shuffle(queries, new Random(BENCHMARK_SEED));
        BenchmarkDataset unindexed = new BenchmarkDataset(List.copyOf(queries), List.copyOf(candidates), candidateDocuments, null);
        return new BenchmarkDataset(unindexed.queries(), unindexed.candidates(), unindexed.candidateDocuments(),
                new CandidateIndex(unindexed, normalizer));
    }

    private void apply(FoundItem item, RawReport report) {
        item.setItemName(report.itemName());
        item.setCategory(report.category());
        item.setColor(report.color());
        item.setLocation(report.location());
        item.setDescription(report.description());
    }

    private void apply(LostItem item, RawReport report) {
        item.setItemName(report.itemName());
        item.setCategory(report.category());
        item.setColor(report.color());
        item.setLocation(report.location());
        item.setDescription(report.description());
    }

    private List<String[]> readResourceRows(String resourceName, int columns) throws IOException {
        InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName);
        if (input == null) {
            throw new IOException("Missing resource " + resourceName);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (reader.readLine() == null) {
                throw new IOException("Missing header in " + resourceName);
            }
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    String[] row = line.split(",", -1);
                    if (row.length != columns) {
                        throw new IOException("Expected " + columns + " columns in " + resourceName + " but found " + row.length);
                    }
                    rows.add(row);
                }
            }
            return rows;
        }
    }

    private Path resultsDirectory() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.getParent().resolve("docs"))) {
            return current.getParent().resolve("docs").resolve("research").resolve("results");
        }
        if (Files.isRegularFile(current.resolve("backend").resolve("pom.xml"))) {
            return current.resolve("docs").resolve("research").resolve("results");
        }
        throw new IllegalStateException("Unable to locate project research results directory from " + current);
    }

    private void writeSummaryCsv(Path file, List<SummaryRow> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("method,weight_set,color_weight,location_weight,keyword_weight,threshold,query_count,match_query_count,no_match_query_count,precision,recall,f1,hit_at_1,hit_at_3,hit_at_5,average_candidates_examined,median_runtime_ns,p10_runtime_ns,p90_runtime_ns,runtime_spread_ns,warmup_iterations,measured_iterations,seed");
        for (SummaryRow row : rows) {
            lines.add(row.toCsv());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private void writeQueryCsv(Path file, List<QueryRow> rows) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("method,weight_set,query_id,true_match_count,accepted_count,candidates_examined,true_positives,false_positives,false_negatives,hit_at_1,hit_at_3,hit_at_5,threshold");
        for (QueryRow row : rows) {
            lines.add(row.toCsv());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** Writes an SVG directly from the measured CSV rows; no external plotting library is used. */
    private void writeSvgPlot(Path file, List<SummaryRow> rows) throws IOException {
        final int width = 1_520;
        final int rowHeight = 42;
        final int height = 92 + rows.size() * rowHeight;
        long maximumRuntime = rows.stream().mapToLong(row -> row.runtime().medianNs()).max().orElse(1L);
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width)
                .append("\" height=\"").append(height).append("\" viewBox=\"0 0 ")
                .append(width).append(' ').append(height).append("\">\n")
                .append("<style>text{font-family:Arial,sans-serif;fill:#172033}.title{font-size:20px;font-weight:bold}.head{font-size:13px;font-weight:bold}.label{font-size:12px}.value{font-size:11px}</style>\n")
                .append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>\n")
                .append("<text class=\"title\" x=\"24\" y=\"30\">P2.4 matching benchmark: measured F1, median query-set runtime, and candidates examined</text>\n")
                .append("<text class=\"head\" x=\"24\" y=\"65\">Method / weights</text>\n")
                .append("<text class=\"head\" x=\"390\" y=\"65\">F1 (0–1)</text>\n")
                .append("<text class=\"head\" x=\"780\" y=\"65\">Median runtime (relative)</text>\n")
                .append("<text class=\"head\" x=\"1190\" y=\"65\">Candidates examined</text>\n");

        int y = 82;
        for (SummaryRow row : rows) {
            double f1 = row.metrics().f1();
            double runtimeMs = row.runtime().medianNs() / 1_000_000.0;
            double examined = row.metrics().averageCandidatesExamined();
            int f1Width = (int) Math.round(300 * f1);
            int runtimeWidth = (int) Math.round(300.0 * row.runtime().medianNs() / maximumRuntime);
            int examinedWidth = (int) Math.round(180.0 * examined / 80.0);
            String label = row.method() + " / " + row.weightSet();
            svg.append("<text class=\"label\" x=\"24\" y=\"").append(y + 15).append("\">")
                    .append(escapeXml(label)).append("</text>\n")
                    .append("<rect x=\"390\" y=\"").append(y).append("\" width=\"300\" height=\"20\" rx=\"3\" fill=\"#edf1f7\"/>\n")
                    .append("<rect x=\"390\" y=\"").append(y).append("\" width=\"").append(f1Width).append("\" height=\"20\" rx=\"3\" fill=\"#4f46e5\"/>\n")
                    .append("<text class=\"value\" x=\"700\" y=\"").append(y + 15).append("\">")
                    .append(String.format(Locale.ROOT, "%.3f", f1)).append("</text>\n")
                    .append("<rect x=\"780\" y=\"").append(y).append("\" width=\"300\" height=\"20\" rx=\"3\" fill=\"#edf1f7\"/>\n")
                    .append("<rect x=\"780\" y=\"").append(y).append("\" width=\"").append(runtimeWidth).append("\" height=\"20\" rx=\"3\" fill=\"#0f9f8d\"/>\n")
                    .append("<text class=\"value\" x=\"1090\" y=\"").append(y + 15).append("\">")
                    .append(String.format(Locale.ROOT, "%.3f ms", runtimeMs)).append("</text>\n")
                    .append("<rect x=\"1190\" y=\"").append(y).append("\" width=\"180\" height=\"20\" rx=\"3\" fill=\"#edf1f7\"/>\n")
                    .append("<rect x=\"1190\" y=\"").append(y).append("\" width=\"").append(examinedWidth).append("\" height=\"20\" rx=\"3\" fill=\"#f59e0b\"/>\n")
                    .append("<text class=\"value\" x=\"1380\" y=\"").append(y + 15).append("\">")
                    .append(String.format(Locale.ROOT, "%.1f", examined)).append("</text>\n");
            y += rowHeight;
        }
        svg.append("</svg>\n");
        Files.writeString(file, svg.toString(), StandardCharsets.UTF_8);
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private interface Ranker {
        QueryRanking rank(Query query, SimilarityScorer.ScoreWeights weights);
    }

    private record BenchmarkMethod(String id, boolean usesWeightSweep, Ranker ranker) {
    }

    private record WeightConfig(String id, SimilarityScorer.ScoreWeights weights) {
    }

    private record RawReport(String id, String type, String itemName, String category, String color,
                             String location, String description) {
    }

    private record Query(String id, LostItem item, TextSimilarityStrategies.TextDocument text,
                         Set<String> trueCandidateIds) {
    }

    private record Candidate(String id, long indexId, FoundItem item, TextSimilarityStrategies.TextDocument text) {
    }

    private record BenchmarkDataset(List<Query> queries, List<Candidate> candidates,
                                    List<TextSimilarityStrategies.TextDocument> candidateDocuments,
                                    CandidateIndex index) {
    }

    private record ScoredCandidate(Candidate candidate, double score) {
    }

    private record QueryRanking(List<ScoredCandidate> ranked, int examined) {
    }

    private record QueryOutcome(String queryId, int trueMatchCount, int acceptedCount, int candidatesExamined,
                                int truePositives, int falsePositives, int falseNegatives,
                                boolean hitAt1, boolean hitAt3, boolean hitAt5) {
    }

    private record Evaluation(List<QueryOutcome> outcomes, Metrics metrics) {
    }

    private record RuntimeStats(long p10Ns, long medianNs, long p90Ns) {
    }

    private record Metrics(double precision, double recall, double f1, double hitAt1, double hitAt3,
                           double hitAt5, double averageCandidatesExamined, int queryCount,
                           int matchQueryCount, int noMatchQueryCount) {
        private static Metrics from(List<QueryOutcome> outcomes) {
            int truePositives = outcomes.stream().mapToInt(QueryOutcome::truePositives).sum();
            int falsePositives = outcomes.stream().mapToInt(QueryOutcome::falsePositives).sum();
            int falseNegatives = outcomes.stream().mapToInt(QueryOutcome::falseNegatives).sum();
            int matchQueries = (int) outcomes.stream().filter(outcome -> outcome.trueMatchCount() > 0).count();
            double precision = divide(truePositives, truePositives + falsePositives);
            double recall = divide(truePositives, truePositives + falseNegatives);
            double f1 = precision + recall == 0.0 ? 0.0 : 2.0 * precision * recall / (precision + recall);
            double hitAt1 = matchQueries == 0 ? 0.0 : outcomes.stream().filter(QueryOutcome::hitAt1).count() / (double) matchQueries;
            double hitAt3 = matchQueries == 0 ? 0.0 : outcomes.stream().filter(QueryOutcome::hitAt3).count() / (double) matchQueries;
            double hitAt5 = matchQueries == 0 ? 0.0 : outcomes.stream().filter(QueryOutcome::hitAt5).count() / (double) matchQueries;
            double averageExamined = outcomes.stream().mapToInt(QueryOutcome::candidatesExamined).average().orElse(0.0);
            return new Metrics(precision, recall, f1, hitAt1, hitAt3, hitAt5, averageExamined,
                    outcomes.size(), matchQueries, outcomes.size() - matchQueries);
        }

        private static double divide(int numerator, int denominator) {
            return denominator == 0 ? 0.0 : numerator / (double) denominator;
        }
    }

    private record SummaryRow(String method, String weightSet, SimilarityScorer.ScoreWeights weights,
                              Metrics metrics, RuntimeStats runtime) {
        private static SummaryRow from(BenchmarkMethod method, WeightConfig weight, Metrics metrics,
                                       RuntimeStats runtime) {
            return new SummaryRow(method.id(), weight.id(), weight.weights(), metrics, runtime);
        }

        private String toCsv() {
            return String.format(Locale.ROOT,
                    "%s,%s,%.2f,%.2f,%.2f,%.2f,%d,%d,%d,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%d,%d,%d,%d,%d,%d,%d",
                    method, weightSet, weights.color(), weights.location(), weights.keyword(), THRESHOLD,
                    metrics.queryCount(), metrics.matchQueryCount(), metrics.noMatchQueryCount(),
                    metrics.precision(), metrics.recall(), metrics.f1(), metrics.hitAt1(), metrics.hitAt3(), metrics.hitAt5(),
                    metrics.averageCandidatesExamined(), runtime.medianNs(), runtime.p10Ns(), runtime.p90Ns(),
                    runtime.p90Ns() - runtime.p10Ns(), WARMUP_ITERATIONS, MEASURED_ITERATIONS, BENCHMARK_SEED);
        }
    }

    private record QueryRow(String method, String weightSet, QueryOutcome outcome) {
        private static List<QueryRow> from(BenchmarkMethod method, WeightConfig weight, List<QueryOutcome> outcomes) {
            return outcomes.stream().map(outcome -> new QueryRow(method.id(), weight.id(), outcome)).toList();
        }

        private String toCsv() {
            return String.format(Locale.ROOT, "%s,%s,%s,%d,%d,%d,%d,%d,%d,%s,%s,%s,%.2f",
                    method, weightSet, outcome.queryId(), outcome.trueMatchCount(), outcome.acceptedCount(),
                    outcome.candidatesExamined(), outcome.truePositives(), outcome.falsePositives(), outcome.falseNegatives(),
                    outcome.hitAt1(), outcome.hitAt3(), outcome.hitAt5(), THRESHOLD);
        }
    }

    /** Existing DSA indexes used as an access path; labels never reach this class. */
    private static final class CandidateIndex {
        private final FoundItemHashTable categoryIndex = new FoundItemHashTable();
        private final FoundItemTrie keywordIndex = new FoundItemTrie();
        private final Map<Long, Candidate> candidatesByIndexId = new HashMap<>();
        private final TextNormalizer normalizer;

        private CandidateIndex(BenchmarkDataset dataset, TextNormalizer normalizer) {
            this.normalizer = normalizer;
            for (Candidate candidate : dataset.candidates()) {
                candidatesByIndexId.put(candidate.indexId(), candidate);
                categoryIndex.insert(normalizer.normalize(candidate.item().getCategory()), candidate.indexId());
                for (String token : candidate.text().tokens()) {
                    keywordIndex.insert(token, candidate.indexId());
                }
            }
        }

        private IndexedCandidates categoryCandidates(Query query) {
            if (!normalizer.isMeaningfulStructuredValue(query.item().getCategory())) {
                return IndexedCandidates.empty();
            }
            Set<Long> categoryIds = new HashSet<>(categoryIndex.search(normalizer.normalize(query.item().getCategory())));
            Set<String> queryTerms = new LinkedHashSet<>(query.text().tokens());
            Map<Long, Integer> intersectionCounts = new HashMap<>();
            for (String token : queryTerms) {
                for (Long candidateId : keywordIndex.search(token)) {
                    if (categoryIds.contains(candidateId)) {
                        intersectionCounts.merge(candidateId, 1, Integer::sum);
                    }
                }
            }
            List<Candidate> candidates = categoryIds.stream()
                    .map(candidatesByIndexId::get)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Candidate::id))
                    .toList();
            Map<Long, Integer> candidateTermCounts = new HashMap<>();
            for (Candidate candidate : candidates) {
                candidateTermCounts.put(candidate.indexId(), new HashSet<>(candidate.text().tokens()).size());
            }
            return new IndexedCandidates(candidates, Map.copyOf(intersectionCounts), queryTerms.size(),
                    Map.copyOf(candidateTermCounts));
        }

        private record IndexedCandidates(List<Candidate> candidates, Map<Long, Integer> intersectionCounts,
                                         int queryTermCount, Map<Long, Integer> candidateTermCounts) {
            private static IndexedCandidates empty() {
                return new IndexedCandidates(List.of(), Map.of(), 0, Map.of());
            }

            private double keywordJaccard(Candidate candidate) {
                int intersection = intersectionCounts.getOrDefault(candidate.indexId(), 0);
                int union = queryTermCount + candidateTermCounts.getOrDefault(candidate.indexId(), 0) - intersection;
                return union == 0 ? 0.0 : (double) intersection / union;
            }
        }
    }
}
