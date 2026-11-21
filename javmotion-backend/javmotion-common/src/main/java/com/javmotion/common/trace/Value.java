package com.javmotion.common.trace;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A variable / field / element value. Serialized with a {@code kind} discriminator:
 * <pre>
 * { "kind": "PRIMITIVE", "type": "int", "value": 5 }
 * { "kind": "REF", "ref": "h3" }
 * { "kind": "NULL" }
 * </pre>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Value.Primitive.class, name = "PRIMITIVE"),
        @JsonSubTypes.Type(value = Value.Ref.class, name = "REF"),
        @JsonSubTypes.Type(value = Value.Null.class, name = "NULL")
})
public sealed interface Value {

    record Primitive(String type, Object value) implements Value {}

    record Ref(String ref) implements Value {}

    record Null() implements Value {}

    static Value ofInt(int v) {
        return new Primitive("int", v);
    }

    static Value ofChar(char v) {
        return new Primitive("char", String.valueOf(v));
    }

    static Value ofBoolean(boolean v) {
        return new Primitive("boolean", v);
    }

    static Value ref(String heapId) {
        return new Ref(heapId);
    }

    static Value nullValue() {
        return new Null();
    }
}
