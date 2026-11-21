# JDI spike (Step B0)

Throwaway proof that JDI can record a JavMotion trace (schema v1) of a real Java program.
One file, no dependencies: [src/JdiSpike.java](src/JdiSpike.java). The real tracer (Step B5)
goes in `javmotion-tracer` and uses the `javmotion-common` trace model + Jackson instead.

## Run

```sh
./run-all.sh                 # JDK 21+ on PATH; writes out/traces/<sample>.json
./docker-test.sh             # same, inside eclipse-temurin:21-jdk with the full sandbox limits
```

Samples in `samples/*/Main.java`: `loop` (same code as the frontend for-loop demo), `recursion`,
`strings` (string concat, array swap, 150-element array), `exception` (uncaught AIOOBE),
`objects` (cyclic linked nodes, boxed Integer, NaN), `heavy` (hits the limits).

Real traces are copied (with `\r\n` → `\n`) to
`javmotion-frontend/src/test/fixtures/traces/jdi-*.json` and
`javmotion-common/src/test/resources/trace-samples/jdi-*.json`, and pass the frontend Zod schema
and the backend Jackson model (`TraceSamplesTest`).

## Results (Windows 11, JDK 24 runtime, `javac --release 21`, 2026-11-21)

| Sample | Steps | Size | Total time |
|---|---|---|---|
| loop | 15 | 4.8 KB | ~250 ms |
| recursion | 21 | 9.6 KB | ~250 ms |
| strings | 20 | 24 KB | ~340 ms |
| heavy | 3,820 (truncated by size) | 10.4 MB | ~2.0 s |

About 2,000 steps/s. JVM launch + connect ≈ 130 ms.

## Findings → what Step B5 must do

1. **It works, and line semantics match the fake fixtures.** A step is reported *before* its line
   runs; a `for` header line is revisited after every iteration (update + condition), and the last
   revisit is followed by the line after the loop. The frontend loop detection (back-edges) works
   on real traces unchanged.
2. **Don't use the default `CommandLineLaunch` connector.** On Windows it uses the shared-memory
   transport and disconnect took **~2.2 s per run**. Instead start the debuggee with
   `ProcessBuilder` + `-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=…` and accept it
   on a `SocketListen` connector. Same path on Linux, and we own the `Process` (pipes, exit code).
   Run time dropped from ~2.4 s to ~0.25 s.
3. **Keep the event loop running until `VMDisconnectEvent`**, and always `resume()` the event set
   (including the VMDeath one).
4. **stdout: drain synchronously at each event, no pump thread.** The debuggee is suspended when
   an event arrives, so `available()` bytes are exactly what it printed so far. Output lands on the
   right step deterministically; `print` without a newline also shows up right away.
5. **Stop stepping when `main` returns** (delete step/method-exit requests), so the agent doesn't
   step through JVM shutdown code.
6. **Frame ids:** JDI frames have no identity. A shadow stack keyed by
   `class#method+signature` per depth gives stable ids; recursion gets a new id per activation
   because every return passes through a caller step first.
7. **Must compile with `javac -g`**, or `visibleVariables()` throws `AbsentInformationException`
   (no locals). `this` comes from `StackFrame.thisObject()`.
8. **Heap ids from `uniqueID()` are stable** across steps (e.g. `rev` gets a new id each concat,
   `arr` keeps its id). Cycles are fine with a visited set.
9. **Exception message:** read the `detailMessage` field directly. `invokeMethod(getMessage)` on a
   suspended thread is slow and risky. The `ExceptionRequest` must not have class exclusions, or
   exceptions thrown inside JDK code are lost.
10. **NaN / Infinity** aren't valid JSON numbers, so they are sent as strings (`"NaN"`). The schema
    already allows string primitive values.
11. **Size is the real limit, not steps.** Full snapshots of a 50-element array reach 10 MB at
    ~3,800 steps (13.7 MB at 5,000). Gzip makes that **~0.13 MB (~100×)**. So: enforce the 10 MB cap
    in the tracer (done here), store gzipped (already planned), send `Content-Encoding: gzip`,
    and consider delta encoding (schema v2) only if browser memory becomes a problem.
12. **Windows line endings**: debuggee stdout is `\r\n` on Windows. Not an issue in the Linux
    sandbox; fixtures were normalized to `\n`.

## Not verified yet — Docker (Docker is not installed on the dev machine)

`Dockerfile` + `docker-test.sh` are ready. Risks to check when it runs:

- `--pids-limit 64` with **two JVMs** (tracer + debuggee, ~20 threads each). May need ~128,
  or fewer JVM threads (`-XX:CICompilerCount=2`, `-XX:+UseSerialGC` is already set).
- `--memory 256m` shared by both JVMs (debuggee `-Xmx128m`, tracer `-Xmx64m` + metaspace).
- JDWP over loopback with `--network none` (loopback should still exist).
- `javac` output on the `noexec` `/tmp` tmpfs (class files are read, not executed, so it should work).
