package com.javmotion.api.visualizer;

import static com.javmotion.api.visualizer.ConceptTraceBuilder.active;
import static com.javmotion.api.visualizer.ConceptTraceBuilder.found;
import static com.javmotion.api.visualizer.ConceptTraceBuilder.pointer;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

/**
 * Reverses a String with {@code rev = rev + s.charAt(i)}. Each concatenation creates a new
 * String object (new heap id, old one freed) to show that Strings are immutable.
 */
@Component
public class StringReverseVisualizer implements ConceptVisualizer<StringReverseVisualizer.Request> {

    static final int MAX_LENGTH = 20;

    public record Request(
            @NotNull
            @Size(min = 1, max = MAX_LENGTH)
            @Pattern(regexp = "[\\x20-\\x7E]*", message = "must contain printable ASCII characters only")
            String input) {}

    private static final String CODE = """
            public class StringReverse {
                public static void main(String[] args) {
                    String s = %s;
                    String rev = "";
                    for (int i = s.length() - 1; i >= 0; i--) {
                        rev = rev + s.charAt(i);
                    }
                    System.out.println(rev);
                }
            }
            """;

    @Override
    public String key() {
        return "string-reverse";
    }

    @Override
    public ConceptResult generate(Request request) {
        String s = request.input();
        ConceptTraceBuilder b = new ConceptTraceBuilder("StringReverse");

        b.step(3);
        String sId = b.newString(s);
        b.setRef("s", sId);
        b.step(4);
        String revId = b.newString("");
        b.setRef("rev", revId);
        b.step(5); // first loop check, i not assigned yet

        String rev = "";
        for (int i = s.length() - 1; i >= 0; i--) {
            b.set("i", i);
            b.step(6, active(sId, i, "i"));
            rev = rev + s.charAt(i);
            String newId = b.newString(rev);
            b.free(revId);
            revId = newId;
            b.setRef("rev", revId);
            b.step(5, pointer(sId, i, "i")); // header revisit (i-- and check still pending)
        }

        b.unset("i");
        b.step(8, found(revId));
        b.println(rev);

        return new ConceptResult(
                key(), "Reverse \"" + s + "\"", CODE.formatted(JavaSource.stringLiteral(s)), b.build());
    }
}
