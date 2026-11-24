# JavMotion Backend — Implementation Checklist

Architecture: [docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md)

**Legend:** `[ ]` not started · `[~]` partial (note what is left) · `[x]` done

**Next up:** Restart the local API from a fresh build and check VISUALIZE end to end, then frontend Step 7. Backend leftovers: rate limiting (Bucket4j), request size limit, orphan container sweeper, Docker runner + security tests (need Docker). Local dev: MySQL80 on port **3300**; worker with `WORKER_RUNNER=host` (no sandbox!).

**Last updated:** 2026-11-21

---

## Step B0 — JDI Spike (risk check, do right after frontend Step 3)

- [x] Tiny standalone Java program: `spikes/jdi-spike/src/JdiSpike.java` (uses ProcessBuilder + `SocketListen`, not `LaunchingConnector` — see README finding 2)
- [x] Line-step through user code, locals + `this` + heap BFS + CALL/RETURN (return values) + uncaught EXCEPTION + stdout per step
- [x] Output a JSON trace matching schema §6 — 6 samples; real traces saved as `jdi-*.json` fixtures, pass frontend Zod + backend `TraceSamplesTest`
- [~] Run the same thing inside a Docker container (`eclipse-temurin:21-jdk`). `Dockerfile` + `docker-test.sh` (full sandbox limits) written. **Left:** actually run it — Docker not installed on dev machine
- [x] Note findings / problems — full list in `spikes/jdi-spike/README.md`

## Step B1 — Project Setup (Phase 1)

- [x] Parent `pom.xml` (Java 21, Spring Boot 4.2.0 parent, modules list) + Maven wrapper
- [x] Module `javmotion-common` (plain Java)
- [x] Module `javmotion-api` (Spring Boot: WebMVC, Validation, Actuator)
- [x] Skeleton modules `javmotion-worker` (Boot app, `GET /internal/ping`, port 8081) and `javmotion-tracer` (stub main)
- [x] Package-by-feature layout: `config/`, `common/`, `system/`, `user/`, `program/`, `execution/`, `visualizer/`, `learning/`
- [x] `application.yml` + `application-dev.yml` + `application-prod.yml` (env vars)
- [x] CORS config (`CORS_ALLOWED_ORIGINS`, default `localhost:5173`)
- [x] Global error handler: ProblemDetail + `code` (`ApiException`, `GlobalExceptionHandler`)
- [x] `GET /actuator/health` + `GET /api/v1/system/ping` working
- [x] `.gitignore`, README
- [x] Tests: `SystemControllerTest` (ping, CORS, 404)

## Step B2 — Concept Visualizers (Phase 2)

- [x] Trace model in `javmotion-common/trace`: `Trace`, `TraceStep`, `Frame`, `Value`, `HeapObject`, `EventType`, `Highlight` (+ `TraceJsonTest`)
- [x] `ConceptVisualizer<I>` interface — returns `ConceptResult { key, title, code, trace }` (code needed by frontend; doc §3.2/§7.2 updated). Shared `ConceptTraceBuilder` follows JDI step semantics
- [x] `POST /api/v1/visualizer/string-reverse`
- [x] `POST /api/v1/visualizer/array` (bubble sort, selection sort, linear search, binary search)
- [x] `POST /api/v1/visualizer/loops` (for, while, do-while, nested; counts down when start > end)
- [x] Input validation + size caps (Bean Validation + `TARGET_REQUIRED`, `ARRAY_NOT_SORTED`, `TOO_MANY_ITERATIONS`; `MALFORMED_REQUEST` for bad JSON/enum)
- [x] Shared trace sample JSONs in `javmotion-common/src/test/resources/trace-samples/` (same as frontend fixtures) + `TraceSamplesTest` (deserialize + round-trip)
- [x] Unit tests for each generator (`ConceptVisualizersTest` incl. random-input well-formedness, `VisualizerControllerTest`)

## Step B3 — Database Setup (needed from Phase 3)

- [x] Add Spring Data JPA, MySQL driver, Flyway (`spring-boot-starter-flyway` + `flyway-mysql`)
- [x] `docker-compose.yml` with MySQL 8 (written, not run — no Docker). Local alternative: `scripts/create-local-db.sql`
- [x] `V1__init.sql`: `executions`, `execution_traces` (+ `Execution` entity with status machine, `ExecutionTrace`, repositories)
- [x] `ddl-auto=validate` — verified against local MySQL 8.0.37 (Flyway applied V1, Hibernate validation passed, API starts)
- [~] Testcontainers MySQL test setup (`ExecutionPersistenceTestcontainersTest`, skips without Docker) + `ExecutionPersistenceLocalMySqlTest` (runs when `DB_URL` set — **passes** on local MySQL). **Left:** run the Testcontainers variant once Docker exists. Controller tests moved to `@WebMvcTest` so they need no DB

## Step B4 — Worker + Sandbox + Compiler (Phase 3)

- [~] `docker/sandbox.Dockerfile` (temurin 21 JDK, non-root user, `run.sh`) written. **Left:** build + run (needs Docker and the real tracer jar)
- [x] `javmotion-worker` Spring Boot app, `POST /internal/jobs` with `X-Worker-Token` (constant-time compare, 401 otherwise)
- [x] Workspace create/cleanup (`src` ro, `out` rw, delete on close) — `Workspace`
- [x] `DockerCommandBuilder` with all limits (network none, memory, cpus, pids, read-only, cap-drop, non-root) + `WorkerProperties`
- [x] Wall-clock timeout + `docker kill` — `ProcessRunner` (+ force-kill of the process tree); timeout verified with the host runner
- [x] Concurrency limit (semaphore, 429 when full; API retries 3×)
- [x] Output truncation (64 KB) — `BoundedOutput` (UTF-8 safe cut), wired into `ProcessRunner`; over the limit → `LIMIT_EXCEEDED`
- [ ] Orphan container sweeper
- [x] Entry class detection (`public class X` with `main`) — `common/job/EntryClassDetector`
- [x] API: `POST /api/v1/executions` (RUN mode) → 202 `{ id, accessToken, status }`
- [x] API: `ExecutionDispatcher` (@Async, bounded executor) + `WorkerClient` (RestClient); keeps a cancel that happens mid-run
- [x] API: `GET /executions/{id}`, `/output`, `/events` (gunzipped trace), `DELETE /executions/{id}` (cancel if running, else delete)
- [x] Status machine (QUEUED → RUNNING → COMPLETED / COMPILE_ERROR / RUNTIME_ERROR / TIMEOUT / LIMIT_EXCEEDED / FAILED / CANCELLED) — transitions on the `Execution` entity
- [x] javac error parser → `compileErrors[{line, column, message}]` — `common/job/JavacErrorParser` (tested against real javac output)
- [x] Per-execution access token (anonymous access control) — only SHA-256 stored; wrong token = 404
- [~] Source size limit (64 KB, counted in UTF-8 bytes) + stdin 8 KB. **Left:** request body size limit (128 KB)
- [ ] Rate limiting (Bucket4j)
- [ ] Sandbox security tests: infinite loop, `System.exit`, fork bomb, file write, network call, OOM (needs Docker)
- [x] Extra: `HostDevRunner` — DEV ONLY, no isolation; `WORKER_RUNNER=host`, refuses under prod profile. Used for local end-to-end runs without Docker

## Step B5 — Tracer / Visualize Mode (Phase 4)

- [x] `javmotion-tracer`: `TracerMain` (ProcessBuilder + SocketListen instead of LaunchingConnector — spike finding); passes program stdout/stderr + exit code through; exit 70 = tracer failed
- [x] StepRequest (STEP_LINE, STEP_INTO) + class exclusions; stepping stops when main returns
- [x] CALL (breakpoint at main + deeper stack) / RETURN via MethodExit (+ return value)
- [x] ExceptionRequest → EXCEPTION (type + detailMessage)
- [x] `StateCapture` (`HeapCapture` + frame capture): frames with stable ids, locals + `this`, heap BFS with stable `h<uniqueID>`, cycles, NaN as string
- [x] stdout → cumulative `stdout` per step (synchronous drain per event, not a pump thread)
- [x] Limits: 5000 steps, 10 MB, 200 objects/step, 100 array elements, 1000 chars + own time budget (bounded event wait — a one-line endless loop sends no events)
- [x] Trace written as JSON (Jackson + common model) to the given path
- [~] Sandbox image includes `tracer.jar` (shaded `javmotion-tracer.jar`); `run.sh` VISUALIZE mode (`javac -g`, 7 s tracer budget). **Left:** build/run the image (Docker)
- [x] Worker validates trace (size ≤ 11 MB + parse into the model) — `TraceAttacher`; truncated → LIMIT_EXCEEDED with the partial trace
- [x] API stores gzipped trace in `execution_traces` (dispatcher, done in B4)
- [x] `GET /executions/{id}/events` (done in B4)
- [x] Tracer tests on real programs (loop line shape = frontend fixtures, recursion, exception, heap, step/time limits, stdin EOF) + worker host-runner VISUALIZE tests

## Step B6 — Advanced Visualization Support (Phase 5)

- [ ] `interned` flag for Strings (String Pool)
- [ ] OBJECT fields capture (OOP, inheritance)
- [ ] Recursion: deep stacks within limits
- [ ] `LIST` / `MAP` kinds for ArrayList, HashMap, etc.

## Step B7 — Users, Programs, Learning (Phase 6)

- [ ] Spring Security + stateless JWT (15 min access token)
- [ ] `users`, `refresh_tokens` migration
- [ ] `POST /auth/register`, `/auth/login`, `/auth/refresh` (rotation), `/auth/logout`
- [ ] BCrypt passwords
- [ ] `GET/PUT /users/me`
- [ ] `programs` migration + CRUD `/programs` (owner-only checks in service)
- [ ] Execution history `GET /executions?page=`
- [ ] Per-user concurrent execution limit (2)
- [ ] `lessons`, `lesson_progress`, `challenges`, `challenge_submissions` migrations
- [ ] Lessons + challenges endpoints; submissions reuse execution pipeline
- [ ] Progress endpoint
- [ ] `TraceRetentionJob` (anon 24h, user traces 7d, rows 90d)

## Step B8 — Advanced Features (Phase 7)

- [ ] AI explanation endpoint (trace summary → LLM)
- [ ] More DSA concept generators
- [ ] Additional language tracer behind same schema

## Step B9 — Production (Phase 8)

- [ ] `docker/api.Dockerfile`, `docker/worker.Dockerfile`
- [ ] Render deploy for API (health check)
- [ ] EC2 worker host (dedicated, Docker, security group: API only)
- [ ] Managed MySQL with TLS
- [ ] GitHub Actions: `mvn verify` + sandbox tests on PR
- [ ] GitHub Actions: deploy API (Render hook) + worker images (GHCR → EC2)
- [ ] Structured JSON logs (no user code at INFO), Micrometer metrics
- [ ] gVisor (`runsc`) for sandbox
- [ ] Security audit + load test

---

## Progress Notes

<!-- Add a dated line when finishing a session: what was done, what is partial, any blockers. -->
- 2026-11-21: Architecture docs written. Checklist created. Nothing implemented yet.
- 2026-11-21: Boilerplate done — Step B1 complete, trace model (B2) added. `./mvnw verify` passes (5 tests). Spring Boot 4.2 (Jackson 3 = `tools.jackson.*`; annotations still `com.fasterxml.jackson.annotation`). Notes: `JAVA_HOME` points to missing `jdk-21` — use `jdk-24` or install 21. Port 8080 is taken by a local Apache `httpd` — stop it or run with `PORT=8090`.
- 2026-11-21: Frontend fixtures copied to `trace-samples/` (for-loop, array-bubble-sort, string-reverse). No backend code changed.
- 2026-11-21: Frontend Step 3 done. `trace-samples/` re-copied after fixture regeneration (loop headers now revisited like real JDI traces). B0 is next.
- 2026-11-21: Step B0 spike done except the Docker run (Docker not installed). Key findings (details in `spikes/jdi-spike/README.md`): default CommandLineLaunch uses shmem on Windows and costs ~2.2 s per run → use ProcessBuilder + SocketListen (~0.25 s total); loop until VMDisconnect; drain stdout synchronously per event; stop stepping when main returns; compile with `-g`; ~2,000 steps/s; **size limit hits before step limit** (10 MB at ~3,800 steps for a 50-element array, gzip ~100×). Docker risks to check: pids-limit 64 with two JVMs, 256 MB shared. `JAVA_HOME` now points to jdk-24 (fixed).
- 2026-11-21: Step B2 complete. 3 concept endpoints, 8 operations. Every trace checked by `ConceptTraceAssertions` (lines point at real code, refs exist, highlight indexes in range, stdout only grows). 59 tests pass (`mvnw verify`). Real responses recorded into frontend `src/test/fixtures/concepts/` and validated there with Zod + loop detection.
- 2026-11-21: Step B3 code done using the local MySQL path (no Docker): deps, V1 migration, entities, repositories, compose file, local DB script. Not yet run against MySQL — waiting for `create-local-db.sql` (local MySQL listens on **3300**). B4 Docker-free pieces: EntryClassDetector, JavacErrorParser, DockerCommandBuilder, BoundedOutput, sandbox Dockerfile + run.sh. 106 tests pass, 4 DB tests skipped. Note: the API now needs MySQL to start (concept endpoints included).
- 2026-11-23: Local MySQL set up (service had stopped after a reboot; port 3300). V1 migration applied, schema validation passes, persistence tests pass, API starts and serves concept endpoints. Found and fixed: Boot 4 `@DataJpaTest` did not roll back, so test rows were committed — contract class is now `@Transactional` (leftover rows deleted).
- 2026-11-23: B4 pipeline works end to end locally (API on 8090 + local MySQL + worker with host runner): hello world COMPLETED, compile error with line/column, infinite loop TIMEOUT at 5 s, stdin, wrong token 404, delete 204, workspaces cleaned. Found: Boot 4 has no `RestClient.Builder` bean without its starter — WorkerClient now uses `RestClient.builder()`; added `ApiContextLocalMySqlTest` (full context, runs with DB_URL) to catch wiring like that. 140 tests, 4 skipped without DB.
- 2026-11-24: Step B5 done: real JDI tracer in `javmotion-tracer` (shaded jar), worker VISUALIZE on host + docker runners with trace validation. Found and fixed: (1) a one-line endless loop sends no STEP_LINE events, so the tracer waited forever — event wait is now bounded by the deadline; (2) killing the debuggee right after VMDisconnect turned exit 0 into 1 — now waits for a natural exit first. Tracer tests have `@Timeout`. End-to-end through the API not re-checked: `mvnw verify` overwrote the API jar while the local API was running from it (repackage then failed on the locked file), so that API instance must be restarted from a fresh build.
