package com.javmotion.worker.sandbox;

import com.javmotion.common.job.ExecutionMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Builds the {@code docker run} argument list for one job with every isolation flag from backend
 * doc §5.2. Returned as a list for {@link ProcessBuilder} — never joined into a shell string, so
 * nothing in it is shell-interpreted.
 */
public final class DockerCommandBuilder {

    /** UID/GID of the non-root user baked into the sandbox image. */
    static final String SANDBOX_USER = "10001:10001";

    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z\\d_$]{0,99}");

    private final WorkerProperties props;

    public DockerCommandBuilder(WorkerProperties props) {
        this.props = props;
    }

    /** Container name for a job — also used by {@code docker kill} and the orphan sweeper. */
    public static String containerName(UUID jobId) {
        return "jm-job-" + jobId;
    }

    /**
     * @param srcDir host directory with {@code <ClassName>.java}, mounted read-only
     * @param outDir host directory for compile output / trace.json, mounted read-write
     * @param withStdin keep stdin open ({@code -i}) so the worker can pipe input
     */
    public List<String> build(
            UUID jobId, ExecutionMode mode, String className, Path srcDir, Path outDir, boolean withStdin) {
        if (!JAVA_IDENTIFIER.matcher(className).matches()) {
            throw new IllegalArgumentException("Not a valid Java class name: " + className);
        }
        if (!srcDir.isAbsolute() || !outDir.isAbsolute()) {
            throw new IllegalArgumentException("Mount paths must be absolute");
        }
        WorkerProperties.Limits l = props.limits();

        List<String> cmd = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--name", containerName(jobId),
                "--network", "none",
                "--memory", l.memory(),
                "--memory-swap", l.memory(), // same as memory: no swap
                "--cpus", l.cpus(),
                "--pids-limit", String.valueOf(l.pids()),
                "--read-only",
                "--tmpfs", "/tmp:rw,size=" + l.tmpfsSize() + ",noexec",
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--user", SANDBOX_USER,
                "-v", srcDir + ":/sandbox/src:ro",
                "-v", outDir + ":/sandbox/out:rw"));
        if (withStdin) cmd.add("-i");
        cmd.addAll(List.of(props.sandboxImage(), "/sandbox/run.sh", mode.name(), className));
        return List.copyOf(cmd);
    }
}
