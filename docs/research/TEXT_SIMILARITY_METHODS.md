# Text Similarity Strategies

All P2.2 strategies use `TextNormalizer` before comparison. Text is lowercased with `Locale.ROOT`; punctuation and whitespace collapse to spaces; Unicode letters and numbers are retained; no stop words are removed and no stemming is used. Item-name tokens are followed by description tokens. This keeps repeated tokens for TF while allowing Jaccard to use a set.

Every strategy receives the same lost-report query, candidate found report, and full fixed candidate pool. Labels are not passed to a strategy. The baseline category gate and color/location components are not changed when a strategy changes; `SimilarityScorer.scoreWithKeywordSimilarity(...)` recombines only the text component.

## Jaccard

```text
Jaccard(q, c) = |unique(q) ∩ unique(c)| / |unique(q) ∪ unique(c)|
```

If either set is empty, the result is `0.0`.

## TF-IDF cosine

IDF is calculated over the candidate pool for the current query. The query itself is not added to that corpus.

```text
N          = number of candidate documents in the fixed pool
df(t)      = number of pool documents containing t at least once
tf(t, d)   = count(t, d) / tokenCount(d)
idf(t)     = ln((N + 1) / (df(t) + 1)) + 1
w(t, d)    = tf(t, d) × idf(t)
cosine     = dot(q, c) / (||q||₂ × ||c||₂)
```

Empty documents or zero vectors return `0.0`. Terms are processed in lexical order, and document frequency counts are order-independent, so a fixed pool produces equal output even if its input order changes.

If a query term does not appear in any candidate document, it still remains in the query vector with `df(t)=0` and `idf(t)=ln(N+1)+1`. It is not silently discarded; this prevents a query containing extra unmatched evidence from being incorrectly treated as identical to a shorter candidate.

## Token-level Levenshtein fuzzy score

For typo-tolerance, unique normalized query and candidate tokens are sorted. Each query token is matched once to the unused candidate token that has the greatest normalized character-edit similarity; lexical token order breaks equal similarities.

```text
tokenSimilarity(a, b) = 1 - levenshteinDistance(a, b) / max(length(a), length(b))
fuzzy(q, c) = sum(selected one-to-one token similarities) / max(uniqueTokenCount(q), uniqueTokenCount(c))
```

Blank input returns `0.0`. The implementation keeps even low-similarity pairings as zero-to-one evidence; it does not invent a learned threshold or use an external model.

## Worked examples covered by tests

- Jaccard: `{black,wallet,leather}` versus `{wallet,leather,chain}` gives `2/4 = 0.5`.
- TF-IDF: pool `C1=wallet leather`, `C2=wallet keys`, `C3=keys`; query `wallet wallet leather` versus `C1` gives `0.943085996661427` under the formula above.
- Fuzzy: `wallet keys` versus `walet keys` gives `(5/6 + 1)/2 = 11/12 = 0.916666666666667`.
