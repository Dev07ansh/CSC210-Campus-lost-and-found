# Lost & Found Backend

Spring Boot API using SQLite.

## Run

```powershell
mvn "-Dmaven.repo.local=$PWD\.m2" spring-boot:run
```

The API listens on `http://localhost:8080`; its database is `data/lostandfound.db`.

## API

```text
GET/POST  /api/lost-items
GET/POST  /api/found-items
GET       /api/found-items/search?query=wallet&prefix=true
GET       /api/found-items/category/{category}
GET       /api/found-items/date-range?start=...&end=...
GET       /api/lost-items/{id}/matches?limit=5
POST      /api/lost-items/{id}/verify-ownership
POST      /api/auth/google
```

## Ownership verification

Every new lost-item report stores three private verification details: interior color or lining, unique marks, and contents. They are write-only API fields, so neither `GET /api/lost-items` nor a report-detail response exposes them.

A claimant answers the same three questions through `POST /api/lost-items/{id}/verify-ownership`. Each answer is assessed with Jaccard word similarity; all three must score at least 60% to pass. Incorrect attempts are stored by claimant email in `verification_attempt`, and the claimant is suspended after three failures. Existing reports that predate this feature expose `verificationAvailable: false` and do not show the claim-verification form.

## DSA features

- TextNormalizer + FoundItemTrie: keyword/prefix lookup.
- FoundItemHashTable: category lookup.
- FoundItemBinarySearch: date-range lookup.
- SimilarityScorer + MaxHeap: ranked candidates.
