package org.example;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static void main() {
        //5,2,13,3,8
        splitListToParts(
                new ListNode(1,
                        new ListNode(2,
                                new ListNode(3,null))),2);
    }

    public static ListNode[] splitListToParts(ListNode head, int k) {
        ListNode[] result = new ListNode[k];
        int size = 0;
        for(ListNode node=head; node != null; node = node.next) size+=1;
        int numOfElements = size/k;
        int extra  = size%k;
        ListNode cur = head;
        for(int i = 0; i < k; i++) {
            result[i] = cur;
            if(cur == null) continue;
            int partSize = numOfElements + (i < extra ? 1 : 0);
            for(int j = 0; j < numOfElements + partSize; j++) cur = cur.next;
            ListNode next = cur.next;
            cur.next = null;
            cur = next;
        }
        return result;
    }
    public static class ListNode {
      int val;
      ListNode next;
      ListNode() {}
      ListNode(int val) { this.val = val; }
      ListNode(int val, ListNode next) { this.val = val; this.next = next; }
  }
}
