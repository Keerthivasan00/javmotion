# JavMotion – Backend

**See Code in Motion.** Spring Boot API, sandboxed execution worker and JDI tracer.

- Architecture: [docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md)
- Progress: [CHECKLIST.md](CHECKLIST.md)

## Stack

Java 21 · Spring Boot 4.2 · Maven (wrapper) · Jackson 3 · Spring Data JPA · MySQL 8 · Flyway · JUnit 5 + Testcontainers — later: Spring Security, Docker sandbox, JDI tracer

## Modules

| Module | What | Port |
|---|---|---|
| `javmotion-common` | Shared DTOs: trace schema (`trace/`), job enums (`job/`). Plain Java | – |
| `javmotion-api` | Public REST API (modular monolith) | 8080 |
| `javmotion-worker` | Private execution worker (Docker sandboxes) — skeleton | 8081 |
| `javmotion-tracer` | Runs inside sandbox, traces code with JDI — stub | – |

## Prerequisites

- **JDK 21+** with `JAVA_HOME` pointing to it (JDK 24 works; code compiles with `--release 21`).
- No Maven install needed — use `./mvnw` (or `mvnw.cmd` on Windows).
- **MySQL 8** for the API (from Step B3). Either:
  - Docker: `docker compose up -d` (matches the dev defaults), or
  - a local install: run `mysql -u root -p -P <port> < scripts/create-local-db.sql`, then set
    `DB_URL=jdbc:mysql://localhost:<port>/javmotion` (user/password default to `javmotion`).

  Flyway creates the tables on first start (`src/main/resources/db/migration`).

## Run

```bash
./mvnw verify                                  # build + all tests (DB tests skip without Docker/DB_URL)
./mvnw -pl javmotion-api -am spring-boot:run   # API on http://localhost:8080
```

Run code end to end without Docker (dev only — the host runner does **not** isolate code):

```bash
WORKER_RUNNER=host ./mvnw -pl javmotion-worker -am spring-boot:run      # worker on :8081
DB_URL=jdbc:mysql://localhost:3300/javmotion PORT=8090 ./mvnw -pl javmotion-api -am spring-boot:run
curl -X POST localhost:8090/api/v1/executions -H 'Content-Type: application/json'   -d '{"sourceCode":"public class Main { public static void main(String[] a) { System.out.println(42); } }","language":"java","mode":"RUN"}'
# → {"id":"…","accessToken":"…","status":"QUEUED"}; then GET /api/v1/executions/{id} with header X-Execution-Token
```

Check it:

```bash
curl http://localhost:8080/api/v1/system/ping   # {"status":"UP",...}
curl http://localhost:8080/actuator/health
```

Different port: `PORT=8090 ./mvnw -pl javmotion-api -am spring-boot:run` (then update `VITE_API_BASE_URL` in the frontend's `.env.development`).

## Configuration (env vars)

| Var | Default | Used by |
|---|---|---|
| `PORT` | 8080 / 8081 | api / worker |
| `SPRING_PROFILES_ACTIVE` | `dev` | api |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | api |
| `WORKER_TOKEN` | `dev-worker-token` | worker (later api too) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | dev: `jdbc:mysql://localhost:3306/javmotion`, `javmotion`, `javmotion`; prod: required | api |
| `SANDBOX_IMAGE`, `SANDBOX_MAX_CONCURRENT`, `JOBS_DIR` | see `application.yml` | worker |
| `WORKER_BASE_URL`, `WORKER_TOKEN` | dev: `http://localhost:8081`, `dev-worker-token`; prod: required | api |
| `WORKER_RUNNER` | `docker` | worker — `host` = **dev only, runs code on your machine with no sandbox** |

JWT and other settings arrive with their checklist steps.

## Database tests

- `ExecutionPersistenceTestcontainersTest` — real MySQL in Docker; skipped when Docker is missing.
- `ExecutionPersistenceLocalMySqlTest` — same checks against an existing MySQL; runs only when
  `DB_URL` is set (each test rolls back):
  `DB_URL=jdbc:mysql://localhost:3300/javmotion DB_USERNAME=javmotion DB_PASSWORD=javmotion ./mvnw -pl javmotion-api -am test`

## API package layout (package-by-feature)

```
com.javmotion.api
├── config/        AppProperties, CorsConfig
├── common/error/  ApiException, GlobalExceptionHandler (ProblemDetail + "code")
├── system/        GET /api/v1/system/ping
├── execution/     Execution entity + status machine, ExecutionTrace, repositories (B3); API (B4)
├── visualizer/    POST /api/v1/visualizer/* concept traces (B2)
├── program/       (B7)
├── user/          (B7)
└── learning/      (B7)
```

Rule: modules talk through service interfaces only — never another module's repository or entity.

## Error format

```json
{ "type": "about:blank", "title": "Bad Request", "status": 400, "detail": "...", "code": "VALIDATION_FAILED", "errors": { "field": "message" } }
```

Throw `new ApiException(HttpStatus.X, "CODE", "message")` from services for expected failures.
