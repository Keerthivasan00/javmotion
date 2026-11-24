#!/bin/sh
# Entry point inside the sandbox container.
# Usage: /sandbox/run.sh RUN|VISUALIZE <ClassName>
#   /sandbox/src  (read-only)  <ClassName>.java
#   /sandbox/out  (writable)   compile.txt, compile-failed marker, trace.json
#   /tmp          (tmpfs, noexec) compiled classes
# The worker tells a compile error apart from the program's own exit code by the
# compile-failed marker, not by the exit code.
set -u
MODE="$1"
CLASS="$2"
CLASSES=/tmp/classes
mkdir -p "$CLASSES"

if [ "$MODE" = "VISUALIZE" ]; then DEBUG_FLAG="-g"; else DEBUG_FLAG=""; fi

# -g is required in VISUALIZE mode so JDI can read local variable names.
if ! javac $DEBUG_FLAG -encoding UTF-8 -d "$CLASSES" "/sandbox/src/$CLASS.java" 2> /sandbox/out/compile.txt; then
  touch /sandbox/out/compile-failed
  exit 1
fi

# TieredStopAtLevel=1: fewer JIT compiler threads, which helps with --pids-limit.
JVM_FLAGS="-Xmx128m -XX:MaxMetaspaceSize=64m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1"

case "$MODE" in
  RUN)
    exec java $JVM_FLAGS -cp "$CLASSES" "$CLASS"
    ;;
  VISUALIZE)
    # Tracer JVM is small; it launches the debuggee with the flags above (spike finding: two JVMs
    # share the container's memory and pids budget).
    # Last arg: tracer stops itself after 7 s (worker hard timeout is 10 s) so a trace survives.
    exec java -Xmx64m -XX:+UseSerialGC -jar /opt/javmotion/tracer.jar "$CLASSES" "$CLASS" /sandbox/out/trace.json 7000
    ;;
  *)
    echo "unknown mode: $MODE" >&2
    exit 2
    ;;
esac
