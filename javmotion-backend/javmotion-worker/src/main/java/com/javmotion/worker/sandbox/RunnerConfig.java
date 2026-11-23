package com.javmotion.worker.sandbox;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Picks the sandbox runner. Docker unless {@code worker.runner=host} is set explicitly — and the
 * host runner refuses to start under the prod profile, because it runs untrusted code unisolated.
 */
@Configuration
public class RunnerConfig {

    private static final Logger log = LoggerFactory.getLogger(RunnerConfig.class);

    @Bean
    @ConditionalOnProperty(name = "worker.runner", havingValue = "docker", matchIfMissing = true)
    SandboxRunner dockerSandboxRunner(WorkerProperties props) {
        return new DockerSandboxRunner(props);
    }

    @Bean
    @ConditionalOnProperty(name = "worker.runner", havingValue = "host")
    SandboxRunner hostDevRunner(WorkerProperties props, Environment env) {
        if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException("worker.runner=host runs code without isolation and is not allowed in prod");
        }
        log.warn("*** worker.runner=host: user code runs DIRECTLY on this machine with NO sandbox. Dev use only. ***");
        return new HostDevRunner(props);
    }
}
