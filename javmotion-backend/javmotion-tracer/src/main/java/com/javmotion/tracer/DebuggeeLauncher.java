package com.javmotion.tracer;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Starts the user's program suspended under JDWP and connects to it.
 *
 * <p>Spike finding (B0): the default CommandLineLaunch connector uses shared memory on Windows
 * and its disconnect costs ~2.2 s per run. We start the process ourselves and let it connect
 * back to a {@code SocketListen} connector instead — same path on Linux and Windows, and we own
 * the {@link Process} (pipes, exit code).
 */
final class DebuggeeLauncher {

    /** JVM flags for the user's program — same as RUN mode in the sandbox (doc §5.2). */
    static final String[] DEBUGGEE_JVM_FLAGS = {
        "-Xmx128m", "-XX:MaxMetaspaceSize=64m", "-Xss512k", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1"
    };

    record Debuggee(VirtualMachine vm, Process process) {}

    private DebuggeeLauncher() {}

    static Debuggee launch(String classpath, String mainClass) throws IOException {
        ListeningConnector listen = Bootstrap.virtualMachineManager().listeningConnectors().stream()
                .filter(c -> c.transport().name().equals("dt_socket"))
                .findFirst()
                .orElseThrow(() -> new IOException("No dt_socket listening connector"));
        Map<String, Connector.Argument> args = listen.defaultArguments();
        args.get("localAddress").setValue("127.0.0.1");
        args.get("port").setValue("0"); // any free port
        args.get("timeout").setValue("10000");
        try {
            String address = listen.startListening(args);
            try {
                String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
                String[] cmd = new String[DEBUGGEE_JVM_FLAGS.length + 5];
                cmd[0] = java;
                cmd[1] = "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + address;
                System.arraycopy(DEBUGGEE_JVM_FLAGS, 0, cmd, 2, DEBUGGEE_JVM_FLAGS.length);
                int i = 2 + DEBUGGEE_JVM_FLAGS.length;
                cmd[i++] = "-cp";
                cmd[i++] = classpath;
                cmd[i] = mainClass;
                Process process = new ProcessBuilder(cmd).start();
                // No stdin while visualizing (v1): EOF instead of a program waiting forever.
                process.getOutputStream().close();
                return new Debuggee(listen.accept(args), process);
            } finally {
                listen.stopListening(args);
            }
        } catch (IllegalConnectorArgumentsException e) {
            throw new IOException("Bad JDI connector arguments", e);
        }
    }
}
