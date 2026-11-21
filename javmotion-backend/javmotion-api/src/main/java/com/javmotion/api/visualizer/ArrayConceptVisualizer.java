package com.javmotion.api.visualizer;

import static com.javmotion.api.visualizer.ConceptTraceBuilder.compare;
import static com.javmotion.api.visualizer.ConceptTraceBuilder.found;
import static com.javmotion.api.visualizer.ConceptTraceBuilder.pointer;
import static com.javmotion.api.visualizer.ConceptTraceBuilder.swap;

import com.javmotion.api.common.error.ApiException;
import com.javmotion.common.trace.Highlight;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Sorting and searching on a small int array, with compare / swap / found highlights. */
@Component
public class ArrayConceptVisualizer implements ConceptVisualizer<ArrayConceptVisualizer.Request> {

    static final int MAX_VALUES = 12;

    public enum Operation {
        BUBBLE_SORT,
        SELECTION_SORT,
        LINEAR_SEARCH,
        BINARY_SEARCH
    }

    public record Request(
            @NotNull Operation operation,
            @NotNull @Size(min = 1, max = MAX_VALUES) List<@NotNull @Min(-999) @Max(999) Integer> values,
            @Min(-999) @Max(999) Integer target) {}

    @Override
    public String key() {
        return "array";
    }

    @Override
    public ConceptResult generate(Request request) {
        int[] arr = request.values().stream().mapToInt(Integer::intValue).toArray();
        return switch (request.operation()) {
            case BUBBLE_SORT -> bubbleSort(arr);
            case SELECTION_SORT -> selectionSort(arr);
            case LINEAR_SEARCH -> linearSearch(arr, requireTarget(request));
            case BINARY_SEARCH -> binarySearch(requireSorted(arr), requireTarget(request));
        };
    }

    // ------------------------------------------------------------------ bubble sort

    private static final String BUBBLE_CODE = """
            public class BubbleSort {
                public static void main(String[] args) {
                    int[] arr = %s;
                    int n = arr.length;
                    for (int i = 0; i < n - 1; i++) {
                        for (int j = 0; j < n - 1 - i; j++) {
                            if (arr[j] > arr[j + 1]) {
                                int temp = arr[j];
                                arr[j] = arr[j + 1];
                                arr[j + 1] = temp;
                            }
                        }
                    }
                    System.out.println(java.util.Arrays.toString(arr));
                }
            }
            """;

    private ConceptResult bubbleSort(int[] input) {
        int[] arr = input.clone();
        int n = arr.length;
        ConceptTraceBuilder b = new ConceptTraceBuilder("BubbleSort");
        List<Integer> sorted = new ArrayList<>();

        b.step(3);
        String h = b.newIntArray(arr);
        b.setRef("arr", h);
        b.step(4);
        b.set("n", n);
        b.step(5);
        for (int i = 0; i < n - 1; i++) {
            b.set("i", i);
            b.step(6, foundAll(h, sorted));
            for (int j = 0; j < n - 1 - i; j++) {
                b.set("j", j);
                b.step(7, with(foundAll(h, sorted), compare(h, j), compare(h, j + 1), pointer(h, j, "j")));
                if (arr[j] > arr[j + 1]) {
                    Highlight[] swapping = with(foundAll(h, sorted), swap(h, j), swap(h, j + 1), pointer(h, j, "j"));
                    b.step(8, swapping);
                    int temp = arr[j];
                    b.set("temp", temp);
                    b.step(9, swapping);
                    arr[j] = arr[j + 1];
                    b.updateIntArray(h, arr);
                    b.step(10, swapping);
                    arr[j + 1] = temp;
                    b.updateIntArray(h, arr);
                    b.unset("temp");
                }
                b.step(6, with(foundAll(h, sorted), pointer(h, j, "j")));
            }
            b.unset("j");
            sorted.add(n - 1 - i);
            b.step(5, foundAll(h, sorted));
        }
        b.unset("i");
        b.step(14, foundAll(h, all(n)));
        b.println(JavaSource.arraysToString(arr));

        return result("Bubble sort", BUBBLE_CODE, input, b);
    }

    // ------------------------------------------------------------------ selection sort

    private static final String SELECTION_CODE = """
            public class SelectionSort {
                public static void main(String[] args) {
                    int[] arr = %s;
                    int n = arr.length;
                    for (int i = 0; i < n - 1; i++) {
                        int min = i;
                        for (int j = i + 1; j < n; j++) {
                            if (arr[j] < arr[min]) {
                                min = j;
                            }
                        }
                        int temp = arr[i];
                        arr[i] = arr[min];
                        arr[min] = temp;
                    }
                    System.out.println(java.util.Arrays.toString(arr));
                }
            }
            """;

    private ConceptResult selectionSort(int[] input) {
        int[] arr = input.clone();
        int n = arr.length;
        ConceptTraceBuilder b = new ConceptTraceBuilder("SelectionSort");
        List<Integer> sorted = new ArrayList<>();

        b.step(3);
        String h = b.newIntArray(arr);
        b.setRef("arr", h);
        b.step(4);
        b.set("n", n);
        b.step(5);
        for (int i = 0; i < n - 1; i++) {
            b.set("i", i);
            b.step(6, with(foundAll(h, sorted), pointer(h, i, "i")));
            int min = i;
            b.set("min", min);
            b.step(7, with(foundAll(h, sorted), pointer(h, i, "i"), pointer(h, min, "min")));
            for (int j = i + 1; j < n; j++) {
                b.set("j", j);
                Highlight[] comparing = with(
                        foundAll(h, sorted), compare(h, j), compare(h, min),
                        pointer(h, i, "i"), pointer(h, j, "j"), pointer(h, min, "min"));
                b.step(8, comparing);
                if (arr[j] < arr[min]) {
                    b.step(9, comparing);
                    min = j;
                    b.set("min", min);
                }
                b.step(7, with(foundAll(h, sorted), pointer(h, i, "i"), pointer(h, j, "j"), pointer(h, min, "min")));
            }
            b.unset("j");
            Highlight[] swapping = with(foundAll(h, sorted), swap(h, i), swap(h, min), pointer(h, i, "i"), pointer(h, min, "min"));
            b.step(12, swapping);
            int temp = arr[i];
            b.set("temp", temp);
            b.step(13, swapping);
            arr[i] = arr[min];
            b.updateIntArray(h, arr);
            b.step(14, swapping);
            arr[min] = temp;
            b.updateIntArray(h, arr);
            b.unset("temp").unset("min");
            sorted.add(i);
            b.step(5, foundAll(h, sorted));
        }
        b.unset("i");
        b.step(16, foundAll(h, all(n)));
        b.println(JavaSource.arraysToString(arr));

        return result("Selection sort", SELECTION_CODE, input, b);
    }

    // ------------------------------------------------------------------ linear search

    private static final String LINEAR_CODE = """
            public class LinearSearch {
                public static void main(String[] args) {
                    int[] arr = %s;
                    int target = %d;
                    int found = -1;
                    for (int i = 0; i < arr.length; i++) {
                        if (arr[i] == target) {
                            found = i;
                            break;
                        }
                    }
                    System.out.println(found);
                }
            }
            """;

    private ConceptResult linearSearch(int[] arr, int target) {
        ConceptTraceBuilder b = new ConceptTraceBuilder("LinearSearch");

        b.step(3);
        String h = b.newIntArray(arr);
        b.setRef("arr", h);
        b.step(4);
        b.set("target", target);
        b.step(5);
        b.set("found", -1);
        b.step(6);
        int found = -1;
        for (int i = 0; i < arr.length; i++) {
            b.set("i", i);
            b.step(7, compare(h, i), pointer(h, i, "i"));
            if (arr[i] == target) {
                b.step(8, found(h, i), pointer(h, i, "i"));
                found = i;
                b.set("found", found);
                b.step(9, found(h, i), pointer(h, i, "i"));
                break;
            }
            b.step(6, pointer(h, i, "i"));
        }
        b.unset("i");
        b.step(12, found >= 0 ? new Highlight[] {found(h, found)} : new Highlight[0]);
        b.println(String.valueOf(found));

        return new ConceptResult(
                key(), "Linear search for " + target,
                LINEAR_CODE.formatted(JavaSource.intArrayLiteral(arr), target), b.build());
    }

    // ------------------------------------------------------------------ binary search

    private static final String BINARY_CODE = """
            public class BinarySearch {
                public static void main(String[] args) {
                    int[] arr = %s;
                    int target = %d;
                    int low = 0;
                    int high = arr.length - 1;
                    int found = -1;
                    while (low <= high) {
                        int mid = (low + high) / 2;
                        if (arr[mid] == target) {
                            found = mid;
                            break;
                        } else if (arr[mid] < target) {
                            low = mid + 1;
                        } else {
                            high = mid - 1;
                        }
                    }
                    System.out.println(found);
                }
            }
            """;

    private ConceptResult binarySearch(int[] arr, int target) {
        ConceptTraceBuilder b = new ConceptTraceBuilder("BinarySearch");

        b.step(3);
        String h = b.newIntArray(arr);
        b.setRef("arr", h);
        b.step(4);
        b.set("target", target);
        b.step(5);
        int low = 0;
        b.set("low", low);
        b.step(6);
        int high = arr.length - 1;
        b.set("high", high);
        b.step(7);
        int found = -1;
        b.set("found", found);
        b.step(8, bounds(h, arr.length, low, high, null));
        while (low <= high) {
            b.step(9, bounds(h, arr.length, low, high, null));
            int mid = (low + high) / 2;
            b.set("mid", mid);
            b.step(10, with(bounds(h, arr.length, low, high, mid), compare(h, mid)));
            if (arr[mid] == target) {
                b.step(11, with(bounds(h, arr.length, low, high, mid), found(h, mid)));
                found = mid;
                b.set("found", found);
                b.step(12, with(bounds(h, arr.length, low, high, mid), found(h, mid)));
                break;
            }
            b.step(13, with(bounds(h, arr.length, low, high, mid), compare(h, mid)));
            if (arr[mid] < target) {
                b.step(14, bounds(h, arr.length, low, high, mid));
                low = mid + 1;
                b.set("low", low);
            } else {
                b.step(16, bounds(h, arr.length, low, high, mid));
                high = mid - 1;
                b.set("high", high);
            }
            b.unset("mid");
            b.step(8, bounds(h, arr.length, low, high, null));
        }
        b.unset("mid");
        b.step(19, found >= 0 ? new Highlight[] {found(h, found)} : new Highlight[0]);
        b.println(String.valueOf(found));

        return new ConceptResult(
                key(), "Binary search for " + target,
                BINARY_CODE.formatted(JavaSource.intArrayLiteral(arr), target), b.build());
    }

    /** low / mid / high pointers; a pointer outside the array (e.g. high = -1) is not drawn. */
    private static Highlight[] bounds(String h, int length, int low, int high, Integer mid) {
        List<Highlight> out = new ArrayList<>();
        if (low >= 0 && low < length) out.add(pointer(h, low, "low"));
        if (mid != null) out.add(pointer(h, mid, "mid"));
        if (high >= 0 && high < length) out.add(pointer(h, high, "high"));
        return out.toArray(Highlight[]::new);
    }

    // ------------------------------------------------------------------ helpers

    private ConceptResult result(String title, String code, int[] input, ConceptTraceBuilder b) {
        return new ConceptResult(key(), title, code.formatted(JavaSource.intArrayLiteral(input)), b.build());
    }

    private static int requireTarget(Request request) {
        if (request.target() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TARGET_REQUIRED", "Searching needs a target value.");
        }
        return request.target();
    }

    private static int[] requireSorted(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            if (arr[i - 1] > arr[i]) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "ARRAY_NOT_SORTED", "Binary search needs values sorted in ascending order.");
            }
        }
        return arr;
    }

    private static Highlight[] foundAll(String h, List<Integer> indexes) {
        return indexes.stream().map(i -> found(h, i)).toArray(Highlight[]::new);
    }

    private static List<Integer> all(int n) {
        return IntStream.range(0, n).boxed().toList();
    }

    private static Highlight[] with(Highlight[] base, Highlight... more) {
        Highlight[] out = Arrays.copyOf(base, base.length + more.length);
        System.arraycopy(more, 0, out, base.length, more.length);
        return out;
    }
}
