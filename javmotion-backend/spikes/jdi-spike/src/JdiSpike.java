import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.Field;
import com.sun.jdi.FloatValue;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VoidValue;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodExitEvent;
import com.sun.jdi.event.StepEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step B0 spike: record a JavMotion trace (schema v1) of a Java program with JDI.
 *
 * <pre>
 * javac -g -d out/app samples/loop/Main.java
 * javac -d out/spike src/JdiSpike.java
 * java -cp out/spike JdiSpike out/app Main trace.json
 * </pre>
 *
 * Deliberately one file with no dependencies (hand-written JSON) so it can be dropped into a
 * container as-is. The real tracer (Step B5) will reuse javmotion-common's Trace model + Jackson.
 */
public final class JdiSpike {

    static final String[] EXCLUDES = {"java.*", "javax.*", "sun.*", "jdk.*", "com.sun.*"};
    static final int MAX_STEPS = 5000;
    static final int MAX_OBJECTS = 200;
    static final int MAX_ARRAY = 100;
    static final int MAX_STRING = 1000;
    static final int MAX_HEAP_DEPTH = 4;
    /** Uncompressed trace size cap (backend doc §6.4). Gzip shrinks traces ~100x in practice. */
    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final VirtualMachine vm;
    private final InputStream stdoutPipe;
    private final InputStream stderrPipe;
    private final ByteArrayOutputStream stdoutBytes = new ByteArrayOutputStream();
    private final List<String> steps = new ArrayList<>();
    private boolean truncated;
    private long bytes;

    /** Shadow stack (bottom first) giving frames stable ids across steps. */
    private final List<String> frameKeys = new ArrayList<>();
    private final List<String> frameIds = new ArrayList<>();
    private int nextFrameId = 1;

    private JdiSpike(VirtualMachine vm, Process process) {
        this.vm = vm;
        this.stdoutPipe = process.getInputStream();
        this.stderrPipe = process.getErrorStream();
    }

    record Debuggee(VirtualMachine vm, Process process) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("Usage: java JdiSpike <classpath> <MainClass> <out.json>");
            System.exit(2);
        }
        long t0 = System.nanoTime();
        Debuggee debuggee = launch(args[0], args[1]);
        long launched = System.nanoTime();

        JdiSpike spike = new JdiSpike(debuggee.vm(), debuggee.process());
        spike.run(args[1]);
        String json = spike.toJson();
        Files.writeString(Path.of(args[2]), json, StandardCharsets.UTF_8);

        long end = System.nanoTime();
        System.err.printf(
                "steps=%d truncated=%b bytes=%d launchMs=%d traceMs=%d totalMs=%d%n",
                spike.steps.size(), spike.truncated, json.length(),
                (launched - t0) / 1_000_000, (end - launched) / 1_000_000, (end - t0) / 1_000_000);
    }

    /**
     * Starts the debuggee ourselves and lets it connect back over a local socket.
     *
     * <p>Spike finding: the default CommandLineLaunch connector uses the shared-memory transport
     * on Windows, where disconnect takes ~2.2 s per run. Launching via ProcessBuilder + SocketListen
     * is the same code path on Windows and Linux, and gives us the Process (pipes, exit code).
     */
    static Debuggee launch(String classpath, String mainClass) throws Exception {
        ListeningConnector listen = Bootstrap.virtualMachineManager().listeningConnectors().stream()
                .filter(c -> c.transport().name().equals("dt_socket"))
                .findFirst()
                .orElseThrow();
        Map<String, Connector.Argument> a = listen.defaultArguments();
        a.get("localAddress").setValue("127.0.0.1");
        a.get("port").setValue("0"); // any free port
        String address = listen.startListening(a);
        try {
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            // Same JVM flags the sandbox will use (backend doc §5.2).
            Process process = new ProcessBuilder(
                            java,
                            "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + address,
                            "-Xmx128m", "-Xss512k", "-XX:+UseSerialGC",
                            "-cp", classpath,
                            mainClass)
                    .start();
            return new Debuggee(listen.accept(a), process);
        } finally {
            listen.stopListening(a);
        }
    }

    private void run(String mainClass) throws Exception {
        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest prepare = erm.createClassPrepareRequest();
        prepare.addClassFilter(mainClass);
        prepare.enable();

        boolean done = false;
        boolean dead = false;
        while (!done) {
            EventSet set = vm.eventQueue().remove();
            for (Event e : set) {
                if (e instanceof ClassPrepareEvent cpe) {
                    onPrepare(erm, cpe.referenceType());
                } else if (e instanceof BreakpointEvent be) {
                    be.request().disable();
                    startStepping(erm, be.thread());
                    record(be, "CALL", null, null);
                } else if (e instanceof StepEvent se) {
                    record(se, null, null, null);
                } else if (e instanceof MethodExitEvent me) {
                    Value rv = me.returnValue();
                    record(me, "RETURN", rv instanceof VoidValue ? null : rv, null);
                    if (frameIds.size() == 1) {
                        // Bottom user frame (main) is returning. Stop stepping now, otherwise the
                        // JDWP agent single-steps all of the JVM's shutdown code through the
                        // exclusion filters — that cost ~2.2 s per run (spike finding).
                        erm.deleteEventRequests(erm.stepRequests());
                        erm.deleteEventRequests(erm.methodExitRequests());
                    }
                } else if (e instanceof ExceptionEvent ee) {
                    record(ee, "EXCEPTION", null, ee.exception());
                } else if (e instanceof VMDeathEvent) {
                    // Keep reading until VMDisconnect: the debuggee process only exits once the
                    // JDWP connection is released (stopping here cost ~2 s per run — spike finding).
                    dead = true;
                } else if (e instanceof VMDisconnectEvent) {
                    done = true;
                }
            }
            if (!done && !dead && truncated) {
                vm.exit(1);
                done = true;
            }
            // Always resume — including the VMDeath set. Leaving it suspended makes the debuggee
            // wait ~2 s for the debugger before it exits (found in this spike).
            try {
                set.resume();
            } catch (com.sun.jdi.VMDisconnectedException ignored) {
                // already gone
            }
        }
        // Debuggee is gone: everything it printed is now in the pipe.
        drainUntilEof(stdoutPipe, stdoutBytes);
        drainUntilEof(stderrPipe, new ByteArrayOutputStream());
        steps.add(stepJson("END", lastLine, "[]", "{}", null, null));
    }

    private void onPrepare(EventRequestManager erm, ReferenceType type) {
        for (Method m : type.methodsByName("main")) {
            if (m.isStatic() && m.signature().equals("([Ljava/lang/String;)V")) {
                BreakpointRequest bp = erm.createBreakpointRequest(m.location());
                bp.enable();
            }
        }
    }

    private void startStepping(EventRequestManager erm, ThreadReference thread) {
        StepRequest step = erm.createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
        for (String ex : EXCLUDES) step.addClassExclusionFilter(ex);
        step.enable();

        MethodExitRequest exit = erm.createMethodExitRequest();
        for (String ex : EXCLUDES) exit.addClassExclusionFilter(ex);
        exit.addThreadFilter(thread);
        exit.enable();

        // No class exclusions here: exceptions thrown inside JDK code (parseInt, AIOOBE helpers)
        // must still be reported when they escape uncaught.
        ExceptionRequest exc = erm.createExceptionRequest(null, false, true);
        exc.addThreadFilter(thread);
        exc.enable();
    }

    // ------------------------------------------------------------------ capture

    private int lastLine;

    private void record(LocatableEvent e, String forcedEvent, Value returnValue, ObjectReference exception)
            throws IncompatibleThreadStateException, IOException {
        if (steps.size() >= MAX_STEPS - 1) { // keep one slot for END
            truncated = true;
            return;
        }
        drainAvailable(stdoutPipe, stdoutBytes);
        drainAvailable(stderrPipe, new ByteArrayOutputStream());

        Location loc = e.location();
        int line = Math.max(loc.lineNumber(), 0);
        lastLine = line;

        List<StackFrame> userFrames = new ArrayList<>();
        List<StackFrame> all = e.thread().frames();
        for (int i = all.size() - 1; i >= 0; i--) { // bottom first
            StackFrame f = all.get(i);
            if (!isExcluded(f.location().declaringType().name())) userFrames.add(f);
        }

        boolean deeper = userFrames.size() > frameIds.size();
        assignFrameIds(userFrames);
        String event = forcedEvent != null ? forcedEvent : (deeper ? "CALL" : "LINE");

        HeapWalker heap = new HeapWalker();
        StringBuilder stack = new StringBuilder("[");
        for (int i = 0; i < userFrames.size(); i++) {
            StackFrame f = userFrames.get(i);
            if (i > 0) stack.append(',');
            stack.append(frameJson(frameIds.get(i), f, heap));
        }
        stack.append(']');

        String rv = returnValue != null ? heap.value(returnValue, 0) : null;
        String exc = exception != null ? exceptionJson(exception) : null;
        String json = stepJson(event, line, stack.toString(), heap.drainToJson(), rv, exc);
        if (bytes + json.length() > MAX_BYTES - 64 * 1024) { // leave room for END
            truncated = true;
            return;
        }
        bytes += json.length();
        steps.add(json);
    }

    private void assignFrameIds(List<StackFrame> frames) {
        int keep = 0;
        while (keep < frames.size() && keep < frameKeys.size() && frameKeys.get(keep).equals(key(frames.get(keep)))) {
            keep++;
        }
        // A frame that is not the top but whose key matches is the same activation; anything
        // above the first mismatch is a new activation (handles recursion via depth changes).
        while (frameKeys.size() > keep) {
            frameKeys.remove(frameKeys.size() - 1);
            frameIds.remove(frameIds.size() - 1);
        }
        for (int i = keep; i < frames.size(); i++) {
            frameKeys.add(key(frames.get(i)));
            frameIds.add("f" + nextFrameId++);
        }
    }

    private static String key(StackFrame f) {
        Method m = f.location().method();
        return m.declaringType().name() + "#" + m.name() + m.signature();
    }

    private String frameJson(String id, StackFrame f, HeapWalker heap) {
        Method m = f.location().method();
        StringBuilder locals = new StringBuilder("{");
        boolean first = true;
        ObjectReference self = f.thisObject();
        if (self != null) {
            locals.append(str("this")).append(':').append(heap.value(self, 0));
            first = false;
        }
        try {
            List<LocalVariable> vars = f.visibleVariables();
            Map<LocalVariable, Value> values = f.getValues(vars);
            for (LocalVariable v : vars) {
                if (!first) locals.append(',');
                locals.append(str(v.name())).append(':').append(heap.value(values.get(v), 0));
                first = false;
            }
        } catch (AbsentInformationException ex) {
            // Compiled without -g: no local variable table. Tracer must always use `javac -g`.
        }
        locals.append('}');
        return "{\"id\":" + str(id)
                + ",\"method\":" + str(m.name())
                + ",\"className\":" + str(m.declaringType().name())
                + ",\"line\":" + Math.max(f.location().lineNumber(), 0)
                + ",\"locals\":" + locals + "}";
    }

    private String exceptionJson(ObjectReference ex) {
        String message = "";
        Field detail = ex.referenceType().fieldByName("detailMessage");
        if (detail != null && ex.getValue(detail) instanceof StringReference s) message = s.value();
        return "{\"type\":" + str(ex.referenceType().name()) + ",\"message\":" + str(message) + "}";
    }

    private String stepJson(String event, int line, String stack, String heap, String returnValue, String exception) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"index\":").append(steps.size())
                .append(",\"event\":").append(str(event))
                .append(",\"line\":").append(line)
                .append(",\"stack\":").append(stack)
                .append(",\"heap\":").append(heap)
                .append(",\"stdout\":").append(str(stdoutBytes.toString(StandardCharsets.UTF_8)));
        if (returnValue != null) sb.append(",\"returnValue\":").append(returnValue);
        if (exception != null) sb.append(",\"exception\":").append(exception);
        return sb.append('}').toString();
    }

    private String toJson() {
        return "{\"schemaVersion\":1,\"source\":\"JDI\",\"language\":\"java\",\"truncated\":" + truncated
                + ",\"steps\":[\n" + String.join(",\n", steps) + "\n]}\n";
    }

    // ------------------------------------------------------------------ heap

    /** Breadth-first heap capture for one step. Ids come from uniqueID(), so they are stable. */
    private final class HeapWalker {
        private final Map<Long, String> objects = new LinkedHashMap<>();
        private final Deque<Object[]> queue = new ArrayDeque<>(); // {ObjectReference, depth}

        String value(Value v, int depth) {
            if (v == null) return "{\"kind\":\"NULL\"}";
            if (v instanceof PrimitiveValue p) return primitive(p);
            ObjectReference o = (ObjectReference) v;
            if (!objects.containsKey(o.uniqueID()) && depth < MAX_HEAP_DEPTH && objects.size() < MAX_OBJECTS) {
                objects.put(o.uniqueID(), null); // reserve
                queue.add(new Object[] {o, depth});
            }
            return "{\"kind\":\"REF\",\"ref\":\"h" + o.uniqueID() + "\"}";
        }

        String drainToJson() {
            while (!queue.isEmpty()) {
                Object[] next = queue.poll();
                ObjectReference o = (ObjectReference) next[0];
                objects.put(o.uniqueID(), object(o, (int) next[1] + 1));
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<Long, String> e : objects.entrySet()) {
                if (e.getValue() == null) continue;
                if (!first) sb.append(',');
                sb.append("\"h").append(e.getKey()).append("\":").append(e.getValue());
                first = false;
            }
            return sb.append('}').toString();
        }

        private String object(ObjectReference o, int depth) {
            String type = str(o.referenceType().name());
            if (o instanceof StringReference s) {
                String value = s.value();
                if (value.length() > MAX_STRING) value = value.substring(0, MAX_STRING);
                return "{\"kind\":\"STRING\",\"type\":" + type + ",\"value\":" + str(value) + "}";
            }
            if (o instanceof ArrayReference a) {
                int len = a.length();
                int shown = Math.min(len, MAX_ARRAY);
                StringBuilder els = new StringBuilder("[");
                List<Value> values = shown == 0 ? List.of() : a.getValues(0, shown);
                for (int i = 0; i < values.size(); i++) {
                    if (i > 0) els.append(',');
                    els.append(value(values.get(i), depth));
                }
                els.append(']');
                return "{\"kind\":\"ARRAY\",\"type\":" + type + ",\"elements\":" + els
                        + (shown < len ? ",\"length\":" + len : "") + "}";
            }
            StringBuilder fields = new StringBuilder("{");
            // JDK objects (Integer, ArrayList, ...) stay opaque until Phase 5 LIST/MAP support.
            if (!isExcluded(o.referenceType().name())) {
                boolean first = true;
                for (Field f : o.referenceType().allFields()) {
                    if (f.isStatic()) continue;
                    if (!first) fields.append(',');
                    fields.append(str(f.name())).append(':').append(value(o.getValue(f), depth));
                    first = false;
                }
            }
            fields.append('}');
            return "{\"kind\":\"OBJECT\",\"type\":" + type + ",\"fields\":" + fields + "}";
        }

        private String primitive(PrimitiveValue p) {
            String type = str(p.type().name());
            String value;
            if (p instanceof BooleanValue b) value = String.valueOf(b.value());
            else if (p instanceof CharValue c) value = str(String.valueOf(c.value()));
            else if (p instanceof DoubleValue d && !Double.isFinite(d.value())) value = str(String.valueOf(d.value()));
            else if (p instanceof FloatValue f && !Float.isFinite(f.value())) value = str(String.valueOf(f.value()));
            else value = p.toString();
            return "{\"kind\":\"PRIMITIVE\",\"type\":" + type + ",\"value\":" + value + "}";
        }
    }

    // ------------------------------------------------------------------ helpers

    static boolean isExcluded(String className) {
        for (String ex : EXCLUDES) {
            if (className.startsWith(ex.substring(0, ex.length() - 1))) return true;
        }
        return false;
    }

    /**
     * Reads what the suspended debuggee already wrote. Doing this synchronously at each event
     * (instead of a pump thread) attaches output to the step that produced it, deterministically.
     */
    static void drainAvailable(InputStream in, ByteArrayOutputStream sink) throws IOException {
        int n;
        while ((n = in.available()) > 0) {
            byte[] buf = new byte[n];
            int read = in.read(buf);
            if (read <= 0) break;
            sink.write(buf, 0, read);
        }
    }

    static void drainUntilEof(InputStream in, ByteArrayOutputStream sink) throws IOException {
        sink.write(in.readAllBytes());
    }

    static String str(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }
}
