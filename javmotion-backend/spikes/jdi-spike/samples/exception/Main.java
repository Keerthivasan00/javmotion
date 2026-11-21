public class Main {
    public static void main(String[] args) {
        int[] a = {1, 2, 3};
        int total = 0;
        for (int i = 0; i <= a.length; i++) {
            total += a[i];
        }
        System.out.println(total);
    }
}
