package com.javmotion.api.visualizer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class VisualizerControllerTest {

    @Autowired
    MockMvc mvc;

    private ResultActions request(String path, String json) throws Exception {
        return mvc.perform(post("/api/v1/visualizer/" + path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void stringReverseReturnsCodeAndTrace() throws Exception {
        request("string-reverse", "{\"input\":\"hi\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("string-reverse"))
                .andExpect(jsonPath("$.code").value(containsString("String s = \"hi\";")))
                .andExpect(jsonPath("$.trace.schemaVersion").value(1))
                .andExpect(jsonPath("$.trace.source").value("CONCEPT"))
                .andExpect(jsonPath("$.trace.steps[0].event").value("CALL"))
                .andExpect(jsonPath("$.trace.steps[-1:].stdout").value("ih\n"))
                // NON_NULL: no nulls leak into the JSON the frontend validates
                .andExpect(jsonPath("$.trace.steps[0].returnValue").doesNotExist());
    }

    @Test
    void arraySortAndSearch() throws Exception {
        request("array", "{\"operation\":\"BUBBLE_SORT\",\"values\":[3,1,2]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace.steps[-1:].stdout").value("[1, 2, 3]\n"))
                .andExpect(jsonPath("$.trace.steps[1].heap.h1.kind").value("ARRAY"));
        request("array", "{\"operation\":\"BINARY_SEARCH\",\"values\":[1,2,3],\"target\":3}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trace.steps[-1:].stdout").value("2\n"));
    }

    @Test
    void loops() throws Exception {
        request("loops", "{\"type\":\"FOR\",\"start\":1,\"end\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("for loop"))
                .andExpect(jsonPath("$.trace.steps[-1:].stdout").value("i = 1\ni = 2\ndone\n"));
    }

    @Test
    void validationErrorsHaveFieldDetails() throws Exception {
        request("string-reverse", "{\"input\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.input").exists());
        request("string-reverse", "{\"input\":\"héllo\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.input").value("must contain printable ASCII characters only"));
        request("array", "{\"operation\":\"BUBBLE_SORT\",\"values\":[1,2,3,4,5,6,7,8,9,10,11,12,13]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.values").exists());
    }

    @Test
    void unknownEnumIsMalformedRequest() throws Exception {
        request("array", "{\"operation\":\"QUICK_SORT\",\"values\":[1]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void domainErrorsUseStableCodes() throws Exception {
        request("array", "{\"operation\":\"BINARY_SEARCH\",\"values\":[2,1],\"target\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ARRAY_NOT_SORTED"));
        request("loops", "{\"type\":\"FOR\",\"start\":-100,\"end\":100}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ITERATIONS"));
    }
}
