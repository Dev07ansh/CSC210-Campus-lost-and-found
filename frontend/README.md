# Lost & Found Frontend

The browser client is written in HTML, CSS, and vanilla JavaScript. It automatically uses local demo data if the backend is not available.

## Run

From this folder:

```powershell
python -m http.server 5500
```

Open `http://localhost:5500`. Start the backend separately from `../backend`.

## Features

- Google Sign-In with the configured web client ID.
- Lost and found report forms backed by the API.
- DSA-backed found-item keyword, category, and date-range retrieval.
- Heap-ranked candidate matches on saved lost reports.

## Google configuration

In Google Cloud, add `http://localhost:5500` as an Authorized JavaScript origin for the configured OAuth web client.
