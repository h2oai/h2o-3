package hex.tree.isoforextended.isolationtree;

import org.apache.log4j.Logger;
import water.util.ArrayUtils;
import water.util.VecUtils;

/**
 * IsolationTree class implements Algorithm 2 (iTree)
 * Naming convention comes from the Extended Isolation Forest paper.
 *
 * @author Adam Valenta
 */
public class IsolationTree {
    private static final Logger LOG = Logger.getLogger(IsolationTree.class);

    private Node[] _nodes;

    private final int _heightLimit;
    private final int _extensionLevel;

    public IsolationTree(int _heightLimit, int _extensionLevel) {
        this._heightLimit = _heightLimit;
        this._extensionLevel = _extensionLevel;
    }

    /**
     * Implementation of Algorithm 2 (iTree) from paper.
     */
    public CompressedIsolationTree buildTree(double[][] data, final long seed, final int treeNum) {
        int maxNumNodesInTree = (int) Math.pow(2, _heightLimit + 1) - 1;
        this._nodes = new Node[maxNumNodesInTree];
        CompressedIsolationTree compressedIsolationTree = new CompressedIsolationTree(_heightLimit);
        
        _nodes[0] = new Node(data, data[0].length, 0);
        for (int i = 0; i < _nodes.length; i++) {
            LOG.trace((i + 1) + " from " + _nodes.length + " is being prepared on tree " + treeNum);
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
                compressedIsolationTree.getNodes()[i] = new CompressedLeaf(node);
            } else {
                if (rightChildIndex(i) < _nodes.length) {
                    currentHeight++;

                    node._p = VecUtils.uniformDistrFromArray(nodeData, seed + i);
                    node._n = ArrayUtils.gaussianVector(
                            nodeData.length, seed + i, nodeData.length - _extensionLevel - 1);

                    FilteredData ret = extendedIsolationForestSplit(nodeData, node._p, node._n);

                    compressedIsolationTree.getNodes()[i] = new CompressedNode(node);
                    if (ret.left != null) {
                        _nodes[leftChildIndex(i)] = new Node(ret.left, ret.left[0].length, currentHeight);
                        compressedIsolationTree.getNodes()[leftChildIndex(i)] = new CompressedNode(_nodes[leftChildIndex(i)]);
                    } else {
                        _nodes[leftChildIndex(i)] = new Node(null, 0, currentHeight);
                        _nodes[leftChildIndex(i)]._external = true;
                        compressedIsolationTree.getNodes()[leftChildIndex(i)] = new CompressedLeaf(_nodes[leftChildIndex(i)]);
                    }
                    if (ret.right != null) {
                        _nodes[rightChildIndex(i)] = new Node(ret.right, ret.right[0].length, currentHeight);
                        compressedIsolationTree.getNodes()[rightChildIndex(i)] = new CompressedNode(_nodes[rightChildIndex(i)]);
                    } else {
                        _nodes[rightChildIndex(i)] = new Node(null, 0, currentHeight);
                        _nodes[rightChildIndex(i)]._external = true;
                        compressedIsolationTree.getNodes()[rightChildIndex(i)] = new CompressedLeaf(_nodes[rightChildIndex(i)]);
                    }
                } else {
                    compressedIsolationTree.getNodes()[i] = new CompressedLeaf(node);
                }
                node._data = null; // attempt to inform Java GC the data are not longer needed
            }
        }
        return compressedIsolationTree;
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
     * IsolationTree Node. Naming convention comes from Algorithm 2 (iTree) in paper.
     * _data should be always null after buildTree() method because only number of rows in data is needed for
     * scoring (evaluation) stage.
     */
    public static class Node {

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

        public Node(double[][] data, int numRows, int currentHeight) {
            this._data = data;
            this._numRows = numRows;
            this._height = currentHeight;
        }

        public double[] getN() {
            return _n;
        }

        public double[] getP() {
            return _p;
        }

        public int getHeight() {
            return _height;
        }

        public int getNumRows() {
            return _numRows;
        }
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
