package com.javmotion.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The same JSON files live in javmotion-frontend/src/test/fixtures/traces and are validated there
 * with the Zod schema. Parsing them here keeps both sides on one contract.
 */
class TraceSamplesTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {
        "for-loop", "array-bubble-sort", "string-reverse",
        "jdi-loop", "jdi-recursion", "jdi-exception", "jdi-objects", "jdi-strings"
    })
    void sampleDeserializesIntoTraceModel(String name) throws IOException {
        Trace trace;
        try (InputStream in = getClass().getResourceAsStream("/trace-samples/" + name + ".json")) {
            assertThat(in).as("sample %s exists", name).isNotNull();
            trace = mapper.readValue(in, Trace.class);
        }

        assertThat(trace.schemaVersion()).isEqualTo(Trace.SCHEMA_VERSION);
        List<TraceStep> steps = trace.steps();
        assertThat(steps).isNotEmpty();
        for (int i = 0; i < steps.size(); i++) assertThat(steps.get(i).index()).isEqualTo(i);
        assertThat(steps.getLast().event()).isEqualTo(EventType.END);

        // Round-trip must be lossless.
        Trace again = mapper.readValue(mapper.writeValueAsString(trace), Trace.class);
        assertThat(again).isEqualTo(trace);
    }
}
