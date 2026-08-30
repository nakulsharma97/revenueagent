# Revenue Recovery Agent — Preview Run Doc

## Uncommitted artifacts to reproduce
None — all source files are in the repo.

## How to run

### Backend (Spring Boot on :8080)
```bash
cd backend
mvn clean package -DskipTests -q
java -jar target/revenue-recovery-agent.jar --spring.profiles.active=dev
```
The `dev` profile uses H2 in-memory database with 300 seeded transactions.

### Frontend (Vite dev server on :5173)
```bash
cd frontend
npm install   # if node_modules missing
npm run dev
```

### Environment
- `VITE_API_BASE` env var can override the default `http://localhost:8080` backend URL.
- For production: `cd frontend && npm run build` → static files in `frontend/dist/`.
