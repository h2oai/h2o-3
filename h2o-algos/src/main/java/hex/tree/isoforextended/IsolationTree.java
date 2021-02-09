package hex.tree.isoforextended;

import org.apache.log4j.Logger;
import water.Iced;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.VecUtils;

/**
 * IsolationTree class implements Algorithm 2 (iTree) and Algorithm 3 (pathLength)
 * from the Extended Isolation Forest paper.
 *
 * @author Adam Valenta
 */
public class IsolationTree extends Iced<IsolationTree> {
    private static final Logger LOG = Logger.getLogger(IsolationTree.class);

    private Node[] _nodes;
    private Node _root;

    private final double[][] _data;
    private final int _heightLimit;
    private final long _seed;
    private final int _extensionLevel;
    private final int _treeNum;

    public IsolationTree(double[][] data, int _heightLimit, long _seed, int _extensionLevel, int _treeNum) {
        this._data = data;
        this._heightLimit = _heightLimit;
        this._seed = _seed;
        this._extensionLevel = _extensionLevel;
        this._treeNum = _treeNum;
    }

    /**
     * Implementation of Algorithm 2 (iTree) from paper.
     */
    public void buildTree() {
        int maxNumNodesInTree = (int) Math.pow(2, _heightLimit) - 1;
        this._nodes = new Node[maxNumNodesInTree];
        
        _nodes[0] = new Node(_data, _data[0].length, 0);
        for (int i = 0; i < _nodes.length; i++) {
            LOG.trace((i + 1) + " from " + _nodes.length + " is being prepared on tree " + _treeNum);
            Node node = _nodes[i];
            if (node == null || node._external) {
                continue;
            }
            double[][] nodeData = node._data;
            int currentHeight = node._height;
            if (node._height >= _heightLimit || nodeData[0].length <= 1) {
                node._external = true;
                node._numRows = nodeData[0].length;
                node._height = currentHeight;
                node._data = null; // attempt to inform Java GC the data are not longer needed
            } else {
                currentHeight++;

                node._p = VecUtils.uniformDistrFromArray(nodeData, _seed + i);
                node._n = ArrayUtils.gaussianVector(
                        nodeData.length, _seed + i, nodeData.length - _extensionLevel - 1);

                FilteredData ret = extendedIsolationForestSplit(nodeData, node._p, node._n);

                if (rightChildIndex(i) < _nodes.length) {
                    if (ret.left != null) {
                        _nodes[leftChildIndex(i)] = new Node(ret.left, ret.left[0].length, currentHeight);
                    } else {
                        _nodes[leftChildIndex(i)] = new Node(null, 0, currentHeight);
                        _nodes[leftChildIndex(i)]._external = true;
                    }
                    if (ret.right != null) {
                        _nodes[rightChildIndex(i)] = new Node(ret.right, ret.right[0].length, currentHeight);
                    } else {
                        _nodes[rightChildIndex(i)] = new Node(null, 0, currentHeight);
                        _nodes[rightChildIndex(i)]._external = true;
                    }
                }
                node._data = null; // attempt to inform Java GC the data are not longer needed
            }
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
        if (_nodes == null) {
            LOG.debug("Not available for buildTreeRecursive()");
        }
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
        if (_nodes == null) {
            LOG.debug("Not available for buildTreeRecursive()");
        }
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
            double mul = ArrayUtils.subAndMul(row,node._p, node._n);
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
        score = node._height + averagePathLengthOfUnsuccessfulSearch(node._numRows);
        return score;
    }

    /**
     * Implementation of Algorithm 2 (iTree) from paper.
     */
    public void buildTreeRecursive() {
        this._root = buildTreeRecursive(_data, 0, _heightLimit);
    }

    private Node buildTreeRecursive(double[][] data, int currentHeight, int heightLimit) {
        Node node = new Node(data, data[0].length, currentHeight);
        if (currentHeight >= heightLimit || data[0].length <= 1) {
            node._external = true;
            node._numRows = data[0].length;
        } else {
            currentHeight++;
            node._p = VecUtils.uniformDistrFromArray(data, _seed + currentHeight);
            node._n = ArrayUtils.gaussianVector(data.length, _seed + currentHeight, data.length - _extensionLevel - 1);
            FilteredData ret = extendedIsolationForestSplit(data, node._p, node._n);
            if (ret.left != null) {
                node._left = buildTreeRecursive(ret.left, currentHeight, heightLimit);
            } else {
                node._left = new Node(null, 0, currentHeight);
                node._left._external = true;
            }
            if (ret.right != null) {
                node._right = buildTreeRecursive(ret.right, currentHeight, heightLimit);
            } else {
                node._right = new Node(null, 0, currentHeight);
                node._right._external = true;
            }
            node._data = null; // attempt to inform Java GC the data are not longer needed
        }
        return node;
    }

    /**
     * Implementation of Algorithm 3 (pathLength) from paper.
     */
    public double computePathLengthRecursive(final double[] row) {
        return computePathLengthRecursive(row, _root);
    }

    private double computePathLengthRecursive(final double[] row, Node node) {
        if (node._external) {
            return node._height + averagePathLengthOfUnsuccessfulSearch(node._numRows);
        } else {
            double mul = ArrayUtils.subAndMul(row,node._p, node._n);
            if (mul <= 0) {
                return computePathLengthRecursive(row, node._left);
            } else {
                return computePathLengthRecursive(row, node._right);
            }
        }
    }

    /**
     * IsolationTree Node. Naming convention comes from Algorithm 2 (iTree) in paper.
     * _data should be always null after buildTree() method because only number of rows in data is needed for
     * scoring (evaluation) stage.
     */
    private static class Node extends Iced<Node> {

        /**
         * Data in this node. After computation should be null, because only _numRows is important.
         */
        private double[][] _data;

        /**
         * Random slope
         */
        private double[] _n;

        /**
         * Random intercept point
         */
        private double[] _p;

        private int _height;
        private boolean _external = false;
        private int _numRows;
        
        private Node _left;
        private Node _right;

        public Node(double[][] data, int numRows, int currentHeight) {
            this._data = data;
            this._numRows = numRows;
            this._height = currentHeight;
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

    /**
     * Compute Extended Isolation Forest split point and filter input data with this split point in the same time.
     * <p>
     * See Algorithm 2 (iTree) in the paper.
     *
     * @return Object containing data for Left and Right branch of the tree.
     */
    public static FilteredData extendedIsolationForestSplit(double[][] data, double[] p, double[] n) {
        double[] res = new double[data[0].length];
        int leftLength = 0;
        int rightLength = 0;

        for (int row = 0; row < data[0].length; row++) {
            for (int col = 0; col < data.length; col++) {
                res[row] += (data[col][row] - p[col]) * n[col];
            }
            if (res[row] <= 0) {
                leftLength++;
            } else {
                rightLength++;
            }
        }

        double[][] left = null;
        if (leftLength > 0) {
            left = new double[data.length][leftLength];
        }

        double[][] right = null;
        if (rightLength > 0) {
            right = new double[data.length][rightLength];
        }

        for (int row = 0, rowLeft = 0, rowRight = 0; row < data[0].length; row++) {
            if (res[row] <= 0) {
                for (int col = 0; col < data.length; col++) {
                    left[col][rowLeft] = data[col][row];
                }
                rowLeft++;
            } else {
                for (int col = 0; col < data.length; col++) {
                    right[col][rowRight] = data[col][row];
                }
                rowRight++;
            }
        }
        return new FilteredData(left, right);
    }

    public static class FilteredData {
        private final double[][] left;
        private final double[][] right;

        public FilteredData(double[][] left, double[][] right) {
            this.left = left;
            this.right = right;
        }

        public double[][] getLeft() {
            return left;
        }

        public double[][] getRight() {
            return right;
        }
    }
}
