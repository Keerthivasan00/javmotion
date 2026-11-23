package com.javmotion.worker.sandbox;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.JobResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Production runner: one throw-away container per job with every isolation flag
 * ({@link DockerCommandBuilder}). Compilation happens inside the container too (docker/run.sh).
 */
public class DockerSandboxRunner implements SandboxRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerSandboxRunner.class);

    private final WorkerProperties props;
    private final DockerCommandBuilder commands;

    public DockerSandboxRunner(WorkerProperties props) {
        this.props = props;
        this.commands = new DockerCommandBuilder(props);
    }

    @Override
    public JobResult run(Workspace ws, String className, JobSpec spec) throws IOException, InterruptedException {
        List<String> cmd = commands.build(
                spec.jobId(), spec.mode(), className, ws.src(), ws.out(), spec.stdin() != null);
        Duration timeout = spec.mode() == ExecutionMode.VISUALIZE
                ? props.limits().visualizeTimeout()
                : props.limits().runTimeout();
        String container = DockerCommandBuilder.containerName(spec.jobId());

        ProcessRunner.Outcome run = ProcessRunner.run(
                cmd, ws.root(), spec.stdin(), timeout, props.limits().maxOutputBytes(), () -> dockerKill(container));

        // run.sh drops this marker when javac fails, so a program's own exit(1) is never
        // mistaken for a compile error.
        Path marker = ws.out().resolve("compile-failed");
        String compileOutput = Files.exists(marker)
                ? Files.readString(ws.out().resolve("compile.txt"), StandardCharsets.UTF_8)
                : null;
        return ResultMapper.map(run, compileOutput, timeout, props.limits().maxOutputBytes(), true);
    }

    private static void dockerKill(String container) {
        try {
            new ProcessBuilder("docker", "kill", container).redirectErrorStream(true).start().waitFor();
        } catch (IOException e) {
            log.warn("docker kill {} failed: {}", container, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
