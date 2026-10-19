# Matching Evaluation Dataset

## Files and reproducibility

- Report corpus: `backend/src/test/resources/matching/evaluation_reports.csv`
- Ground-truth pairs: `backend/src/test/resources/matching/evaluation_pairs.csv`
- Report source tag: `manual-scenarios-v1`
- Label source tag: `manual-label-v1`
- Seed: `none` — the dataset is manually authored, not randomly generated.

The report corpus has **100 reports**: 20 lost-report queries and 80 found-report candidates. It covers ten categories (`Wallet`, `Keys`, `Electronics`, `Bags`, `Documents`, `Clothing`, `Accessories`, `Water Bottle`, `Umbrella`, and `Stationery`) with two lost reports and eight found candidates per category.

The pair file has **100 explicitly reviewed pairs**: 17 true matches (`label=1`) and 83 distractors (`label=0`). Each of the 17 matching lost reports has one positive and four manually selected distractors. The three no-match queries (`L18`, `L19`, and `L20`) each have five explicitly reviewed distractors and no positive label.

## Label rules

The scenarios were written before the scorer and strategies were run. A label of `1` means the report IDs describe the same physical item according to the scenario author’s fixed identity map. A label of `0` means they describe different physical items even when category, color, location, or wording overlap.

The evaluation uses a fixed closed-world candidate pool of all 80 found reports for every lost query. For a query with an explicitly listed `label=1`, that one candidate is its only true item; all other pool candidates are distractors. For the three no-match queries, every candidate is a distractor. This closed-world rule is part of the independently authored label protocol, not a score-derived inference. The benchmark loads the pair file only when calculating metrics; no label is provided to matching, indexing, or ranking code.

## Coverage deliberately included

- **Same-category distractors:** every category has multiple candidate reports, including similar wallets, keys, bags, and umbrellas.
- **Mismatched color/location:** examples include black fabric wallet `F04` versus lost wallet `L01`, and red drawstring bag `F28` versus lost bag `L04`.
- **Noisy wording and typos:** `F10` uses `keyes`/`key chain`, `F18` uses `Sliver`, and `F26` uses `Pumma back pack`.
- **Controlled noisy positive:** `L13` and its true match `F18` describe the same charger, but `F18` records `Grey` rather than `Silver` and spells `Sliver`. This isolates a noisy color observation and textual typo from the otherwise matching category, location, and detailed description.
- **Hard no-match:** `L20` and `F77` are both green notebooks at Residence Hall, but `F77` has blank pages rather than L20's organic-chemistry notes and owner name. It remains label `0` and checks that a plausible generic match can become a counted false positive instead of being treated as a true match.
- **No-match queries:** `L18` orange flask, `L19` yellow sunflower umbrella, and `L20` named chemistry notebook intentionally have no matching found report.
- **Unknown structured values:** `F08` and `F40` use `Not specified` for color, which tests the policy that unknown structured values are not agreement evidence.

## Limitations

This is a small, manually authored course-project dataset rather than an operational campus dataset. It contains English descriptions, one true candidate at most per query, fixed categories, and no photos or private verification data. Its labels should support reproducible comparison of retrieval methods, not a claim that any reported score generalizes to all campus lost-and-found cases.
