# Deterministic Baseline Matching Score

## Purpose and boundary

This score ranks a found-item candidate for a lost-item report. It is a retrieval suggestion only: a score never approves a claim, proves ownership, or exposes private verification details.

## Formula

The scorer first applies a category gate. Both report categories must normalize to a meaningful, identical value. Otherwise the result is exactly `0.0`.

For a passing category gate:

```text
score = 0.35 × Color + 0.30 × Location + 0.35 × KeywordJaccard
```

- `Color` is `1` for exact normalized agreement and `0` otherwise.
- `Location` is `1` for exact normalized agreement and `0` otherwise.
- `KeywordJaccard = |lostTokens ∩ foundTokens| / |lostTokens ∪ foundTokens|`.
- If either keyword set is empty, `KeywordJaccard = 0`.

The weights sum to `0.35 + 0.30 + 0.35 = 1.00`. Each component is in `[0, 1]`, so the weighted sum is in `[0, 1]`; the implementation also clamps defensive floating-point input to that range.

## Normalization and token policy

`TextNormalizer` applies the same policy to search terms, Jaccard, TF-IDF, and typo strategies:

1. lowercase using `Locale.ROOT`;
2. replace each run of punctuation or whitespace with one space;
3. trim surrounding whitespace;
4. retain Unicode letters and numbers;
5. split on spaces.

There is deliberately **no stop-word removal and no stemming**. Jaccard converts the item name and description tokens to a set, so repeated words do not change its score.

## Missing and unknown values

For structured `category`, `color`, and `location` fields, `null`, blank/punctuation-only input, `Other`, and `Not specified` are not evidence. Two such values never count as agreement.

- Unknown/different category: category gate fails and score is exactly `0.0`.
- Unknown color or location: that component is `0`.
- Both keyword sets empty: keyword component is `0`; the scorer does not divide by zero or create an empty-text match.

The rule applies to structured dropdown fields only. Words such as `other` in a user-written description remain normal text tokens.

## Deterministic ranking

`FoundItemService` ranks by score descending. When two persisted found reports have the same score, it returns the lower candidate ID first. This avoids depending on repository, hash-map, or heap iteration order.

## Worked example

Lost report: category `Wallet`, color `Black`, location `Library`, text `Black wallet`.

Found candidate: category `wallet`, color `black`, location `Gate 12`, text `Black leather wallet`.

```text
Color = 1
Location = 0
lost tokens  = {black, wallet}
found tokens = {black, leather, wallet}
Jaccard = 2 / 3
score = 0.35(1) + 0.30(0) + 0.35(2/3)
      = 0.5833333333333333
```
