package com.javmotion.api.execution;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

/**
 * Same checks against an existing MySQL (e.g. a local install without Docker).
 * Runs only when DB_URL is set, e.g. DB_URL=jdbc:mysql://localhost:3300/javmotion
 * DB_USERNAME=javmotion DB_PASSWORD=javmotion. Each test rolls back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:mysql:.+")
class ExecutionPersistenceLocalMySqlTest extends ExecutionPersistenceContract {}
