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
GET       /api/claims/staff-access
GET       /api/claims/review-queue
POST      /api/claims/{id}/review
POST      /api/claims/{id}/complete-handover
POST      /api/claims/{id}/cancel
POST      /api/auth/google
```

## Claims and staff review

Every new lost-item report stores three private identifying details in the separate `lost_item_secret` table: interior color or lining, unique marks, and contents. They never appear in public lost-item, match, or ordinary claim responses.

On startup, complete values from the older `lost_item` verification columns are moved into `lost_item_secret` and the obsolete plaintext copies are cleared. Older reports that have no recorded reporter or no complete private details remain visible but cannot start a claim; recreate those reports if a claim workflow is needed.

Matching is only a candidate-ranking signal. The lost-report owner starts a claim with `POST /api/claims`; the backend derives the claimant, finder, and match score rather than accepting them from the request body. The owner does **not** answer the same private details again. A claim moves through `PENDING_STAFF_REVIEW`, `APPROVED_FOR_HANDOVER`, and `RESOLVED`; it can also be `REJECTED` or `CANCELLED`.

Configure staff accounts with a comma-separated `CLAIMS_STAFF_EMAILS` environment variable (for example, `CLAIMS_STAFF_EMAILS=staff1@example.edu,staff2@example.edu`). Only configured, non-participant staff can call `GET /api/claims/review-queue`; this is the sole endpoint that returns the saved private details, along with the lost and found report context. Staff submits `POST /api/claims/{id}/review` with `{ "decision": "APPROVE" | "REJECT", "staffNote": "optional audit note" }`. Approval makes the claim ready for an in-person handover. A staff member confirms the physical exchange with `POST /api/claims/{id}/complete-handover`, which resolves the claim and both reports in one transaction. The reviewer and completing staff member/timestamps are recorded separately.

Existing SQLite claims in the retired `PENDING_VERIFICATION` or `VERIFIED` states are migrated at startup to `PENDING_STAFF_REVIEW` and `APPROVED_FOR_HANDOVER`. Their active-claim indexes are rebuilt for the new lifecycle.

For the current local/demo browser application, protected write endpoints resolve an identity from the `X-User-Email` request header. The service enforces claim roles for that identity and never accepts claimant/finder IDs in request bodies. However, this header is **not authenticated** and can be spoofed; it must be replaced with a server-validated session or JWT derived from Google sign-in before deployment or any real privacy/security use.

## DSA features

- TextNormalizer + FoundItemTrie: keyword/prefix lookup.
- FoundItemHashTable: category lookup.
- FoundItemBinarySearch: date-range lookup.
- SimilarityScorer + MaxHeap: ranked candidates.
