package hex.tree.isoforextended.isolationtree;

import water.AutoBuffer;
import water.Key;
import water.Keyed;
import water.util.ArrayUtils;

import static hex.genmodel.algos.isoforextended.ExtendedIsolationForestMojoModel.*;

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

    private CompressedNode compressedNode(AbstractCompressedNode node) {
        assert node instanceof CompressedNode : "AbstractCompressedNode cannot be cast to CompressedNode";
        return (CompressedNode) node;
    }

    private CompressedLeaf compressedLeaf(AbstractCompressedNode node) {
        assert node instanceof CompressedLeaf : "AbstractCompressedNode cannot be cast to CompressedLeaf";
        return (CompressedLeaf) node;
    }

    private boolean isLeaf(AbstractCompressedNode node) {
        return node instanceof CompressedLeaf;
    }

    /**
     * Implementation of Algorithm 3 (pathLength) from paper.
     *
     * @param row a row of the input data
     * @return how deep the data are in the isolation tree plus estimation in case that heightLimit is hit
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

    public byte[] toBytes() {
        AutoBuffer ab = new AutoBuffer();
        assert _nodes[0] != null : "Tree is empty, there are zero nodes in the tree";
        ab.put4(compressedNode(_nodes[0]).getN().length); // size of the internal arrays
        for(int i = 0; i < _nodes.length; i++) {
            if (_nodes[i] != null) {
                ab.put4(i); // node number
                _nodes[i].toBytes(ab);
            }
        }
        return ab.bufClose();
    }
}
