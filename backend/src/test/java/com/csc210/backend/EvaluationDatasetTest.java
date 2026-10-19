package com.csc210.backend;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationDatasetTest {
    @Test
    void evaluationCorpusAndLabelsAreCompleteSeparateAndReproducible() throws Exception {
        List<String[]> reports = readRows("matching/evaluation_reports.csv");
        List<String[]> pairs = readRows("matching/evaluation_pairs.csv");

        assertEquals(100, reports.size());
        assertEquals(100, pairs.size());

        Map<String, String> typeById = new HashMap<>();
        Map<String, String[]> reportById = new HashMap<>();
        Map<String, Integer> reportsByCategory = new HashMap<>();
        int lostCount = 0;
        int foundCount = 0;
        for (String[] report : reports) {
            assertEquals(10, report.length, "reports must have the documented ten columns");
            assertTrue(typeById.put(report[0], report[1]) == null, "report IDs must be unique");
            reportById.put(report[0], report);
            assertEquals("manual-scenarios-v1", report[8]);
            assertEquals("none", report[9]);
            reportsByCategory.merge(report[3], 1, Integer::sum);
            if ("LOST".equals(report[1])) {
                lostCount++;
            } else if ("FOUND".equals(report[1])) {
                foundCount++;
            }
        }
        assertEquals(20, lostCount);
        assertEquals(80, foundCount);
        assertEquals(10, reportsByCategory.size());
        assertTrue(reportsByCategory.values().stream().allMatch(count -> count == 10));
        // A controlled true-match typo/noisy-observation pair and a hard no-match share only the intended fields.
        assertEquals("Grey", reportById.get("F18")[4]);
        assertEquals("Green", reportById.get("F77")[4]);

        int positiveCount = 0;
        int negativeCount = 0;
        Map<String, Integer> pairsByQuery = new HashMap<>();
        Set<String> seenPairs = new HashSet<>();
        Map<String, String> labelsByPair = new HashMap<>();
        Set<String> positiveQueries = new HashSet<>();
        Set<String> positiveCandidates = new HashSet<>();
        for (String[] pair : pairs) {
            assertEquals(5, pair.length, "pairs must have query ID, candidate ID, label, source, and seed");
            assertEquals("LOST", typeById.get(pair[0]));
            assertEquals("FOUND", typeById.get(pair[1]));
            assertEquals("manual-label-v1", pair[3]);
            assertEquals("none", pair[4]);
            String pairKey = pair[0] + "|" + pair[1];
            assertTrue(seenPairs.add(pairKey), "pair rows must be unique");
            labelsByPair.put(pairKey, pair[2]);
            pairsByQuery.merge(pair[0], 1, Integer::sum);
            if ("1".equals(pair[2])) {
                positiveCount++;
                assertTrue(positiveQueries.add(pair[0]), "each query has at most one true candidate");
                assertTrue(positiveCandidates.add(pair[1]), "each found report is a true match at most once");
            } else {
                assertEquals("0", pair[2]);
                negativeCount++;
            }
        }
        assertEquals(17, positiveCount);
        assertEquals(83, negativeCount);
        assertEquals(20, pairsByQuery.size());
        assertTrue(pairsByQuery.values().stream().allMatch(count -> count == 5));
        assertFalse(positiveQueries.contains("L18"));
        assertFalse(positiveQueries.contains("L19"));
        assertFalse(positiveQueries.contains("L20"));
        assertEquals("1", labelsByPair.get("L13|F18"), "the controlled noisy pair must stay a true match");
        assertEquals("0", labelsByPair.get("L20|F77"), "the hard no-match must stay explicitly negative");
    }

    private List<String[]> readRows(String resourceName) throws Exception {
        InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName);
        assertNotNull(input, () -> "Missing test resource " + resourceName);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertNotNull(header);
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(line.split(",", -1));
                }
            }
            return rows;
        }
    }
}
