package com.javmotion.common.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JobParsingTest {

    @Nested
    class EntryClass {

        @Test
        void picksPublicClassWithMain() {
            assertThat(EntryClassDetector.detect("""
                    class Helper { void x() {} }
                    public class BubbleSort {
                        public static void main(String[] args) {}
                    }
                    """)).isEqualTo("BubbleSort");
        }

        @Test
        void acceptsMainSignatureVariants() {
            assertThat(EntryClassDetector.detect("public class A { public static void main(String... a) {} }"))
                    .isEqualTo("A");
            assertThat(EntryClassDetector.detect("public final class B { public static void main(final String args[]) {} }"))
                    .isEqualTo("B");
        }

        @Test
        void ignoresClassNamesInCommentsAndStrings() {
            assertThat(EntryClassDetector.detect("""
                    // public class Fake { public static void main(String[] a) {} }
                    /* public class AlsoFake {} */
                    public class Real {
                        String s = "public class InString { public static void main(String[] a) {} }";
                        public static void main(String[] args) {}
                    }
                    """)).isEqualTo("Real");
        }

        @Test
        void mainInNestedClassDoesNotCountForOuterMismatch() {
            // Outer public class has no main → still the file name javac needs.
            assertThat(EntryClassDetector.detect("public class Outer { void f() {} }")).isEqualTo("Outer");
        }

        @Test
        void nonPublicClassWithMain() {
            assertThat(EntryClassDetector.detect("class Hello { public static void main(String[] a) {} }"))
                    .isEqualTo("Hello");
        }

        @Test
        void fallsBackToMain() {
            assertThat(EntryClassDetector.detect("int x = 1;")).isEqualTo("Main");
            assertThat(EntryClassDetector.detect("")).isEqualTo("Main");
        }

        @Test
        void strippingKeepsOffsets() {
            String src = "a \"b\" /* c */ d // e\nf";
            assertThat(EntryClassDetector.stripCommentsAndLiterals(src)).hasSameSizeAs(src).contains("\n");
        }
    }

    @Nested
    class Javac {

        @Test
        void parsesLineColumnAndMessage() {
            String out = """
                    Main.java:5: error: ';' expected
                            int x = 5
                                     ^
                    1 error
                    """;
            assertThat(JavacErrorParser.parse(out)).containsExactly(new CompileError(5, 18, "';' expected"));
        }

        @Test
        void includesSymbolDetailsAndHandlesSeveralErrors() {
            String out = """
                    /sandbox/src/Main.java:3: error: cannot find symbol
                            foo();
                            ^
                      symbol:   method foo()
                      location: class Main
                    /sandbox/src/Main.java:7: error: incompatible types: String cannot be converted to int
                            int y = "a";
                                    ^
                    2 errors
                    """;
            List<CompileError> errors = JavacErrorParser.parse(out);
            assertThat(errors).hasSize(2);
            assertThat(errors.get(0).line()).isEqualTo(3);
            assertThat(errors.get(0).column()).isEqualTo(9);
            assertThat(errors.get(0).message())
                    .startsWith("cannot find symbol")
                    .contains("symbol:   method foo()")
                    .contains("location: class Main");
            assertThat(errors.get(1)).isEqualTo(
                    new CompileError(7, 17, "incompatible types: String cannot be converted to int"));
        }

        @Test
        void ignoresWarningsAndWindowsLineEndings() {
            String out = "Main.java:2: warning: [removal] x\r\n  a\r\n  ^\r\nMain.java:4: error: oops\r\n  b\r\n   ^\r\n";
            assertThat(JavacErrorParser.parse(out)).containsExactly(new CompileError(4, 4, "oops"));
        }

        @Test
        void parsesRealJavacOutput(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws Exception {
            java.nio.file.Path file = dir.resolve("Main.java");
            java.nio.file.Files.writeString(file, """
                    public class Main {
                        public static void main(String[] args) {
                            int x = 5
                            undefined();
                        }
                    }
                    """);
            javax.tools.JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
            java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
            int exit = javac.run(null, null, err, "-d", dir.toString(), file.toString());

            assertThat(exit).isNotZero();
            List<CompileError> errors = JavacErrorParser.parse(err.toString());
            assertThat(errors).first().isEqualTo(new CompileError(3, 18, "';' expected"));
        }

        @Test
        void emptyOrUnknownOutput() {
            assertThat(JavacErrorParser.parse(null)).isEmpty();
            assertThat(JavacErrorParser.parse("error: file not found: X.java")).isEmpty();
        }
    }
}
