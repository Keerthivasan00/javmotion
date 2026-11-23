package com.javmotion.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.javmotion.api.execution.ExecutionController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

/**
 * Full application context — catches wiring problems the slice tests cannot (e.g. a missing
 * auto-configured bean). Needs a database, so it runs only when DB_URL is set.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:mysql:.+")
class ApiContextLocalMySqlTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextStarts() {
        assertThat(context.getBean(ExecutionController.class)).isNotNull();
    }
}
