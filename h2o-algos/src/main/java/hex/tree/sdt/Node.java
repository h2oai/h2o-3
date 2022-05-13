package hex.tree.sdt;



public class Node {
    private Integer feature;
    private Double threshold;
    private Node left;
    private Node right;
    private Integer decisionValue;
    
    public Node() {
        feature = null;
        threshold = null;
        left = null;
        right = null;
        decisionValue = null;
    }
    
    public boolean isLeaf(){
        return decisionValue != null;
    }
    
    public void setLeft(final Node left) {
        this.left = left;
    }

    public void setRight(final Node right) {
        this.right = right;
    }

    public Integer getFeature() {
        return feature;
    }

    public void setFeature(Integer feature) {
        this.feature = feature;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public Integer getDecisionValue() {
        return decisionValue;
    }

    public void setDecisionValue(Integer decisionValue) {
        this.decisionValue = decisionValue;
    }

    public Node getLeft() {
        return left;
    }
    
    public Node getRight() {
        return right;
    }
    
}
