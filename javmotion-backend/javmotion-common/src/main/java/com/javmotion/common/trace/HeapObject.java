package com.javmotion.common.trace;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.Map;

/** An object living on the heap, referenced from {@link Value.Ref}. Discriminated by {@code kind}. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = HeapObject.StringObj.class, name = "STRING"),
        @JsonSubTypes.Type(value = HeapObject.ArrayObj.class, name = "ARRAY"),
        @JsonSubTypes.Type(value = HeapObject.ObjectObj.class, name = "OBJECT"),
        @JsonSubTypes.Type(value = HeapObject.ListObj.class, name = "LIST"),
        @JsonSubTypes.Type(value = HeapObject.MapObj.class, name = "MAP")
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface HeapObject {

    String type();

    record StringObj(String type, String value, Boolean interned) implements HeapObject {}

    /** {@code length} set only when {@code elements} is a truncated preview. */
    record ArrayObj(String type, List<Value> elements, Integer length) implements HeapObject {}

    record ObjectObj(String type, Map<String, Value> fields) implements HeapObject {}

    record ListObj(String type, List<Value> elements) implements HeapObject {}

    record MapObj(String type, List<Entry> entries) implements HeapObject {
        public record Entry(Value key, Value value) {}
    }
}
