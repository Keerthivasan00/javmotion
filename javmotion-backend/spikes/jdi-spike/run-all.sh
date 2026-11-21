#!/bin/sh
# Compiles the spike + every sample, traces each one into $OUT/traces/<sample>.json.
# Usage: ./run-all.sh            (OUT defaults to ./out; needs JDK 21+ on PATH)
#        OUT=/tmp/out ./run-all.sh   (inside the read-only Docker container)
set -e
cd "$(dirname "$0")"
OUT=${OUT:-out}
rm -rf "$OUT"
mkdir -p "$OUT/spike" "$OUT/traces"
javac -d "$OUT/spike" src/JdiSpike.java
for dir in samples/*/; do
  name=$(basename "$dir")
  mkdir -p "$OUT/app/$name"
  javac -g --release 21 -d "$OUT/app/$name" "$dir"Main.java
  printf '%-10s ' "$name"
  # Tracer JVM gets a small heap: it shares the container's 256 MB with the debuggee (-Xmx128m).
  java -Xmx64m -XX:+UseSerialGC -cp "$OUT/spike" JdiSpike "$OUT/app/$name" Main "$OUT/traces/$name.json"
done
