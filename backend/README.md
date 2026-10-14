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
POST      /api/claims
GET       /api/claims/me
POST      /api/claims/{id}/verify
POST      /api/claims/{id}/resolve
POST      /api/claims/{id}/cancel
POST      /api/auth/google
```

## Claims and ownership verification

Every new lost-item report stores three private verification details in the separate `lost_item_secret` table: interior color or lining, unique marks, and contents. They never appear in lost-item, match, or claim responses.

On startup, complete values from the older `lost_item` verification columns are moved into `lost_item_secret` and the obsolete plaintext copies are cleared. Older reports that have no recorded reporter or no complete private details remain visible but cannot start a claim; recreate those reports if a claim workflow is needed.

Matching is only a candidate-ranking signal. The lost-report owner starts a claim with `POST /api/claims`; the backend derives the claimant, finder, and match score rather than accepting them from the request body. A claim moves through `PENDING_VERIFICATION`, `VERIFIED`, and `RESOLVED`; it can also be `REJECTED` or `CANCELLED`.

The claimant verifies three private answers through `POST /api/claims/{id}/verify`. Each answer is assessed with Jaccard word similarity and must be at least 60%. The server records the attempt number, pass/fail result, decision score, and timestamp, but never the raw answers. A third failed attempt rejects that claim. A successful verification produces a six-digit handover code once; only the finder can submit it to `POST /api/claims/{id}/resolve`, which resolves the claim and both reports in one transaction.

For the current local/demo browser application, protected write endpoints resolve an identity from the `X-User-Email` request header. The service enforces claim roles for that identity and never accepts claimant/finder IDs in request bodies. However, this header is **not authenticated** and can be spoofed; it must be replaced with a server-validated session or JWT derived from Google sign-in before deployment or any real privacy/security use.

## DSA features

- TextNormalizer + FoundItemTrie: keyword/prefix lookup.
- FoundItemHashTable: category lookup.
- FoundItemBinarySearch: date-range lookup.
- SimilarityScorer + MaxHeap: ranked candidates.
