package com.javmotion.tracer;

import com.javmotion.common.trace.HeapObject;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.BooleanValue;
import com.sun.jdi.CharValue;
import com.sun.jdi.DoubleValue;
import com.sun.jdi.Field;
import com.sun.jdi.FloatValue;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.PrimitiveValue;
import com.sun.jdi.StringReference;
import com.sun.jdi.Value;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Breadth-first snapshot of the objects reachable from one step's locals. Ids come from
 * {@link ObjectReference#uniqueID()}, so the same object keeps its {@code h<n>} id across steps
 * and the frontend can animate it. Bounded by depth and object count (doc §6.4).
 */
final class HeapCapture {

    private final TraceLimits limits;
    private final Map<String, HeapObject> heap = new LinkedHashMap<>();
    private final Deque<Pending> queue = new ArrayDeque<>();

    private record Pending(ObjectReference ref, int depth) {}

    HeapCapture(TraceLimits limits) {
        this.limits = limits;
    }

    /** Converts a JDI value; objects are queued for capture and referenced by id. */
    com.javmotion.common.trace.Value value(Value v, int depth) {
        if (v == null) return com.javmotion.common.trace.Value.nullValue();
        if (v instanceof PrimitiveValue p) return primitive(p);
        ObjectReference o = (ObjectReference) v;
        String id = "h" + o.uniqueID();
        if (!heap.containsKey(id) && depth < limits.maxHeapDepth() && heap.size() < limits.maxObjectsPerStep()) {
            heap.put(id, null); // reserve; filled in drain()
            queue.add(new Pending(o, depth));
        }
        return com.javmotion.common.trace.Value.ref(id);
    }

    /** Captures everything queued and returns the step's heap (unfilled slots dropped). */
    Map<String, HeapObject> drain() {
        while (!queue.isEmpty()) {
            Pending p = queue.poll();
            heap.put("h" + p.ref().uniqueID(), object(p.ref(), p.depth() + 1));
        }
        heap.values().removeIf(java.util.Objects::isNull);
        return heap;
    }

    private HeapObject object(ObjectReference o, int depth) {
        String type = o.referenceType().name();
        if (o instanceof StringReference s) {
            String value = s.value();
            if (value.length() > limits.maxStringChars()) value = value.substring(0, limits.maxStringChars());
            return new HeapObject.StringObj(type, value, null);
        }
        if (o instanceof ArrayReference a) {
            int length = a.length();
            int shown = Math.min(length, limits.maxArrayElements());
            List<com.javmotion.common.trace.Value> elements = new ArrayList<>(shown);
            if (shown > 0) for (Value e : a.getValues(0, shown)) elements.add(value(e, depth));
            return new HeapObject.ArrayObj(type, elements, shown < length ? length : null);
        }
        Map<String, com.javmotion.common.trace.Value> fields = new LinkedHashMap<>();
        // JDK objects (Integer, ArrayList, …) stay opaque until Phase 5 LIST/MAP support.
        if (!Exclusions.isExcluded(type)) {
            for (Field f : o.referenceType().allFields()) {
                if (!f.isStatic()) fields.put(f.name(), value(o.getValue(f), depth));
            }
        }
        return new HeapObject.ObjectObj(type, fields);
    }

    private static com.javmotion.common.trace.Value primitive(PrimitiveValue p) {
        String type = p.type().name();
        Object v;
        if (p instanceof BooleanValue b) v = b.value();
        else if (p instanceof CharValue c) v = String.valueOf(c.value());
        // NaN / Infinity are not valid JSON numbers: send them as strings (spike finding).
        else if (p instanceof DoubleValue d) v = Double.isFinite(d.value()) ? d.value() : String.valueOf(d.value());
        else if (p instanceof FloatValue f) v = Float.isFinite(f.value()) ? f.value() : String.valueOf(f.value());
        else v = switch (type) {
            case "long" -> p.longValue();
            default -> p.intValue(); // int, short, byte
        };
        return new com.javmotion.common.trace.Value.Primitive(type, v);
    }
}
