package com.javmotion.api.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.javmotion.api.common.error.ApiException;
import com.javmotion.api.config.ExecutionProperties;
import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.ExecutionStatus;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExecutionServiceTest {

    private final ExecutionRepository executions = mock(ExecutionRepository.class);
    private final ExecutionService service = new ExecutionService(
            executions, mock(ExecutionTraceRepository.class), new ExecutionProperties(100, 10), Clock.systemUTC());

    @BeforeEach
    void saveReturnsArgument() {
        given(executions.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createStoresOnlyTheTokenHash() {
        ExecutionService.Created created = service.create("class A {}", ExecutionMode.RUN, null);

        assertThat(created.execution().getStatus()).isEqualTo(ExecutionStatus.QUEUED);
        assertThat(created.accessToken()).hasSizeGreaterThan(40);
        assertThat(created.execution().getAccessTokenHash())
                .hasSize(64)
                .isEqualTo(AccessTokens.hash(created.accessToken()))
                .isNotEqualTo(created.accessToken());
    }

    @Test
    void limitsCountBytesNotCharacters() {
        String tamil = "த".repeat(40); // 40 chars, 120 UTF-8 bytes
        assertThatThrownBy(() -> service.create(tamil, ExecutionMode.RUN, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "SOURCE_TOO_LARGE");
        assertThatThrownBy(() -> service.create("class A {}", ExecutionMode.RUN, "12345678901"))
                .hasFieldOrPropertyWithValue("code", "STDIN_TOO_LARGE");
    }

    @Test
    void wrongOrMissingTokenLooksLikeNotFound() {
        ExecutionService.Created created = service.create("class A {}", ExecutionMode.RUN, null);
        String id = created.execution().getId();
        given(executions.findById(id)).willReturn(Optional.of(created.execution()));

        assertThat(service.get(id, created.accessToken())).isSameAs(created.execution());
        for (String bad : new String[] {null, "", "nope"}) {
            assertThatThrownBy(() -> service.get(id, bad))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("code", "NOT_FOUND");
        }
    }

    @Test
    void deleteCancelsRunningButRemovesFinished() {
        ExecutionService.Created created = service.create("class A {}", ExecutionMode.RUN, null);
        Execution e = created.execution();
        given(executions.findById(e.getId())).willReturn(Optional.of(e));

        service.cancelOrDelete(e.getId(), created.accessToken());
        assertThat(e.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);

        service.cancelOrDelete(e.getId(), created.accessToken());
        verify(executions).delete(e);
    }
}
