package com.javmotion.common.trace;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Visual hint used by concept visualizers, e.g. "compare cells 1 and 2 of array h7". */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Highlight(String ref, Integer index, Role role, String label) {

    public enum Role {
        compare,
        swap,
        active,
        found,
        pointer
    }
}
