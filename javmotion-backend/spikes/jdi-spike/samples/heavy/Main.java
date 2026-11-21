public class Main {
    public static void main(String[] args) {
        int[] data = new int[50];
        long sum = 0;
        for (int i = 0; i < 100000; i++) {
            data[i % data.length] = i;
            sum += i;
        }
        System.out.println(sum);
    }
}
