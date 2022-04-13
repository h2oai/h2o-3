package hex.tree.sdt;

import water.fvec.Frame;
import water.fvec.Vec;

/**
 * Single Decision Tree
 */
public class SDT {
    private Frame trainX;
    private Vec trainY;
    private Integer maxDepth;
    private String[] features;
    
    private Integer actualDepth;
    
    private Node root;
    
    public SDT(final Frame trainX, final Vec trainY, final Integer maxDepth) {
        this.trainX = trainX;
        this.trainY = trainY;
        this.maxDepth = maxDepth;
        
        this.actualDepth = 0;
        this.features = trainX.names();
    }

    public SDT(final Frame trainX, final Vec trainY) {
        this.trainX = trainX;
        this.trainY = trainY;
        this.maxDepth = Integer.MAX_VALUE;
        
        this.actualDepth = 0;
        this.features = trainX.names();
    }
    
    public Node buildSubtree(final Frame splitX, final Vec splitY) {
        Node subtreeRoot = new Node();
        if(actualDepth >= maxDepth) {
            // set isLeaf (if it makes sense) // todo
            // set decision value to node
            return null;
        }
        // find split (feature and threshold)
        // split data
        subtreeRoot.setLeft(buildSubtree(leftSplitX, leftSplitY));
        subtreeRoot.setRight(buildSubtree(rightSplitX, rightSplitY));
        return subtreeRoot;
    }
    
    public void train() {
        root = buildSubtree(trainX, trainY);
    }

    public Vec predict(final Frame splitX) {
        // task to recursively apply nodes criteria to each row, so it can be parallelized
        
    }

    public Frame splitXBy(final String /*Integer*/ feature, final Double threshold /* are all features double ? todo*/) {
        
    }

}


