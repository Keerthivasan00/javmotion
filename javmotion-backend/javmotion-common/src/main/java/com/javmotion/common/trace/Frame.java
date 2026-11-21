package com.javmotion.common.trace;

import java.util.Map;

/** One method stack frame. {@code locals} keeps declaration order (use LinkedHashMap). */
public record Frame(
        String id,
        String method,
        String className,
        int line,
        Map<String, Value> locals) {}
