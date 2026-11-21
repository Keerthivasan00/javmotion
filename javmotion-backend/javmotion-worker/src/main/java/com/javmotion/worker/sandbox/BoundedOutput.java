package com.javmotion.worker.sandbox;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Collects a process stream up to {@code limit} bytes and silently drops the rest, so a program
 * printing in a loop cannot exhaust worker memory. Keeps counting so we know it was truncated.
 */
public final class BoundedOutput extends OutputStream {

    private final int limit;
    private final ByteArrayOutputStream kept;
    private long total;

    public BoundedOutput(int limit) {
        this.limit = limit;
        this.kept = new ByteArrayOutputStream(Math.min(limit, 8192));
    }

    @Override
    public synchronized void write(int b) {
        if (total++ < limit) kept.write(b);
    }

    @Override
    public synchronized void write(byte[] b, int off, int len) {
        int room = (int) Math.max(0, Math.min(len, limit - total));
        if (room > 0) kept.write(b, off, room);
        total += len;
    }

    public synchronized boolean truncated() {
        return total > limit;
    }

    public synchronized long totalBytes() {
        return total;
    }

    /** Kept bytes as UTF-8, never ending in half a multi-byte character. */
    public synchronized String text() {
        byte[] bytes = kept.toByteArray();
        return new String(bytes, 0, completeUtf8Length(bytes), StandardCharsets.UTF_8);
    }

    /** Length without a trailing incomplete UTF-8 sequence (the cut can land mid-character). */
    static int completeUtf8Length(byte[] b) {
        int n = b.length;
        // Walk back over at most 3 continuation bytes (10xxxxxx) to the lead byte.
        int i = n - 1;
        int continuation = 0;
        while (i >= 0 && continuation < 3 && (b[i] & 0xC0) == 0x80) {
            i--;
            continuation++;
        }
        if (i < 0) return n;
        int lead = b[i] & 0xFF;
        int needed = lead >= 0xF0 ? 3 : lead >= 0xE0 ? 2 : lead >= 0xC0 ? 1 : 0;
        return continuation < needed ? i : n;
    }
}
