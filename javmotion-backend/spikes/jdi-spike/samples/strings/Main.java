public class Main {
    public static void main(String[] args) {
        String s = "code";
        String rev = "";
        for (int i = s.length() - 1; i >= 0; i--) {
            rev = rev + s.charAt(i);
        }
        int[] arr = {5, 1, 4};
        int tmp = arr[0];
        arr[0] = arr[1];
        arr[1] = tmp;
        int[] big = new int[150];
        System.out.println(rev + " " + arr[0] + " " + big.length);
    }
}
