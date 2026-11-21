package com.javmotion.worker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.javmotion.common.job.ExecutionMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SandboxPiecesTest {

    private static final WorkerProperties PROPS = new WorkerProperties(
            "t", "javmotion-sandbox:21", 4, Path.of("/var/javmotion/jobs"),
            new WorkerProperties.Limits("256m", "1", 64, "16m", Duration.ofSeconds(5), Duration.ofSeconds(10), 65536));

    private static final UUID JOB = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final Path SRC = Path.of("/var/javmotion/jobs/x/src").toAbsolutePath();
    private static final Path OUT = Path.of("/var/javmotion/jobs/x/out").toAbsolutePath();

    @Nested
    class DockerCommand {

        private final DockerCommandBuilder builder = new DockerCommandBuilder(PROPS);

        @Test
        void containsEveryIsolationFlag() {
            List<String> cmd = builder.build(JOB, ExecutionMode.RUN, "Main", SRC, OUT, false);
            String joined = String.join(" ", cmd);

            assertThat(cmd.subList(0, 3)).containsExactly("docker", "run", "--rm");
            assertThat(joined)
                    .contains("--name jm-job-" + JOB)
                    .contains("--network none")
                    .contains("--memory 256m --memory-swap 256m")
                    .contains("--cpus 1")
                    .contains("--pids-limit 64")
                    .contains("--read-only")
                    .contains("--tmpfs /tmp:rw,size=16m,noexec")
                    .contains("--cap-drop ALL")
                    .contains("--security-opt no-new-privileges")
                    .contains("--user 10001:10001")
                    .contains(SRC + ":/sandbox/src:ro")
                    .contains(OUT + ":/sandbox/out:rw")
                    .doesNotContain(" -i ");
            assertThat(cmd.subList(cmd.size() - 4, cmd.size()))
                    .containsExactly("javmotion-sandbox:21", "/sandbox/run.sh", "RUN", "Main");
        }

        @Test
        void addsInteractiveFlagOnlyWithStdin() {
            assertThat(builder.build(JOB, ExecutionMode.VISUALIZE, "Main", SRC, OUT, true)).contains("-i");
        }

        @Test
        void rejectsClassNamesThatAreNotIdentifiers() {
            for (String bad : List.of("Main; rm -rf /", "../Main", "1Main", "", "Ma in")) {
                assertThatThrownBy(() -> builder.build(JOB, ExecutionMode.RUN, bad, SRC, OUT, false))
                        .as(bad)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        @Test
        void rejectsRelativeMounts() {
            assertThatThrownBy(() -> builder.build(JOB, ExecutionMode.RUN, "Main", Path.of("src"), OUT, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Output {

        @Test
        void keepsEverythingUnderTheLimit() {
            BoundedOutput out = new BoundedOutput(10);
            out.write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
            assertThat(out.text()).isEqualTo("hello");
            assertThat(out.truncated()).isFalse();
        }

        @Test
        void dropsBytesPastTheLimitButKeepsCounting() {
            BoundedOutput out = new BoundedOutput(4);
            byte[] data = "abcdefgh".getBytes(StandardCharsets.UTF_8);
            out.write(data, 0, 3);
            out.write(data, 3, 5);
            out.write('z');
            assertThat(out.text()).isEqualTo("abcd");
            assertThat(out.truncated()).isTrue();
            assertThat(out.totalBytes()).isEqualTo(9);
        }

        @Test
        void neverEndsInHalfACharacter() {
            byte[] tamil = "தமிழ்".getBytes(StandardCharsets.UTF_8); // 3-byte characters
            for (int limit = 0; limit <= tamil.length; limit++) {
                BoundedOutput out = new BoundedOutput(limit);
                out.write(tamil, 0, tamil.length);
                assertThat(out.text()).as("limit %d", limit).doesNotContain("�");
                assertThat("தமிழ்").startsWith(out.text());
            }
        }
    }
}
