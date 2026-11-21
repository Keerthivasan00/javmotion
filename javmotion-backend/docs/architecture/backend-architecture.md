# JavMotion Backend Architecture

> Scope: `javmotion-backend/` — Spring Boot API, sandboxed execution worker, trace format, database, security, deployment.
> Companion doc: [frontend-architecture.md](frontend-architecture.md)

---

## 1. Overview

The backend is a **modular monolith API** plus a **separately deployed execution worker**. User code never runs inside the API process.

```
                 ┌───────────────────────── Public internet ─────────────────────────┐
React (GitHub Pages) ──HTTPS/JSON──▶  javmotion-api  (Spring Boot, Render)
                                       │  modules: user, program, execution,
                                       │           visualizer, learning
                                       │
                                       ├──JPA──▶ MySQL
                                       │
                                       └──HTTPS + shared secret──▶ javmotion-worker (EC2 / local Docker)
                                                                     │
                                                                     └─ docker run (one sandbox per job)
                                                                          ├─ javac
                                                                          ├─ java          (Normal Run)
                                                                          └─ tracer + JDI  (Visualize)
```

Two runnable apps, one shared library:

| Artifact | Role | Exposed to internet |
|---|---|---|
| `javmotion-api` | REST API, auth, persistence, job orchestration, concept visualizers | Yes |
| `javmotion-worker` | Receives jobs, starts sandbox containers, collects output/trace | No (private network, API-only) |
| `javmotion-tracer` | Small Java program run *inside* the sandbox; uses JDI to trace user code | Runs in sandbox only |
| `javmotion-common` | Shared DTOs: job request/result, trace event schema | Library |

---

## 2. Repository Layout (Maven multi-module)

```
javmotion-backend/
├── pom.xml                         # parent: Java 21, Spring Boot 3.x BOM, module list
├── javmotion-common/               # plain Java, no Spring
│   └── src/main/java/com/javmotion/common/
│       ├── job/      ExecutionJob, ExecutionResult, ExecutionMode, JobStatus
│       └── trace/    Trace, TraceStep, Frame, Value, HeapObject, EventType
├── javmotion-api/
│   └── src/main/java/com/javmotion/api/
│       ├── JavMotionApiApplication.java
│       ├── config/          SecurityConfig, CorsConfig, JacksonConfig, WorkerClientConfig
│       ├── common/          error handling (ApiError, GlobalExceptionHandler), paging, utils
│       ├── user/            auth + profile
│       ├── program/         saved programs CRUD
│       ├── execution/       submit / status / output / trace, worker client, retention
│       ├── visualizer/      concept visualizers (string, array, loop) — no user code run
│       └── learning/        lessons, challenges, progress
│   └── src/main/resources/
│       ├── application.yml, application-dev.yml, application-prod.yml
│       └── db/migration/    Flyway V1__init.sql, V2__..., ...
├── javmotion-worker/
│   └── src/main/java/com/javmotion/worker/
│       ├── JavMotionWorkerApplication.java
│       ├── api/             InternalJobController (POST /internal/jobs)
│       ├── sandbox/         SandboxRunner, DockerCommandBuilder, ResourceLimits
│       ├── workspace/       temp dir create/cleanup
│       └── result/          output truncation, trace validation
├── javmotion-tracer/
│   └── src/main/java/com/javmotion/tracer/
│       ├── TracerMain.java  # launches debuggee JVM via JDI LaunchingConnector
│       ├── EventLoop.java   # Step / MethodEntry / MethodExit / Exception handling
│       ├── StateCapture.java# frames, locals, heap walk
│       └── TraceWriter.java # JSON to /sandbox/out/trace.json
├── docker/
│   ├── sandbox.Dockerfile   # eclipse-temurin:21-jdk + tracer.jar, non-root user
│   ├── api.Dockerfile
│   └── worker.Dockerfile
└── docker-compose.yml       # dev: mysql + api + worker
```

**Package-by-feature rule:** each module package (`user`, `program`, ...) owns its `controller`, `service`, `repository`, `entity`, `dto` sub-packages. Modules talk to each other only through service interfaces, never through another module's repository or entity. This keeps the monolith splittable later.

```
execution/
├── ExecutionController.java
├── ExecutionService.java          # interface used by other modules
├── internal/
│   ├── ExecutionServiceImpl.java
│   ├── WorkerClient.java          # RestClient to worker
│   ├── ExecutionDispatcher.java   # @Async job submit + status updates
│   └── TraceRetentionJob.java     # @Scheduled cleanup
├── entity/   Execution, ExecutionTrace
├── repository/
└── dto/      CreateExecutionRequest, ExecutionResponse, OutputResponse
```

---

## 3. Module Responsibilities

Mapping of the proposal's 7 backend modules to code:

| Proposal module | Lives in | Notes |
|---|---|---|
| Execution | `api/execution` | Lifecycle, status, timeouts, persistence |
| Worker | `javmotion-worker` | Docker isolation, limits, cleanup |
| Debugger | `javmotion-tracer` (+ trace post-processing in `api/execution`) | JDI line stepping, variables, call stack |
| Visualizer | `api/visualizer` | Concept visualizations producing the **same trace format** |
| Program | `api/program` | CRUD on saved code |
| User | `api/user` | Register, login, JWT, profile |
| Learning | `api/learning` | Lessons, challenges, progress |

### 3.1 Debugger strategy: record, then replay

JDI is used to **record a full trace in one run**, not for live interactive sessions. The frontend replays the trace.

Why:
- No long-lived debug sessions to hold open per user (cheap, stateless, scales).
- Previous Step / replay / speed control become trivial (just array indexing on client).
- Breakpoints become a client-side playback feature ("play until line N").
- Works on hosts that can't keep WebSockets open (Render free tier sleeps).

Trade-off: no stdin interaction during visualize, and step cap applies (see §6.4). Live debugging can be added later as a separate mode.

### 3.2 Concept visualizers (Phase 2)

`api/visualizer` generates traces for predefined concepts (string reverse, array sort, loop iteration) **in pure Java, without running user code**. Each generator emits `Trace` objects identical in schema to JDI traces. Result: frontend visualizers are built once and work for both Phase 2 demos and Phase 4 real traces.

```java
public interface ConceptVisualizer<I> {
    String key();                 // "string-reverse", "array", "loops"
    Trace generate(I input);      // validated, size-limited input
}
```

---

## 4. Execution Flow

### 4.1 Sequence

```
Client            API                         Worker                    Sandbox container
  │ POST /executions │                          │                            │
  │─────────────────▶│ validate, rate-limit     │                            │
  │                  │ save Execution(QUEUED)   │                            │
  │◀─ 202 {id,token} │                          │                            │
  │                  │ @Async dispatch ────────▶│ POST /internal/jobs        │
  │                  │ status=RUNNING           │ mkdir workspace, write src │
  │                  │                          │ docker run ... ───────────▶│ javac -g Main.java
  │                  │                          │                            │ java Main  | tracer
  │                  │                          │◀── exit code, stdout,      │
  │                  │                          │    stderr, trace.json      │
  │                  │                          │ truncate + validate        │
  │                  │◀── ExecutionResult ──────│ rm workspace               │
  │                  │ save output/trace        │                            │
  │                  │ status=COMPLETED/...     │                            │
  │ GET /executions/{id} (poll every ~500ms)    │                            │
  │─────────────────▶│                          │                            │
  │◀── status        │                          │                            │
  │ GET /executions/{id}/events                 │                            │
  │─────────────────▶│ trace JSON               │                            │
```

Polling first (simple, works everywhere). Upgrade path: SSE on `GET /executions/{id}/stream`.

### 4.2 Execution status machine

```
QUEUED ─▶ RUNNING ─┬─▶ COMPLETED          (exit 0)
                   ├─▶ COMPILE_ERROR      (javac failed)
                   ├─▶ RUNTIME_ERROR      (uncaught exception / non-zero exit)
                   ├─▶ TIMEOUT            (wall clock exceeded)
                   ├─▶ LIMIT_EXCEEDED     (memory, output size, step cap)
                   └─▶ FAILED             (internal/worker error)
QUEUED/RUNNING ─▶ CANCELLED               (DELETE /executions/{id})
```

### 4.3 Modes

| | Normal Run | Visualize |
|---|---|---|
| Command in sandbox | `javac Main.java && java -Xmx128m Main` | `javac -g Main.java && java -jar tracer.jar Main` |
| Output | stdout, stderr, exit code, duration | same + `trace.json` |
| Time limit | 5 s | 10 s |
| stdin | optional input string | not supported (v1) |

`-g` is required so JDI can read local variable names.

### 4.4 Source handling

- Entry class detection: find `public class X` with `public static void main(String[])`; file written as `X.java`. Fallback: `Main`.
- Single-file programs in v1. Multi-file later (zip of sources).
- Max source size: 64 KB. Rejected at API before any dispatch.

---

## 5. Worker & Sandbox

### 5.1 Worker app

Small Spring Boot app (or plain Java + embedded server). Responsibilities:
1. Auth check on `/internal/jobs` (shared secret header `X-Worker-Token`, plus private network / IP allowlist).
2. Concurrency limit: bounded thread pool (e.g. 4 concurrent sandboxes per host). Excess → `429`, API retries/queues.
3. Workspace: `/var/javmotion/jobs/{jobId}/src` (read-only mount) and `/out` (writable mount). Always deleted in `finally`.
4. Run sandbox with hard limits, kill on wall-clock timeout (`docker kill`), collect results.
5. Truncate stdout/stderr (64 KB each) and validate trace (size, schema, step cap) — **trace content is untrusted** because it's produced in the same container as user code.

### 5.2 Sandbox container command

```bash
docker run --rm \
  --name jm-job-{jobId} \
  --network none \
  --memory 256m --memory-swap 256m \
  --cpus 1 \
  --pids-limit 64 \
  --read-only \
  --tmpfs /tmp:rw,size=16m,noexec \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  --user 10001:10001 \
  -v /var/javmotion/jobs/{jobId}/src:/sandbox/src:ro \
  -v /var/javmotion/jobs/{jobId}/out:/sandbox/out:rw \
  javmotion-sandbox:21 /sandbox/run.sh {mode} {className}
```

- Image `javmotion-sandbox:21`: `eclipse-temurin:21-jdk` + `tracer.jar` + `run.sh`, non-root user, no extra tools.
- JVM flags inside: `-Xmx128m -XX:MaxMetaspaceSize=64m -Xss512k -XX:+UseSerialGC` (keeps footprint predictable under cgroup limit).
- Compile happens in the same container (simpler; javac also untrusted-input-facing).

### 5.3 Isolation hardening roadmap

| Stage | Isolation |
|---|---|
| Dev | Docker on developer machine |
| v1 prod | Dedicated EC2 host; only worker + Docker; no secrets on host besides worker token |
| v2 | gVisor (`--runtime=runsc`) for syscall isolation |
| v3 | Firecracker microVMs or per-job ephemeral instances if load justifies |

> **Security note:** the worker needs access to the Docker socket, which is root-equivalent on that host. That is why the worker must run on a **dedicated host** with no database credentials, no API secrets, and no inbound access except from the API.

---

## 6. Trace Format (Backend ⇄ Frontend Contract)

Owned by `javmotion-common/trace`. Frontend mirrors it in TypeScript (`src/lib/trace/types.ts`). Any change bumps `schemaVersion`.

### 6.1 Shape

```json
{
  "schemaVersion": 1,
  "source": "JDI",                // or "CONCEPT"
  "language": "java",
  "truncated": false,
  "steps": [
    {
      "index": 0,
      "event": "LINE",            // LINE | CALL | RETURN | EXCEPTION | OUTPUT | END
      "line": 3,
      "stack": [
        {
          "id": "f1",
          "method": "main",
          "className": "Main",
          "line": 3,
          "locals": {
            "i":    { "kind": "PRIMITIVE", "type": "int", "value": 0 },
            "name": { "kind": "REF", "ref": "h3" },
            "arr":  { "kind": "REF", "ref": "h7" },
            "obj":  { "kind": "NULL" }
          }
        }
      ],
      "heap": {
        "h3": { "kind": "STRING", "type": "java.lang.String", "value": "hello", "interned": true },
        "h7": { "kind": "ARRAY",  "type": "int[]", "elements": [
                  { "kind": "PRIMITIVE", "type": "int", "value": 5 },
                  { "kind": "PRIMITIVE", "type": "int", "value": 2 } ] },
        "h9": { "kind": "OBJECT", "type": "Dog", "fields": {
                  "name": { "kind": "REF", "ref": "h3" } } }
      },
      "stdout": "",               // cumulative output up to this step
      "returnValue": null,        // set on RETURN
      "exception": null,          // { "type": "...", "message": "..." } on EXCEPTION
      "highlights": []            // concept visualizers only: e.g. [{"ref":"h7","index":1,"role":"compare"}]
    }
  ]
}
```

### 6.2 Design rules

- **Full snapshot per step** (v1): stack + reachable heap. Simplest for the frontend (random access to any step). Delta encoding is a v2 optimization if traces get large.
- Heap ids (`h<n>`) are stable across steps for the same object (based on JDI `ObjectReference.uniqueID()`), so the frontend can animate the same object moving/changing.
- Stack top = last element in `stack`.
- Only user classes traced: JDI class exclusion filters `java.*`, `javax.*`, `sun.*`, `jdk.*`, `com.sun.*`. JDK internals appear only as opaque values.
- Strings shown as values; `interned` flag supports String Pool visualization (Phase 5).
- Collections (`ArrayList`, `HashMap`) captured as special `kind` (`LIST`, `MAP`) in Phase 5 instead of walking internals.

### 6.3 Tracer internals (JDI)

```
TracerMain
 ├─ LaunchingConnector → start debuggee "Main" with -Xmx128m, suspended
 ├─ ClassPrepareRequest (user classes) → on prepare: add breakpoints-free StepRequest
 ├─ StepRequest(STEP_LINE, STEP_INTO) with class exclusions  → LINE events
 ├─ MethodEntryRequest / MethodExitRequest (user classes)     → CALL / RETURN (+ return value)
 ├─ ExceptionRequest(caught=false, uncaught=true)             → EXCEPTION
 ├─ stdout pump thread: debuggee stdout → buffer, attached to next step
 └─ on each event: StateCapture walks thread frames → visibleVariables → heap BFS
                   (depth limit 4, object limit 200 per step, array preview 100 elements)
```

### 6.4 Limits (enforced in tracer, re-checked in worker)

| Limit | Value | On exceed |
|---|---|---|
| Max steps | 5,000 | stop, `truncated: true`, status `LIMIT_EXCEEDED` (trace kept) |
| Max trace size | 10 MB uncompressed | stop, truncated |
| Heap objects per step | 200 | elide, mark `"elided": true` |
| Array elements shown | 100 | preview + `length` |
| String length shown | 1,000 chars | truncate |

---

## 7. REST API

Base path: `/api/v1`. JSON everywhere. Errors use one shape (RFC 7807 style):

```json
{ "type": "about:blank", "title": "Validation failed", "status": 400,
  "code": "SOURCE_TOO_LARGE", "detail": "Source exceeds 64 KB", "traceId": "..." }
```

### 7.1 Execution

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/executions` | optional | Body `{ sourceCode, language:"java", mode:"RUN"\|"VISUALIZE", stdin?, programId? }` → `202 { id, accessToken, status }` |
| GET | `/executions/{id}` | owner / token | Status, timings, exit code |
| GET | `/executions/{id}/output` | owner / token | stdout, stderr, compile errors (with parsed line/column) |
| GET | `/executions/{id}/events` | owner / token | Trace JSON (§6). `404` for RUN mode |
| DELETE | `/executions/{id}` | owner / token | Cancel if running; else delete record |
| GET | `/executions?page=` | user | History for logged-in user |

Execution ids are UUIDs. Anonymous callers get a per-execution `accessToken` (header `X-Execution-Token`) so nobody can read another user's job by guessing ids.

Compile errors are parsed from javac output into structured items so the editor can underline them:

```json
{ "compileErrors": [ { "line": 5, "column": 12, "message": "';' expected" } ] }
```

### 7.2 Visualizer (concept)

| Method | Path | Body |
|---|---|---|
| POST | `/visualizer/string-reverse` | `{ "input": "hello" }` |
| POST | `/visualizer/array` | `{ "operation": "BUBBLE_SORT"\|"LINEAR_SEARCH"\|"BINARY_SEARCH"\|..., "values": [..], "target"?: n }` |
| POST | `/visualizer/loops` | `{ "type": "FOR"\|"WHILE"\|"DO_WHILE"\|"NESTED", "start", "end", "step" }` |

All return a `Trace` synchronously (pure computation, sub-millisecond, input size-capped).

### 7.3 Programs

| Method | Path | Notes |
|---|---|---|
| POST | `/programs` | `{ title, sourceCode, language }` |
| GET | `/programs?page=&size=` | Current user's programs |
| GET | `/programs/{id}` | Owner only |
| PUT | `/programs/{id}` | Owner only |
| DELETE | `/programs/{id}` | Owner only |

### 7.4 Auth & user

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | `{ username, email, password }` |
| POST | `/auth/login` | → `{ accessToken, expiresIn, user }` + refresh cookie |
| POST | `/auth/refresh` | Uses refresh cookie → new access token (rotation) |
| POST | `/auth/logout` | Revokes refresh token, clears cookie |
| GET | `/users/me` | Profile |
| PUT | `/users/me` | Update profile |

### 7.5 Learning (Phase 6)

`GET /lessons`, `GET /lessons/{slug}`, `GET /challenges`, `GET /challenges/{id}`, `POST /challenges/{id}/submissions`, `GET /users/me/progress`.

Challenge submissions reuse the execution pipeline; expected output comparison happens in the API after the worker returns.

### 7.6 Ops

`GET /actuator/health` (public, for Render health check). Other actuator endpoints disabled or protected.

---

## 8. Security

### 8.1 Authentication

- Spring Security, **stateless JWT** (frontend on GitHub Pages is a different origin, so session cookies are awkward).
- Access token: 15 min, HS256 (secret from env), returned in body, kept **in memory** on the client.
- Refresh token: 14 days, random opaque value, stored hashed in `refresh_tokens`, sent as `HttpOnly; Secure; SameSite=None; Path=/api/v1/auth` cookie. Rotated on every refresh; reuse of an old token revokes the whole family.
- Passwords: BCrypt (strength 12).

### 8.2 Authorization

- Public: `POST /executions` (anonymous allowed, stricter rate limits), `/visualizer/**`, `GET /lessons/**`, `/auth/**`, health.
- Authenticated: `/programs/**`, `/users/me/**`, execution history, challenge submissions.
- Ownership enforced in service layer (`program.userId == currentUser.id`), not just controllers.

### 8.3 Abuse controls

| Control | Setting |
|---|---|
| Rate limit (Bucket4j, per IP / per user) | anonymous: 10 executions/min; user: 30/min |
| Request body size | 128 KB |
| Source size | 64 KB |
| Concurrent executions per user | 2 |
| CORS | allow only `https://<user>.github.io` + `http://localhost:5173` |
| Input validation | Jakarta Bean Validation on all DTOs |

### 8.4 Execution security checklist (maps proposal §15)

- [x] Never run user code in API JVM — worker + sandbox only
- [x] CPU / memory / pids limits — §5.2
- [x] Timeouts — wall clock in worker, `docker kill` on exceed
- [x] No network — `--network none`
- [x] Filesystem — read-only root, tmpfs, only job dirs mounted
- [x] Non-root — `--user 10001`, `--cap-drop ALL`, `no-new-privileges`
- [x] Job access control — ownership or per-execution token
- [x] Cleanup — `--rm`, workspace deleted in `finally`, periodic orphan sweeper (`docker ps --filter name=jm-job-`)
- [x] Size limits — source, output, trace

---

## 9. Database (MySQL 8)

Schema managed by **Flyway**; `spring.jpa.hibernate.ddl-auto=validate`.

```
users ──< programs ──< executions ──1:1── execution_traces
  │                         │
  │                         └── (user_id nullable for anonymous)
  ├──< refresh_tokens
  ├──< challenge_submissions >── challenges
  └──< lesson_progress     >── lessons
```

### 9.1 Tables

```sql
users (
  id BIGINT PK AUTO_INCREMENT,
  username VARCHAR(50) UNIQUE NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(100),
  role VARCHAR(20) NOT NULL DEFAULT 'USER',
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
)

refresh_tokens (
  id BIGINT PK, user_id FK, token_hash CHAR(64) UNIQUE, family_id CHAR(36),
  expires_at DATETIME(6), revoked_at DATETIME(6) NULL, created_at DATETIME(6)
)

programs (
  id BIGINT PK, user_id FK NOT NULL, title VARCHAR(150) NOT NULL,
  source_code MEDIUMTEXT NOT NULL, language VARCHAR(20) NOT NULL DEFAULT 'java',
  created_at, updated_at,
  INDEX (user_id, updated_at)
)

executions (
  id CHAR(36) PK,                    -- UUID
  user_id FK NULL, program_id FK NULL,
  access_token_hash CHAR(64) NULL,   -- for anonymous access
  mode VARCHAR(16) NOT NULL,         -- RUN | VISUALIZE
  status VARCHAR(20) NOT NULL,
  source_code MEDIUMTEXT NOT NULL,   -- snapshot at run time
  stdin TEXT NULL,
  stdout MEDIUMTEXT NULL, stderr MEDIUMTEXT NULL,
  compile_errors JSON NULL,
  exit_code INT NULL,
  execution_time_ms INT NULL,
  error_message VARCHAR(500) NULL,
  created_at, started_at NULL, finished_at NULL,
  INDEX (user_id, created_at), INDEX (status, created_at)
)

execution_traces (
  execution_id CHAR(36) PK FK,
  schema_version INT NOT NULL,
  step_count INT NOT NULL,
  truncated BOOLEAN NOT NULL,
  trace_gzip LONGBLOB NOT NULL,      -- gzipped JSON
  created_at DATETIME(6)
)

lessons (id, slug UNIQUE, title, module, order_index, content_md MEDIUMTEXT, example_code TEXT, created_at)
lesson_progress (user_id, lesson_id, completed_at, PK(user_id, lesson_id))
challenges (id, title, description MEDIUMTEXT, difficulty VARCHAR(10), starter_code TEXT,
            test_cases JSON, created_at)
challenge_submissions (id, challenge_id, user_id, execution_id, passed BOOLEAN, created_at)
```

### 9.2 Deviation from proposal: `execution_events`

The proposal lists `execution_events (step_number, event_type, event_data)` — one row per step. A 5,000-step trace would mean 5,000 inserts per run and 5,000-row reads per replay. Instead we store **one gzipped JSON blob per execution** in `execution_traces`. The frontend always loads the full trace anyway, so per-step rows bring no query benefit. Gzip typically shrinks traces 10–20×.

### 9.3 Retention

`TraceRetentionJob` (`@Scheduled`, daily):
- Anonymous executions + traces: delete after 24 h.
- User traces: delete after 7 days (execution row + output kept for history).
- Execution rows: kept 90 days.

---

## 10. Configuration

`application.yml` reads everything sensitive from environment variables.

| Env var | Used by | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | api, worker | `dev` / `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | api | `jdbc:mysql://...:3306/javmotion` |
| `JWT_SECRET` | api | 256-bit random |
| `CORS_ALLOWED_ORIGINS` | api | `https://<user>.github.io,http://localhost:5173` |
| `WORKER_BASE_URL` | api | `http://worker:8081` (dev) |
| `WORKER_TOKEN` | api, worker | shared secret |
| `SANDBOX_IMAGE` | worker | `javmotion-sandbox:21` |
| `SANDBOX_MAX_CONCURRENT` | worker | `4` |
| `JOBS_DIR` | worker | `/var/javmotion/jobs` |

Typed config via `@ConfigurationProperties` classes (`ExecutionProperties`, `WorkerProperties`, `JwtProperties`).

---

## 11. Key Libraries

| Concern | Choice |
|---|---|
| Framework | Spring Boot 3.x (Web, Data JPA, Security, Validation, Actuator) |
| DB | MySQL Connector/J, Flyway |
| JWT | `jjwt` or Spring Security OAuth2 Resource Server (Nimbus) |
| Rate limiting | Bucket4j |
| HTTP client (api→worker) | Spring `RestClient` |
| Mapping | MapStruct (optional) or hand-written mappers |
| Boilerplate | Lombok (optional) |
| Docker control (worker) | `ProcessBuilder` calling `docker` CLI (v1); docker-java later |
| Debugger | `jdk.jdi` module (built into JDK 21) |
| Tests | JUnit 5, Spring Boot Test, Testcontainers (MySQL), MockMvc |

---

## 12. Testing Strategy

| Layer | What | How |
|---|---|---|
| Unit | services, concept visualizers, compile-error parser, trace validator | JUnit 5 + Mockito |
| Tracer | known Java programs → expected trace snapshots | golden-file tests in `javmotion-tracer/src/test/resources/programs/` |
| Repository | JPA + Flyway migrations | Testcontainers MySQL |
| API | controllers, security rules, error shapes | MockMvc / `@SpringBootTest` |
| Worker | sandbox limits: infinite loop, `System.exit`, fork bomb, file write, network call, OOM | integration tests needing Docker (tagged, run in CI) |
| Contract | trace JSON matches frontend TS types | shared JSON samples in `docs/` or `javmotion-common/src/test/resources/trace-samples/` used by both sides |

---

## 13. Deployment

### 13.1 Local dev (`docker-compose.yml`)

```yaml
services:
  mysql:   { image: mysql:8.4, ports: ["3306:3306"], env: MYSQL_DATABASE=javmotion }
  api:     { build: docker/api.Dockerfile, ports: ["8080:8080"], depends_on: [mysql, worker] }
  worker:  { build: docker/worker.Dockerfile, ports: ["8081:8081"],
             volumes: ["/var/run/docker.sock:/var/run/docker.sock", "jobs:/var/javmotion/jobs"] }
```

Build the sandbox image once: `docker build -f docker/sandbox.Dockerfile -t javmotion-sandbox:21 .`

> On Windows/Docker Desktop, the worker's job directory must be a path Docker can bind-mount into sandbox containers (same path on host and in worker container). Simplest in dev: run the worker directly on the host (`mvn -pl javmotion-worker spring-boot:run`) instead of in compose.

### 13.2 Production

| Component | Host | Notes |
|---|---|---|
| API | Render (Docker deploy) | Health check `/actuator/health`; HTTPS by Render |
| Worker | AWS EC2 (dedicated, e.g. t3.small) | Security group: inbound only from API egress / via HTTPS + token; Docker installed |
| MySQL | Managed (Aiven / Railway / AWS RDS) | TLS required |

Note: Render cannot run Docker-in-Docker, which is why the worker cannot live on Render.

### 13.3 CI/CD (GitHub Actions)

- `backend-ci.yml`: on PR — `mvn -B verify` (unit + Testcontainers), build sandbox image, run sandbox security tests.
- `backend-deploy.yml`: on `main` — Render deploy hook for API; build + push worker/sandbox images to GHCR; SSH/SSM deploy to EC2.

---

## 14. Observability

- Structured JSON logs (Logback + logstash encoder) with `traceId` per request and `executionId` per job.
- Never log user source code or stdout at INFO level.
- Metrics (Micrometer): executions by status, queue wait time, run duration, sandbox concurrency, trace size.
- Phase 8: ship logs/metrics to a hosted stack (Grafana Cloud / CloudWatch).

---

## 15. Phase Mapping

| Phase | Backend deliverables |
|---|---|
| 1 Foundation | Maven multi-module skeleton, `api` boots, health endpoint, CORS, Flyway V1, compose with MySQL |
| 2 Basic visualization | `javmotion-common/trace`, `api/visualizer` string/array/loop generators |
| 3 Compiler | Worker + sandbox image, `POST /executions` RUN mode, output + compile error parsing, timeouts |
| 4 Debugging | `javmotion-tracer` (JDI), VISUALIZE mode, trace storage, limits |
| 5 Advanced viz | Heap object graph, String Pool flag, recursion frames, `LIST`/`MAP` kinds |
| 6 Users & learning | Auth, programs CRUD, history, lessons, challenges, progress |
| 7 Advanced | AI explanations (send trace summary to LLM), more DSA generators, new language tracers behind same trace schema |
| 8 Production | gVisor, monitoring, load tests, security audit |

Phase 6 auth can be pulled earlier if saved programs are wanted sooner; the execution API already supports anonymous use, so ordering is flexible.
