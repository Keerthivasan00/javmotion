package com.javmotion.worker.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.javmotion.common.job.ExecutionStatus;
import com.javmotion.common.job.JobResult;
import com.javmotion.worker.job.JobService;
import com.javmotion.worker.sandbox.WorkerProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(JobController.class)
@TestPropertySource(properties = "worker.token=secret-token")
@Import(JobControllerTest.Props.class)
class JobControllerTest {

    @TestConfiguration
    @EnableConfigurationProperties(WorkerProperties.class)
    static class Props {}

    @Autowired
    MockMvc mvc;

    @MockitoBean
    JobService jobs;

    private static final String BODY = """
            {"jobId":"123e4567-e89b-12d3-a456-426614174000","mode":"RUN","sourceCode":"class A {}"}""";

    private ResultActions call(String token) throws Exception {
        var req = post("/internal/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY);
        if (token != null) req.header(JobController.TOKEN_HEADER, token);
        return mvc.perform(req);
    }

    @Test
    void rejectsMissingOrWrongToken() throws Exception {
        call(null).andExpect(status().isUnauthorized());
        call("wrong").andExpect(status().isUnauthorized());
    }

    @Test
    void runsWithTheRightToken() throws Exception {
        given(jobs.run(any())).willReturn(
                new JobResult(ExecutionStatus.COMPLETED, "hi\n", "", List.of(), 0, 12, false, null, null));
        call("secret-token")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stdout").value("hi\n"));
    }

    @Test
    void busyIs429() throws Exception {
        given(jobs.run(any())).willThrow(new JobService.BusyException());
        call("secret-token").andExpect(status().isTooManyRequests());
    }
}
