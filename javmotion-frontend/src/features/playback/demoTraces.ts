import { parseTrace } from '@/lib/trace/schema'
import type { Trace } from '@/lib/trace/types'

/**
 * Fake traces used until the backend can produce real ones (Step 5 / Step 7).
 * Each `code` line-matches its fixture so the editor shows the program being played.
 * JSON is lazy-loaded so it stays out of the main bundle.
 */
export interface DemoTrace {
  id: string
  title: string
  code: string
  load: () => Promise<Trace>
}

const loadFixture = (importer: () => Promise<{ default: unknown }>) => async () =>
  parseTrace((await importer()).default)

export const DEMO_TRACES: DemoTrace[] = [
  {
    id: 'for-loop',
    title: 'For loop (sum)',
    load: loadFixture(() => import('@/test/fixtures/traces/for-loop.json')),
    code: `public class Main {
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
  },
  {
    id: 'array-bubble-sort',
    title: 'Array — bubble sort',
    load: loadFixture(() => import('@/test/fixtures/traces/array-bubble-sort.json')),
    code: `public class BubbleSort {
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
  },
  {
    id: 'string-reverse',
    title: 'String — reverse',
    load: loadFixture(() => import('@/test/fixtures/traces/string-reverse.json')),
    code: `public class StringReverse {
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
  },
]
