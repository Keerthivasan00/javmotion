package com.javmotion.worker.sandbox;

import com.javmotion.common.job.ExecutionMode;
import com.javmotion.common.job.JobResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * <b>DEV ONLY — NO ISOLATION.</b> Runs javac/java directly on the worker host, with the same
 * time and output limits but none of the container protections (network, filesystem, process
 * limits). Exists so the run pipeline can be developed without Docker. Enabled only with
 * {@code worker.runner=host} outside the prod profile (see {@code RunnerConfig}).
 */
public class HostDevRunner implements SandboxRunner {

    static final Duration COMPILE_TIMEOUT = Duration.ofSeconds(15);

    private final WorkerProperties props;
    private final Path javaBin;

    public HostDevRunner(WorkerProperties props) {
        this.props = props;
        this.javaBin = Path.of(System.getProperty("java.home"), "bin");
    }

    @Override
    public JobResult run(Workspace ws, String className, JobSpec spec) throws IOException, InterruptedException {
        if (spec.mode() == ExecutionMode.VISUALIZE) {
            return JobResult.failed("Visualize needs the JDI tracer (Step B5), which is not built yet.");
        }
        int maxOut = props.limits().maxOutputBytes();
        Path classes = Files.createDirectories(ws.out().resolve("classes"));

        List<String> javac = List.of(tool("javac"), "-encoding", "UTF-8", "-d", classes.toString(),
                ws.src().resolve(className + ".java").toString());
        ProcessRunner.Outcome compile = ProcessRunner.run(javac, ws.root(), null, COMPILE_TIMEOUT, maxOut, () -> {});
        if (compile.timedOut()) return JobResult.failed("Compilation took too long.");
        if (compile.exitCode() != 0) {
            return ResultMapper.map(compile, compile.stderr(), COMPILE_TIMEOUT, maxOut, false);
        }

        List<String> java = new ArrayList<>(List.of(tool("java"),
                "-Xmx128m", "-XX:MaxMetaspaceSize=64m", "-Xss512k", "-XX:+UseSerialGC",
                "-cp", classes.toString(), className));
        Duration timeout = props.limits().runTimeout();
        ProcessRunner.Outcome run = ProcessRunner.run(java, ws.root(), spec.stdin(), timeout, maxOut, () -> {});
        return ResultMapper.map(run, null, timeout, maxOut, false);
    }

    private String tool(String name) {
        Path exe = javaBin.resolve(name + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : ""));
        return Files.exists(exe) ? exe.toString() : name;
    }
}
