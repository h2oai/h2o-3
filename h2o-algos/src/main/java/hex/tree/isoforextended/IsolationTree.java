package hex.tree.isoforextended;

import hex.psvm.psvm.MatrixUtils;
import org.apache.log4j.Logger;
import water.DKV;
import water.Iced;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.VecUtils;

/**
 * IsolationTree class implements Algorithm 2 (iTree) and Algorithm 3 (pathLength)
 * from the Extended Isolation Forest paper.
 *
 * The binary tree recursive implementation is replaced by array implementation.
 * It is the most simple array implementation of binary tree.
 */
public class IsolationTree extends Iced<IsolationTree> {
    private static final Logger LOG = Logger.getLogger(IsolationTree.class);

    private final Node[] _nodes;

    private final Key<Frame> _frameKey;
    private final int _heightLimit;
    private final long _seed;
    private final int _extensionLevel;
    private final int _treeNum;

    public IsolationTree(Key<Frame> frame, int _heightLimit, long _seed, int _extensionLevel, int _treeNum) {
        this._frameKey = frame;
        this._heightLimit = _heightLimit;
        this._seed = _seed;
        this._extensionLevel = _extensionLevel;
        this._treeNum = _treeNum;

        int maxNumNodesInTree = (int) Math.pow(2, _heightLimit) - 1;
        this._nodes = new Node[maxNumNodesInTree];
    }

    /**
     * Implementation of Algorithm 2 (iTree) from paper.
     * Algorithm not need exact row vectors in scoring (evaluation) stage but just number of rows in node.
     * Therefore nodeFrames are removed from DKV after filtering.
     */
    public void buildTree() {
        try {
            Scope.enter();
            Frame frame = DKV.get(_frameKey).get();
            Scope.track(frame);
            _nodes[0] = new Node(frame._key, frame.numRows(), 0);
            for (int i = 0; i < _nodes.length; i++) {
//                LOG.debug(i + " from " + _nodes.length + " is being prepared on tree " + _treeNum);
                Node node = _nodes[i];
                if (node == null || node._external) {
                    continue;
                }
                Frame nodeFrame = node.getFrame();
                Scope.track(nodeFrame);
                int currentHeight = node._height;
                if (node._height >= _heightLimit || nodeFrame.numRows() <= 1) {
                    node._external = true;
                    node._numRows = nodeFrame.numRows();
                    node._height = currentHeight;
                } else {
                    currentHeight++;

                    node._p = VecUtils.uniformDistrFromFrame(nodeFrame, _seed + i);
                    node._n = ArrayUtils.gaussianVector(
                            nodeFrame.numCols(), _seed + i, nodeFrame.numCols() - _extensionLevel - 1);
                    Frame sub = MatrixUtils.subtractionMtv(nodeFrame, node._p);
                    Vec mul = MatrixUtils.productMtvMath(sub, node._n);
                    Frame left = new FilterLtTask(mul, 0)
                            .doAll(nodeFrame.types(), nodeFrame)
                            .outputFrame(Key.make(), nodeFrame._names, nodeFrame.domains());
                    Frame right = new FilterGteTask(mul, 0)
                            .doAll(nodeFrame.types(), nodeFrame)
                            .outputFrame(Key.make(), nodeFrame._names, nodeFrame.domains());
                    Scope.track(left, right);

                    if (rightChildIndex(i) < _nodes.length) {
                        _nodes[leftChildIndex(i)] = new Node(left._key, left.numRows(), currentHeight);
                        _nodes[rightChildIndex(i)] = new Node(right._key, right.numRows(), currentHeight);
                    }
                }
            }
        }
        finally {
            Scope.exit();
        }
    }

    private int leftChildIndex(int i) {
        return 2 * i + 1;
    }

    private int rightChildIndex(int i) {
        return 2 * i + 2;
    }

    /**
     * Helper method. Print nodes' size of the tree.
     */
    public void logNodesNumRows() {
        StringBuilder logMessage = new StringBuilder();
        for (int i = 0; i < _nodes.length; i++) {
            if (_nodes[i] == null)
                logMessage.append(". ");
            else
                logMessage.append(_nodes[i]._numRows + " ");
        }
        LOG.debug(logMessage.toString());
    }

    /**
     * Helper method. Print height (length of path from root) of each node in trees. Root is 0.
     */
    public void logNodesHeight() {
        StringBuilder logMessage = new StringBuilder();
        for (int i = 0; i < _nodes.length; i++) {
            if (_nodes[i] == null)
                logMessage.append(". ");
            else
                logMessage.append(_nodes[i]._height + " ");
        }
        LOG.debug(logMessage.toString());
    }

    /**
     * Implementation of Algorithm 3 (pathLength) from paper.
     */
    public double computePathLength(double[] row) {
        int position = 0;
        Node node = _nodes[0];
        double score = 0;
        while (!node._external) {
            double[] sub = ArrayUtils.subtract(row, node._p);
            double mul = ArrayUtils.innerProduct(sub, node._n);
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
        score += node._height + averagePathLengthOfUnsuccessfulSearch(node._numRows);
        return score;
    }

    /**
     * IsolationTree Node. Naming convention comes from Algorithm 2 (iTree) in paper.
     * frameKey should be always empty after buildTree() method because only number of rows in Frame is needed for
     * scoring (evaluation) stage.
     */
    private static class Node extends Iced<Node> {
        private Key<Frame> _frameKey;
        private double[] _n;
        private double[] _p;

        private int _height;
        private boolean _external = false;
        private long _numRows;

        public Node(Key<Frame> _frameKey, long _numRows, int currentHeight) {
            this._frameKey = _frameKey;
            this._height = currentHeight;
            this._numRows = _numRows;
        }

        Frame getFrame() {
            return DKV.getGet(_frameKey);
        }
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
