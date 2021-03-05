package hex.tree.isoforextended.isolationtree;

import water.Iced;
import water.Key;
import water.Keyed;
import water.util.ArrayUtils;
import water.util.MathUtils;

/**
 * IsolationTree structure with better memory performance. Store only the data that are needed for scoring.
 */
public class CompressedIsolationTree extends Keyed<CompressedIsolationTree> {

    private final AbstractCompressedNode[] _nodes;

    public CompressedIsolationTree(int heightLimit) {
        _key = Key.make("CompressedIsolationTree" + Key.rand());
        _nodes = new AbstractCompressedNode[(int) Math.pow(2, heightLimit + 1) - 1];
    }

    public AbstractCompressedNode[] getNodes() {
        return  _nodes;
    }

    private int leftChildIndex(int i) {
        return 2 * i + 1;
    }

    private int rightChildIndex(int i) {
        return 2 * i + 2;
    }

    private CompressedNode compressedNode(AbstractCompressedNode node) {
        return (CompressedNode) node;
    }

    private CompressedLeaf compressedLeaf(AbstractCompressedNode node) {
        return (CompressedLeaf) node;
    }

    private boolean isLeaf(AbstractCompressedNode node) {
        return node instanceof CompressedLeaf;
    }

    /**
     * Implementation of Algorithm 3 (pathLength) from paper.
     */
    public double computePathLength(double[] row) {
        int position = 0;
        AbstractCompressedNode node = _nodes[0];
        while (!isLeaf(node)) {
            CompressedNode compressedNode = compressedNode(node);
            double mul = ArrayUtils.subAndMul(row, compressedNode.getP(), compressedNode.getN());
            if (mul <= 0) {
                position = leftChildIndex(position);
            } else {
                position = rightChildIndex(position);
            }
            if (position < _nodes.length)
                node = _nodes[position];
            else
                break;
        }
        return node.getHeight() + averagePathLengthOfUnsuccessfulSearch(compressedLeaf(node).getNumRows());
    }

    /**
     * Gives the average path length of unsuccessful search in BST.
     * Comes from Algorithm 3 (pathLength) and Equation 2 in paper
     *
     * @param n number of elements
     */
    public static double averagePathLengthOfUnsuccessfulSearch(long n) {
        if (n <= 0)
            return 0;
        if (n == 2)
            return 1;
        return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0)) / n;
    }
}
