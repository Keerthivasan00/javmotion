package com.javmotion.common.job;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns javac's text output into {@link CompileError}s.
 *
 * <pre>
 * Main.java:5: error: ';' expected
 *         int x = 5
 *                  ^
 * </pre>
 *
 * The column comes from the caret line. Warnings and the trailing "N errors" summary are ignored.
 * Anything unrecognised is skipped rather than guessed.
 */
public final class JavacErrorParser {

    /** Never return more than this many markers (javac stops at 100 anyway). */
    static final int MAX_ERRORS = 100;

    private static final Pattern HEADER = Pattern.compile("^(?:.*[/\\\\])?[^/\\\\:]+\\.java:(\\d+): error: (.*)$");

    private JavacErrorParser() {}

    public static List<CompileError> parse(String javacOutput) {
        List<CompileError> errors = new ArrayList<>();
        if (javacOutput == null || javacOutput.isBlank()) return errors;

        String[] lines = javacOutput.replace("\r\n", "\n").split("\n");
        for (int i = 0; i < lines.length && errors.size() < MAX_ERRORS; i++) {
            Matcher m = HEADER.matcher(lines[i]);
            if (!m.matches()) continue;

            int line = Integer.parseInt(m.group(1));
            StringBuilder message = new StringBuilder(m.group(2).strip());
            int column = 1;

            // Following lines: optional extra message lines ("symbol: ...", "location: ..."),
            // the echoed source line, then the caret line. Stop at the next header.
            for (int j = i + 1; j < lines.length && !HEADER.matcher(lines[j]).matches(); j++) {
                String l = lines[j];
                int caret = l.indexOf('^');
                if (caret >= 0 && l.strip().equals("^")) {
                    column = caret + 1;
                } else if (l.startsWith("  symbol:") || l.startsWith("  location:")) {
                    message.append(" (").append(l.strip()).append(')');
                }
            }
            errors.add(new CompileError(line, column, message.toString()));
        }
        return errors;
    }
}
