#!/bin/sh
# Runs the spike with the SAME limits as the real sandbox (backend doc §5.2).
# Things to watch: --pids-limit 64 with TWO JVMs (tracer + debuggee), 256 MB for both,
# JDWP over loopback with --network none, javac writing to a noexec tmpfs.
set -e
cd "$(dirname "$0")"
docker build -t jm-jdi-spike .
docker run --rm \
  --network none \
  --memory 256m --memory-swap 256m \
  --cpus 1 \
  --pids-limit 64 \
  --read-only \
  --tmpfs /tmp:rw,size=16m,noexec \
  --cap-drop ALL \
  --security-opt no-new-privileges \
  --user 10001:10001 \
  -e OUT=/tmp/out \
  jm-jdi-spike
