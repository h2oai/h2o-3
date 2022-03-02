package hex.tree.isoforextended.isolationtree;

import water.AutoBuffer;

import java.util.Arrays;

/**
 * IsolationTree Node with better memory performance. Store only the data that are needed for scoring.
 * Naming convention comes from Algorithm 2 (iTree) in paper.
 */
public class CompressedNode extends AbstractCompressedNode {

    /**
     * Random slope
     */
    private final double[] _n;

    /**
     * Random intercept point
     */
    private final double[] _p;

    public CompressedNode(IsolationTree.Node node) {
        this(node.getN(), node.getP(), node.getHeight());
    }

    public CompressedNode(double[] n, double[] p, int currentHeight) {
        super(currentHeight);
        this._n = n == null ? null : Arrays.copyOf(n, n.length);
        this._p = p == null ? null : Arrays.copyOf(p, p.length);
    }

    public double[] getN() {
        return _n;
    }

    public double[] getP() {
        return _p;
    }

    @Override
    public void toBytes(AutoBuffer ab) {
        ab.put1('N'); // identifier of this node type
        for (double v : _n) {
            ab.put8d(v);
        }
        for (double v : _p) {
            ab.put8d(v);
        }
    }
}
