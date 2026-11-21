public class Main {
    static class Node {
        int value;
        Node next;

        Node(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        Node head = new Node(1);
        head.next = new Node(2);
        head.next.next = head;
        Integer boxed = 42;
        double ratio = 0.0 / 0.0;
        System.out.print("no newline");
        System.out.println(head.next.value);
    }
}
