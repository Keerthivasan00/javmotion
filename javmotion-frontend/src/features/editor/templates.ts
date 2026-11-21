export interface CodeTemplate {
  id: string
  title: string
  code: string
}

export const TEMPLATES: CodeTemplate[] = [
  {
    id: 'hello',
    title: 'Hello World',
    code: `public class Main {
    public static void main(String[] args) {
        System.out.println("Hello, JavMotion!");
    }
}
`,
  },
  {
    id: 'loop',
    title: 'For Loop',
    code: `public class Main {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 1; i <= 5; i++) {
            sum += i;
            System.out.println("i = " + i + ", sum = " + sum);
        }
    }
}
`,
  },
  {
    id: 'reverse',
    title: 'Reverse String',
    code: `public class Main {
    public static void main(String[] args) {
        String text = "motion";
        String reversed = "";
        for (int i = text.length() - 1; i >= 0; i--) {
            reversed += text.charAt(i);
        }
        System.out.println(reversed);
    }
}
`,
  },
]

export const DEFAULT_TEMPLATE = TEMPLATES[0]
