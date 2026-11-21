package com.javmotion.tracer;

/**
 * Entry point inside the sandbox: {@code java -jar tracer.jar <MainClass>}.
 *
 * <p>Plan (Step B0 spike, then B5): launch the debuggee via JDI LaunchingConnector, line-step user
 * classes, capture frames/locals/heap on each event, write {@code /sandbox/out/trace.json}.
 */
public final class TracerMain {

    private TracerMain() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javmotion-tracer.jar <MainClass>");
            System.exit(2);
        }
        System.err.println("javmotion-tracer: not implemented yet (see CHECKLIST.md Step B0/B5)");
        System.exit(1);
    }
}
