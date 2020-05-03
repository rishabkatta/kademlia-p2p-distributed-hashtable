package edu.rit.cs;

/**
 *
 * @author rishab katta
 * Binary Tree is used to hold 16-bit binary representations where only leafnodes contain "value".
 * Used to calculate potentialNeighbors of each K-Bucket.
 */

class Node {

    Node one;
    Node zero;
    Integer value;

    Node(){

    }
    Node(Integer value) {
        this.value = value;
        one = null;
        zero = null;
    }

    @Override
    public String toString() {
        return "Node{" +
                "one=" + one +
                ", zero=" + zero +
                ", value=" + value +
                '}';
    }
}

public class BinaryTree {

    public Node populateBinaryTree(Node node, int depth, String bin_repr) {
        if(depth == 4) {
            node.value = Integer.parseInt(bin_repr, 2);
            return node;
        }
        node.one = populateBinaryTree(new Node(), depth +1, bin_repr+'1');
        node.zero = populateBinaryTree(new Node(), depth +1, bin_repr+'0');
        return node;
    }
}
