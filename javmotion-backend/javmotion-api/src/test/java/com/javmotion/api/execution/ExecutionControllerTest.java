package com.javmotion.api.execution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.javmotion.api.common.error.ApiException;
import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExecutionController.class)
class ExecutionControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ExecutionService service;

    @MockitoBean
    ExecutionDispatcher dispatcher;

    private static Execution finished() {
        Execution e = Execution.queued(ExecutionMode.RUN, "class A {}", null, "h", Instant.EPOCH);
        e.start(Instant.EPOCH);
        e.finish(new ExecutionResult(ExecutionStatus.COMPILE_ERROR, "", "err",
                "[{\"line\":3,\"column\":7,\"message\":\"oops\"}]", null, 5, null), Instant.EPOCH);
        return e;
    }

    @Test
    void createReturns202WithTokenAndDispatches() throws Exception {
        Execution e = Execution.queued(ExecutionMode.RUN, "class A {}", null, "h", Instant.EPOCH);
        given(service.create(eq("class A {}"), eq(ExecutionMode.RUN), isNull()))
                .willReturn(new ExecutionService.Created(e, "raw-token"));

        mvc.perform(post("/api/v1/executions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCode\":\"class A {}\",\"language\":\"java\",\"mode\":\"RUN\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(e.getId()))
                .andExpect(jsonPath("$.accessToken").value("raw-token"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
        verify(dispatcher).dispatch(e.getId());
    }

    @Test
    void validatesTheRequest() throws Exception {
        mvc.perform(post("/api/v1/executions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceCode\":\"\",\"language\":\"python\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.sourceCode").exists())
                .andExpect(jsonPath("$.errors.language").value("only java is supported"))
                .andExpect(jsonPath("$.errors.mode").exists());
        verify(dispatcher, never()).dispatch(any());
    }

    @Test
    void outputParsesCompileErrors() throws Exception {
        Execution e = finished();
        given(service.get(e.getId(), "tok")).willReturn(e);

        mvc.perform(get("/api/v1/executions/{id}/output", e.getId()).header(ExecutionController.TOKEN_HEADER, "tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPILE_ERROR"))
                .andExpect(jsonPath("$.compileErrors[0].line").value(3))
                .andExpect(jsonPath("$.compileErrors[0].column").value(7));
    }

    @Test
    void unknownOrForbiddenIs404() throws Exception {
        given(service.get("x", null)).willThrow(ApiException.notFound("Execution"));
        mvc.perform(get("/api/v1/executions/x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
