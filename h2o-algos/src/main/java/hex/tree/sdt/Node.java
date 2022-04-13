package hex.tree.sdt;

public class Node {
    private String feature;
    private Double threshold;
    private Node left;
    private Node right;
    private Double decisionValue;
    
    public Node() {}
    
    public boolean isLeaf(){
        return decisionValue != null;
    }
    
    public void setLeft(final Node left) {
        this.left = left;
    }

    public void setRight(final Node right) {
        this.right = right;
    }
    
}
