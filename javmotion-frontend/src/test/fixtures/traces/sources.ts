/** Java source for each hand-made fixture in this folder (line numbers match the traces). */
export const FIXTURE_SOURCES: Record<string, string> = {
  'for-loop': `public class Main {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 1; i <= 3; i++) {
            sum += i;
            System.out.println("sum = " + sum);
        }
        System.out.println("done");
    }
}
`,
  'array-bubble-sort': `public class BubbleSort {
    public static void main(String[] args) {
        int[] arr = {5, 1, 4, 2};
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
`,
  'string-reverse': `public class StringReverse {
    public static void main(String[] args) {
        String s = "code";
        String rev = "";
        for (int i = s.length() - 1; i >= 0; i--) {
            rev = rev + s.charAt(i);
        }
        System.out.println(rev);
    }
}
`,
}
