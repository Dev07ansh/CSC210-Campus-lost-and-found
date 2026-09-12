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
POST      /api/auth/google
```

## DSA features

- TextNormalizer + FoundItemTrie: keyword/prefix lookup.
- FoundItemHashTable: category lookup.
- FoundItemBinarySearch: date-range lookup.
- SimilarityScorer + MaxHeap: ranked candidates.
