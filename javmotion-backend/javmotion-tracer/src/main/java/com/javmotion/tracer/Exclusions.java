package com.javmotion.tracer;

/** JDK packages that are never stepped into and whose objects stay opaque (doc §6.2). */
final class Exclusions {

    static final String[] PATTERNS = {"java.*", "javax.*", "sun.*", "jdk.*", "com.sun.*"};

    private Exclusions() {}

    static boolean isExcluded(String className) {
        for (String p : PATTERNS) {
            if (className.startsWith(p.substring(0, p.length() - 1))) return true;
        }
        return false;
    }
}
