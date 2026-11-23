package com.javmotion.worker.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Per-job directory {@code <jobsDir>/<jobId>/{src,out}}. Use with try-with-resources: the whole
 * directory is deleted on close, whatever happened (backend doc §5.1).
 */
public final class Workspace implements AutoCloseable {

    private final Path root;
    private final Path src;
    private final Path out;

    private Workspace(Path root) throws IOException {
        this.root = root;
        this.src = Files.createDirectories(root.resolve("src"));
        this.out = Files.createDirectories(root.resolve("out"));
    }

    public static Workspace create(Path jobsDir, UUID jobId) throws IOException {
        Path root = jobsDir.toAbsolutePath().normalize().resolve(jobId.toString());
        if (Files.exists(root)) throw new IOException("Workspace already exists: " + root);
        return new Workspace(root);
    }

    /** Writes {@code <className>.java}; javac requires a public class to live in a matching file. */
    public Path writeSource(String className, String sourceCode) throws IOException {
        return Files.writeString(src.resolve(className + ".java"), sourceCode, StandardCharsets.UTF_8);
    }

    public Path root() {
        return root;
    }

    public Path src() {
        return src;
    }

    public Path out() {
        return out;
    }

    @Override
    public void close() {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not delete workspace " + root, e);
        }
    }
}
