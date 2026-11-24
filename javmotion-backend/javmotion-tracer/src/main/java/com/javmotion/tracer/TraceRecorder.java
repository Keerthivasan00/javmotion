package com.javmotion.tracer;

import com.javmotion.common.trace.EventType;
import com.javmotion.common.trace.Frame;
import com.javmotion.common.trace.HeapObject;
import com.javmotion.common.trace.Trace;
import com.javmotion.common.trace.TraceSource;
import com.javmotion.common.trace.TraceStep;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.Value;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VoidValue;
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
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.MethodExitRequest;
import com.sun.jdi.request.StepRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records one program run as a {@link Trace} ("record, then replay", doc §3.1).
 *
 * <p>Step semantics (same as concept traces): a step is recorded <em>before</em> its line runs;
 * the first step is CALL, the last END. Lessons from the B0 spike are marked "spike".
 */
public final class TraceRecorder {

    /** What one recording produced. {@code exitCode} is the user program's exit code. */
    public record Recording(Trace trace, String stdout, String stderr, int exitCode, boolean outputTruncated) {}

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final TraceLimits limits;
    private final List<TraceStep> steps = new ArrayList<>();
    private final BoundedBuffer stdout;
    private final BoundedBuffer stderr;
    private long bytes;
    private boolean truncated;
    private int lastLine = 1;
    private long deadline;
    private String lastStdout = "";

    /** Shadow stack (bottom first) giving JDI frames stable ids across steps (spike). */
    private final List<String> frameKeys = new ArrayList<>();
    private final List<String> frameIds = new ArrayList<>();
    private int nextFrameId = 1;

    public TraceRecorder(TraceLimits limits) {
        this.limits = limits;
        this.stdout = new BoundedBuffer(limits.maxOutputBytes());
        this.stderr = new BoundedBuffer(limits.maxOutputBytes());
    }

    public Recording record(String classpath, String mainClass) throws IOException, InterruptedException {
        deadline = System.currentTimeMillis() + limits.maxMillis();
        DebuggeeLauncher.Debuggee d = DebuggeeLauncher.launch(classpath, mainClass);
        try {
            loop(d.vm(), d.process(), mainClass);
        } finally {
            // After VMDisconnect the JVM is still shutting down: let it exit by itself so its real
            // exit code survives; only kill it if it hangs (or we stopped it on a limit).
            if (!d.process().waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) d.process().destroyForcibly();
        }
        int exit = d.process().waitFor();
        // Debuggee is gone: whatever it printed is now fully in the pipes.
        stdout.drainToEof(d.process().getInputStream());
        stderr.drainToEof(d.process().getErrorStream());

        steps.add(new TraceStep(steps.size(), EventType.END, lastLine, List.of(), Map.of(), stdout.text(),
                null, null, null));
        Trace trace = new Trace(Trace.SCHEMA_VERSION, TraceSource.JDI, "java", truncated, List.copyOf(steps));
        return new Recording(trace, stdout.text(), stderr.text(), exit, stdout.truncated() || stderr.truncated());
    }

    private void loop(VirtualMachine vm, Process process, String mainClass) throws IOException {
        EventRequestManager erm = vm.eventRequestManager();
        ClassPrepareRequest prepare = erm.createClassPrepareRequest();
        prepare.addClassFilter(mainClass);
        prepare.enable();

        boolean done = false;
        boolean dead = false;
        while (!done) {
            EventSet set;
            try {
                // Bounded wait: a loop that stays on ONE line (`while (true) { x++; }`) produces no
                // new STEP_LINE events, so the deadline must be checked even when nothing arrives.
                long left = deadline - System.currentTimeMillis();
                set = dead ? vm.eventQueue().remove(2_000) : vm.eventQueue().remove(Math.max(1, left));
                if (set == null) {
                    if (dead) return; // dying VM went quiet; the process is cleaned up by record()
                    truncated = true;
                    vm.exit(1);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (VMDisconnectedException e) {
                return;
            }
            for (Event e : set) {
                try {
                    if (e instanceof ClassPrepareEvent cpe) {
                        breakAtMain(erm, cpe.referenceType());
                    } else if (e instanceof BreakpointEvent be) {
                        be.request().disable();
                        startStepping(erm, be.thread());
                        capture(be, process, EventType.CALL, null, null);
                    } else if (e instanceof StepEvent se) {
                        capture(se, process, null, null, null);
                    } else if (e instanceof MethodExitEvent me) {
                        Value rv = me.returnValue();
                        capture(me, process, EventType.RETURN, rv instanceof VoidValue ? null : rv, null);
                        if (frameIds.size() == 1) {
                            // main is returning: stop stepping so the JDWP agent does not single-step
                            // the JVM's shutdown code (spike).
                            erm.deleteEventRequests(erm.stepRequests());
                            erm.deleteEventRequests(erm.methodExitRequests());
                        }
                    } else if (e instanceof ExceptionEvent ee) {
                        capture(ee, process, EventType.EXCEPTION, null, ee.exception());
                    } else if (e instanceof VMDeathEvent) {
                        dead = true; // keep going until VMDisconnect (spike)
                    } else if (e instanceof VMDisconnectEvent) {
                        done = true;
                    }
                } catch (IncompatibleThreadStateException ex) {
                    // thread not suspended for this event: skip it
                }
            }
            if (!done && !dead && truncated) {
                vm.exit(1); // limit hit: stop the program, keep what we recorded
                return;
            }
            try {
                set.resume(); // always — including the VMDeath set (spike)
            } catch (VMDisconnectedException ignored) {
                return;
            }
        }
    }

    private static void breakAtMain(EventRequestManager erm, ReferenceType type) {
        for (Method m : type.methodsByName("main")) {
            if (m.isStatic() && m.signature().equals("([Ljava/lang/String;)V")) {
                erm.createBreakpointRequest(m.location()).enable();
            }
        }
    }

    private static void startStepping(EventRequestManager erm, ThreadReference thread) {
        StepRequest step = erm.createStepRequest(thread, StepRequest.STEP_LINE, StepRequest.STEP_INTO);
        for (String ex : Exclusions.PATTERNS) step.addClassExclusionFilter(ex);
        step.enable();

        MethodExitRequest exit = erm.createMethodExitRequest();
        for (String ex : Exclusions.PATTERNS) exit.addClassExclusionFilter(ex);
        exit.addThreadFilter(thread);
        exit.enable();

        // No class exclusions: exceptions thrown inside JDK code must still be seen (spike).
        ExceptionRequest exc = erm.createExceptionRequest(null, false, true);
        exc.addThreadFilter(thread);
        exc.enable();
    }

    // ------------------------------------------------------------------ capture

    private void capture(LocatableEvent e, Process process, EventType forced, Value returnValue, ObjectReference exception)
            throws IncompatibleThreadStateException, IOException {
        if (truncated) return;
        if (steps.size() >= limits.maxSteps() - 1 || System.currentTimeMillis() > deadline) {
            truncated = true;
            return;
        }
        // The debuggee is suspended, so what is in the pipe is exactly what it printed so far
        // (spike: synchronous drain keeps output on the right step).
        stdout.drainAvailable(process.getInputStream());
        stderr.drainAvailable(process.getErrorStream());

        Location loc = e.location();
        int line = Math.max(loc.lineNumber(), 0);

        List<StackFrame> userFrames = new ArrayList<>();
        List<StackFrame> all = e.thread().frames();
        for (int i = all.size() - 1; i >= 0; i--) { // bottom first
            StackFrame f = all.get(i);
            if (!Exclusions.isExcluded(f.location().declaringType().name())) userFrames.add(f);
        }
        boolean deeper = userFrames.size() > frameIds.size();
        assignFrameIds(userFrames);
        EventType event = forced != null ? forced : deeper ? EventType.CALL : EventType.LINE;

        HeapCapture heap = new HeapCapture(limits);
        List<Frame> stack = new ArrayList<>(userFrames.size());
        for (int i = 0; i < userFrames.size(); i++) stack.add(frame(frameIds.get(i), userFrames.get(i), heap));
        com.javmotion.common.trace.Value rv = returnValue != null ? heap.value(returnValue, 0) : null;
        Map<String, HeapObject> objects = heap.drain();

        String out = stdout.text();
        if (out.equals(lastStdout)) out = lastStdout; // share the String across steps (memory)
        lastStdout = out;

        TraceStep step = new TraceStep(steps.size(), event, line, stack, objects, out, rv,
                exception != null ? exceptionOf(exception) : null, null);
        long size = JSON.writeValueAsBytes(step).length;
        if (bytes + size > limits.maxBytes() - 64 * 1024) { // keep room for END
            truncated = true;
            return;
        }
        bytes += size;
        lastLine = line;
        steps.add(step);
    }

    private void assignFrameIds(List<StackFrame> frames) {
        int keep = 0;
        while (keep < frames.size() && keep < frameKeys.size() && frameKeys.get(keep).equals(key(frames.get(keep)))) {
            keep++;
        }
        while (frameKeys.size() > keep) {
            frameKeys.removeLast();
            frameIds.removeLast();
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

    private static Frame frame(String id, StackFrame f, HeapCapture heap) {
        Method m = f.location().method();
        Map<String, com.javmotion.common.trace.Value> locals = new LinkedHashMap<>();
        ObjectReference self = f.thisObject();
        if (self != null) locals.put("this", heap.value(self, 0));
        try {
            List<LocalVariable> vars = f.visibleVariables();
            Map<LocalVariable, Value> values = f.getValues(vars);
            for (LocalVariable v : vars) locals.put(v.name(), heap.value(values.get(v), 0));
        } catch (AbsentInformationException ignored) {
            // compiled without -g: no local names. The sandbox always compiles with -g.
        }
        return new Frame(id, m.name(), m.declaringType().name(), Math.max(f.location().lineNumber(), 0), locals);
    }

    /** Reads Throwable.detailMessage directly; invoking getMessage() on a suspended VM is slow (spike). */
    private static TraceStep.TraceException exceptionOf(ObjectReference ex) {
        String message = "";
        Field detail = ex.referenceType().fieldByName("detailMessage");
        if (detail != null && ex.getValue(detail) instanceof StringReference s) message = s.value();
        return new TraceStep.TraceException(ex.referenceType().name(), message);
    }

    /** Keeps the first {@code limit} bytes of a stream, discards (but drains) the rest. */
    static final class BoundedBuffer {
        private final int limit;
        private final ByteArrayOutputStream kept = new ByteArrayOutputStream();
        private long total;

        BoundedBuffer(int limit) {
            this.limit = limit;
        }

        void drainAvailable(InputStream in) throws IOException {
            int n;
            while ((n = in.available()) > 0) {
                byte[] buf = in.readNBytes(n);
                if (buf.length == 0) break;
                write(buf);
            }
        }

        void drainToEof(InputStream in) throws IOException {
            write(in.readAllBytes());
        }

        private void write(byte[] buf) {
            int room = (int) Math.max(0, Math.min(buf.length, limit - total));
            kept.write(buf, 0, room);
            total += buf.length;
        }

        boolean truncated() {
            return total > limit;
        }

        String text() {
            return kept.toString(StandardCharsets.UTF_8);
        }
    }
}
