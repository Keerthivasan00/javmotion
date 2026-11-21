# JavMotion – Backend

**See Code in Motion.** Spring Boot API, sandboxed execution worker and JDI tracer.

- Architecture: [docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md)
- Progress: [CHECKLIST.md](CHECKLIST.md)

## Stack

Java 21 · Spring Boot 4.2 · Maven (wrapper) · Jackson 3 · JUnit 5 — later: Spring Data JPA, MySQL, Flyway, Spring Security, Docker, JDI

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

## Run

```bash
./mvnw verify                                  # build + all tests
./mvnw -pl javmotion-api -am spring-boot:run   # API on http://localhost:8080
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
| `SANDBOX_IMAGE`, `SANDBOX_MAX_CONCURRENT`, `JOBS_DIR` | see `application.yml` | worker |

DB, JWT and other settings arrive with their checklist steps.

## API package layout (package-by-feature)

```
com.javmotion.api
├── config/        AppProperties, CorsConfig
├── common/error/  ApiException, GlobalExceptionHandler (ProblemDetail + "code")
├── system/        GET /api/v1/system/ping
├── execution/     (B3/B4)
├── visualizer/    (B2)
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
