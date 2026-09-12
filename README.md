# Campus Lost & Found

A full-stack campus Lost & Found application with a vanilla JavaScript frontend and a Spring Boot / SQLite backend.

## Structure

```text
.
├── frontend/     # browser client, assets, and UI documentation
├── backend/      # Spring Boot API, SQLite configuration, Java source and tests
└── README.md     # project setup
```

## Run locally

Open two terminals at this project folder.

```powershell
cd backend
mvn "-Dmaven.repo.local=$PWD\.m2" spring-boot:run
```

```powershell
cd frontend
python -m http.server 5500
```

Open [http://localhost:5500](http://localhost:5500). The client calls `http://localhost:8080/api`.

See [frontend/README.md](frontend/README.md) and [backend/README.md](backend/README.md) for details.
