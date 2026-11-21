package com.javmotion.api.visualizer;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Helpers for rendering request values into the Java code shown next to a concept trace. */
final class JavaSource {

    private JavaSource() {}

    /** Java string literal. Input is validated to printable ASCII, so only {@code \} and {@code "} need escaping. */
    static String stringLiteral(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    static String intArrayLiteral(int[] values) {
        return Arrays.stream(values).mapToObj(String::valueOf).collect(Collectors.joining(", ", "{", "}"));
    }

    static String arraysToString(int[] values) {
        return Arrays.toString(values);
    }

    /** Counting direction for a loop from {@code start} to {@code end} (inclusive). */
    record Counter(String var, int start, int end, int step) {

        boolean up() {
            return start <= end;
        }

        boolean test(int i) {
            return up() ? i <= end : i >= end;
        }

        int next(int i) {
            return up() ? i + step : i - step;
        }

        int iterations() {
            return Math.abs(end - start) / step + 1;
        }

        String condition() {
            return var + (up() ? " <= " : " >= ") + end;
        }

        String update() {
            if (step == 1) return var + (up() ? "++" : "--");
            return var + (up() ? " += " : " -= ") + step;
        }

        String forHeader() {
            return "for (int %s = %d; %s; %s) {".formatted(var, start, condition(), update());
        }
    }
}
