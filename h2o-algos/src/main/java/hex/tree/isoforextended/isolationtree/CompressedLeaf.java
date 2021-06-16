package hex.tree.isoforextended.isolationtree;

/**
 * IsolationTree Leaf Node with better memory performance. Store only the data that are needed for scoring.
 */
public class CompressedLeaf extends AbstractCompressedNode {
    private final int _numRows;

    public CompressedLeaf(IsolationTree.Node node) {
        this(node.getHeight(), node.getNumRows());
    }

    public CompressedLeaf(int currentHeight, int numRows) {
        super(currentHeight);
        _numRows = numRows;
    }

    public int getNumRows() {
        return _numRows;
    }
}
