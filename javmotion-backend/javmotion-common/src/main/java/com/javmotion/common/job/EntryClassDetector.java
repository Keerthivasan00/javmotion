package com.javmotion.common.job;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the class to run in a single-file program (backend doc §4.4): the {@code public class}
 * that declares {@code public static void main(String[] ...)}. The source file must be named after
 * it, because javac requires a public class to live in {@code <Name>.java}.
 *
 * <p>Comments, strings and char literals are blanked out first, so a {@code "public class X"}
 * inside a string or comment is never picked.
 */
public final class EntryClassDetector {

    public static final String FALLBACK = "Main";

    private static final Pattern PUBLIC_CLASS =
            Pattern.compile("\\bpublic\\s+(?:(?:final|abstract|strictfp)\\s+)*class\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern MAIN_METHOD = Pattern.compile(
            "\\bpublic\\s+static\\s+(?:final\\s+)?void\\s+main\\s*\\(\\s*(?:final\\s+)?String\\s*"
                    + "(?:\\[\\s*]\\s*[A-Za-z_$][\\w$]*|\\.\\.\\.\\s*[A-Za-z_$][\\w$]*|[A-Za-z_$][\\w$]*\\s*\\[\\s*])\\s*\\)");
    private static final Pattern ANY_CLASS = Pattern.compile("\\bclass\\s+([A-Za-z_$][\\w$]*)");

    private EntryClassDetector() {}

    public static String detect(String source) {
        String code = stripCommentsAndLiterals(source);

        // Prefer the public top-level class whose body contains main.
        Matcher m = PUBLIC_CLASS.matcher(code);
        String firstPublic = null;
        while (m.find()) {
            String name = m.group(1);
            if (firstPublic == null) firstPublic = name;
            int bodyStart = code.indexOf('{', m.end());
            if (bodyStart < 0) continue;
            int bodyEnd = matchingBrace(code, bodyStart);
            if (MAIN_METHOD.matcher(code.substring(bodyStart, bodyEnd)).find()) return name;
        }
        if (firstPublic != null) return firstPublic; // javac will then report the missing main

        // No public class: any class with main can run from a file of the same name.
        Matcher any = ANY_CLASS.matcher(code);
        while (any.find()) {
            int bodyStart = code.indexOf('{', any.end());
            if (bodyStart < 0) continue;
            int bodyEnd = matchingBrace(code, bodyStart);
            if (MAIN_METHOD.matcher(code.substring(bodyStart, bodyEnd)).find()) return any.group(1);
        }
        return FALLBACK;
    }

    private static int matchingBrace(String code, int open) {
        int depth = 0;
        for (int i = open; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i + 1;
        }
        return code.length();
    }

    /** Replaces comments and string/char/text-block contents with spaces, keeping offsets. */
    static String stripCommentsAndLiterals(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            char next = i + 1 < s.length() ? s.charAt(i + 1) : '\0';
            if (c == '/' && next == '/') {
                while (i < s.length() && s.charAt(i) != '\n') i = blank(out, i);
            } else if (c == '/' && next == '*') {
                int end = s.indexOf("*/", i + 2);
                int stop = end < 0 ? s.length() : end + 2;
                while (i < stop) i = blank(out, i, s);
            } else if (s.startsWith("\"\"\"", i)) {
                int end = s.indexOf("\"\"\"", i + 3);
                int stop = end < 0 ? s.length() : end + 3;
                while (i < stop) i = blank(out, i, s);
            } else if (c == '"' || c == '\'') {
                out.append(' ');
                i++;
                while (i < s.length() && s.charAt(i) != c && s.charAt(i) != '\n') {
                    if (s.charAt(i) == '\\' && i + 1 < s.length()) {
                        out.append("  ");
                        i += 2;
                    } else {
                        out.append(' ');
                        i++;
                    }
                }
                if (i < s.length()) {
                    out.append(s.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static int blank(StringBuilder out, int i) {
        out.append(' ');
        return i + 1;
    }

    private static int blank(StringBuilder out, int i, String s) {
        out.append(s.charAt(i) == '\n' ? '\n' : ' ');
        return i + 1;
    }
}
