package com.javmotion.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** Guards the JSON shape the frontend depends on. */
class TraceJsonTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void serializesWithKindDiscriminators() {
        Map<String, Value> locals = new LinkedHashMap<>();
        locals.put("i", Value.ofInt(0));
        locals.put("arr", Value.ref("h1"));
        locals.put("obj", Value.nullValue());

        TraceStep step = new TraceStep(
                0, EventType.LINE, 3,
                List.of(new Frame("f1", "main", "Main", 3, locals)),
                Map.of("h1", new HeapObject.ArrayObj("int[]", List.of(Value.ofInt(5)), null)),
                "", null, null, null);

        String json = mapper.writeValueAsString(Trace.concept(List.of(step)));

        assertThat(json)
                .contains("\"schemaVersion\":1")
                .contains("\"source\":\"CONCEPT\"")
                .contains("{\"kind\":\"PRIMITIVE\",\"type\":\"int\",\"value\":0}")
                .contains("{\"kind\":\"REF\",\"ref\":\"h1\"}")
                .contains("{\"kind\":\"NULL\"}")
                .contains("\"kind\":\"ARRAY\"")
                .doesNotContain("returnValue")
                .doesNotContain("\"length\"");
    }

    @Test
    void roundTrips() {
        TraceStep step = new TraceStep(
                0, EventType.END, 1, List.of(), Map.of("h1", new HeapObject.StringObj("java.lang.String", "hi", true)),
                "hi\n", null, null, null);
        Trace original = Trace.concept(List.of(step));

        Trace parsed = mapper.readValue(mapper.writeValueAsString(original), Trace.class);

        assertThat(parsed).isEqualTo(original);
    }
}
