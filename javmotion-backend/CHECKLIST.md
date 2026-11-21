# JavMotion Backend — Implementation Checklist

Architecture: [docs/architecture/backend-architecture.md](docs/architecture/backend-architecture.md)

**Legend:** `[ ]` not started · `[~]` partial (note what is left) · `[x]` done

**Next up:** Wait — frontend Step 3 comes first (Step 2 done). Then Step B0 (JDI spike).

**Last updated:** 2026-11-21

---

## Step B0 — JDI Spike (risk check, do right after frontend Step 3)

- [ ] Tiny standalone Java program: launch a sample `Main` via JDI `LaunchingConnector`
- [ ] Line-step through user code, print line + local variables per step
- [ ] Output a JSON trace matching schema §6 for a simple loop program
- [ ] Run the same thing inside a Docker container (`eclipse-temurin:21-jdk`)
- [ ] Note findings / problems in Progress Notes below

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
- [ ] `ConceptVisualizer<I>` interface
- [ ] `POST /api/v1/visualizer/string-reverse`
- [ ] `POST /api/v1/visualizer/array` (bubble sort, linear search, binary search, …)
- [ ] `POST /api/v1/visualizer/loops` (for, while, do-while, nested)
- [ ] Input validation + size caps
- [~] Shared trace sample JSONs in `javmotion-common/src/test/resources/trace-samples/` (same as frontend fixtures). Files copied from frontend. **Left:** a test that deserializes them into `Trace`
- [ ] Unit tests for each generator

## Step B3 — Database Setup (needed from Phase 3)

- [ ] Add Spring Data JPA, MySQL driver, Flyway
- [ ] `docker-compose.yml` with MySQL 8
- [ ] `V1__init.sql`: `executions`, `execution_traces`
- [ ] `ddl-auto=validate`
- [ ] Testcontainers MySQL test setup

## Step B4 — Worker + Sandbox + Compiler (Phase 3)

- [ ] `docker/sandbox.Dockerfile` (temurin 21 JDK, non-root user, `run.sh`)
- [ ] `javmotion-worker` Spring Boot app, `POST /internal/jobs` with `X-Worker-Token`
- [ ] Workspace create/cleanup (`src` ro, `out` rw, delete in `finally`)
- [ ] `DockerCommandBuilder` with all limits (network none, memory, cpus, pids, read-only, cap-drop, non-root)
- [ ] Wall-clock timeout + `docker kill`
- [ ] Concurrency limit (bounded pool, 429 when full)
- [ ] Output truncation (64 KB)
- [ ] Orphan container sweeper
- [ ] Entry class detection (`public class X` with `main`)
- [ ] API: `POST /api/v1/executions` (RUN mode) → 202 `{ id, accessToken }`
- [ ] API: `ExecutionDispatcher` (@Async) + `WorkerClient` (RestClient)
- [ ] API: `GET /executions/{id}`, `/output`, `DELETE /executions/{id}`
- [ ] Status machine (QUEUED → RUNNING → COMPLETED / COMPILE_ERROR / RUNTIME_ERROR / TIMEOUT / LIMIT_EXCEEDED / FAILED / CANCELLED)
- [ ] javac error parser → `compileErrors[{line, column, message}]`
- [ ] Per-execution access token (anonymous access control)
- [ ] Source size limit (64 KB), request size limit (128 KB)
- [ ] Rate limiting (Bucket4j)
- [ ] Sandbox security tests: infinite loop, `System.exit`, fork bomb, file write, network call, OOM

## Step B5 — Tracer / Visualize Mode (Phase 4)

- [ ] `javmotion-tracer`: `TracerMain` (LaunchingConnector)
- [ ] StepRequest (STEP_LINE, STEP_INTO) + class exclusions
- [ ] MethodEntry / MethodExit → CALL / RETURN (+ return value)
- [ ] ExceptionRequest → EXCEPTION
- [ ] `StateCapture`: frames, locals, heap BFS with stable `h<id>`
- [ ] stdout pump → cumulative `stdout` per step
- [ ] Limits: 5000 steps, 10 MB, 200 objects/step, 100 array elements, 1000 chars
- [ ] `TraceWriter` → `/sandbox/out/trace.json`
- [ ] Sandbox image includes `tracer.jar`; `run.sh` VISUALIZE mode (`javac -g`)
- [ ] Worker validates trace (size + schema) — untrusted
- [ ] API stores gzipped trace in `execution_traces`
- [ ] `GET /executions/{id}/events`
- [ ] Golden-file tracer tests (sample programs → expected traces)

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
