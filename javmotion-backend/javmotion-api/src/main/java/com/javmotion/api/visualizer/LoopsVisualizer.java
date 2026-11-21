package com.javmotion.api.visualizer;

import com.javmotion.api.common.error.ApiException;
import com.javmotion.api.visualizer.JavaSource.Counter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * for / while / do-while / nested loops counting from {@code start} to {@code end} (inclusive).
 * Counts down when {@code start > end}. No highlights: the frontend LoopVisualizer derives
 * iterations and condition results from line revisits, exactly as for JDI traces.
 */
@Component
public class LoopsVisualizer implements ConceptVisualizer<LoopsVisualizer.Request> {

    static final int MAX_ITERATIONS = 20;
    static final int MAX_NESTED_OUTER = 6;
    static final int NESTED_INNER = 3;

    public enum LoopType {
        FOR,
        WHILE,
        DO_WHILE,
        NESTED
    }

    public record Request(
            @NotNull LoopType type,
            @NotNull @Min(-100) @Max(100) Integer start,
            @NotNull @Min(-100) @Max(100) Integer end,
            @Min(1) @Max(10) Integer step) {

        int stepOrDefault() {
            return step == null ? 1 : step;
        }
    }

    @Override
    public String key() {
        return "loops";
    }

    @Override
    public ConceptResult generate(Request request) {
        Counter c = new Counter("i", request.start(), request.end(), request.stepOrDefault());
        int max = request.type() == LoopType.NESTED ? MAX_NESTED_OUTER : MAX_ITERATIONS;
        if (c.iterations() > max) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "TOO_MANY_ITERATIONS",
                    "This loop would run " + c.iterations() + " times; the limit is " + max + ".");
        }
        return switch (request.type()) {
            case FOR -> forLoop(c);
            case WHILE -> whileLoop(c);
            case DO_WHILE -> doWhileLoop(c);
            case NESTED -> nestedLoop(c);
        };
    }

    private ConceptResult forLoop(Counter c) {
        String code = """
                public class ForLoop {
                    public static void main(String[] args) {
                        %s
                            System.out.println("i = " + i);
                        }
                        System.out.println("done");
                    }
                }
                """.formatted(c.forHeader());
        ConceptTraceBuilder b = new ConceptTraceBuilder("ForLoop");

        b.step(3); // init + first check
        for (int i = c.start(); c.test(i); i = c.next(i)) {
            b.set("i", i);
            b.step(4);
            b.println("i = " + i);
            b.step(3); // update + check
        }
        b.unset("i");
        b.step(6);
        b.println("done");
        return new ConceptResult(key(), "for loop", code, b.build());
    }

    private ConceptResult whileLoop(Counter c) {
        String code = """
                public class WhileLoop {
                    public static void main(String[] args) {
                        int i = %d;
                        while (%s) {
                            System.out.println("i = " + i);
                            %s;
                        }
                        System.out.println("done");
                    }
                }
                """.formatted(c.start(), c.condition(), c.update());
        ConceptTraceBuilder b = new ConceptTraceBuilder("WhileLoop");

        b.step(3);
        int i = c.start();
        b.set("i", i);
        b.step(4);
        while (c.test(i)) {
            b.step(5);
            b.println("i = " + i);
            b.step(6);
            i = c.next(i);
            b.set("i", i);
            b.step(4);
        }
        b.step(8);
        b.println("done");
        return new ConceptResult(key(), "while loop", code, b.build());
    }

    private ConceptResult doWhileLoop(Counter c) {
        String code = """
                public class DoWhileLoop {
                    public static void main(String[] args) {
                        int i = %d;
                        do {
                            System.out.println("i = " + i);
                            %s;
                        } while (%s);
                        System.out.println("done");
                    }
                }
                """.formatted(c.start(), c.update(), c.condition());
        ConceptTraceBuilder b = new ConceptTraceBuilder("DoWhileLoop");

        b.step(3);
        int i = c.start();
        b.set("i", i);
        // `do {` has no bytecode, so (like JDI) the next step is the first body line.
        do {
            b.step(5);
            b.println("i = " + i);
            b.step(6);
            i = c.next(i);
            b.set("i", i);
            b.step(7); // condition check at the bottom
        } while (c.test(i));
        b.step(8);
        b.println("done");
        return new ConceptResult(key(), "do-while loop", code, b.build());
    }

    private ConceptResult nestedLoop(Counter c) {
        Counter inner = new Counter("j", 1, NESTED_INNER, 1);
        String code = """
                public class NestedLoop {
                    public static void main(String[] args) {
                        %s
                            %s
                                System.out.println(i + " x " + j + " = " + (i * j));
                            }
                        }
                        System.out.println("done");
                    }
                }
                """.formatted(c.forHeader(), inner.forHeader());
        ConceptTraceBuilder b = new ConceptTraceBuilder("NestedLoop");

        b.step(3);
        for (int i = c.start(); c.test(i); i = c.next(i)) {
            b.set("i", i);
            b.step(4);
            for (int j = inner.start(); inner.test(j); j = inner.next(j)) {
                b.set("j", j);
                b.step(5);
                b.println(i + " x " + j + " = " + (i * j));
                b.step(4);
            }
            b.unset("j");
            b.step(3);
        }
        b.unset("i");
        b.step(8);
        b.println("done");
        return new ConceptResult(key(), "nested loops", code, b.build());
    }
}
