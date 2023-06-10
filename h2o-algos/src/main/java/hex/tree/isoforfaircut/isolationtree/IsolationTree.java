package hex.tree.isoforfaircut.isolationtree;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import water.util.ArrayUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class IsolationTree {
    private static final Logger LOG = Logger.getLogger(IsolationTree.class);

    private Node[] _nodes;

    private final int _heightLimit;
    private final int _extensionLevel;
    private final int _k_planes;

    private int _isolatedPoints = 0;
    private long _notIsolatedPoints = 0;
    private int _zeroSplits = 0;
    private int _leaves = 0;
    private int _depth = 0;

    public IsolationTree(int _heightLimit, int _extensionLevel, int _k_planes) {
        this._heightLimit = _heightLimit;
        this._extensionLevel = _extensionLevel;
        this._k_planes = _k_planes;
    }
    
    public CompressedIsolationTree buildTree(double[][] data, final long seed, final int treeNum) {
        int maxNumNodesInTree = (int) Math.pow(2, _heightLimit + 1) - 1;
        _isolatedPoints = 0;
        _notIsolatedPoints = 0;
        _zeroSplits = 0;
        _leaves = 0;
        _depth = 0;
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
                node._data = null; // attempt to inform Java GC the data are not longer needed
                compressedIsolationTree.getNodes()[i] = new CompressedLeaf(node);
                if (nodeData[0].length == 1)
                    _isolatedPoints++;
                if (nodeData[0].length > 1)
                    _notIsolatedPoints += node._numRows;
                _leaves++;
            } else {
                if (rightChildIndex(i) < _nodes.length) {
                    currentHeight++;
                    _depth = currentHeight;
                    
                    SplitCriteria bestSplitCriteria = 
                            new SplitCriteria(null, 0, null,null, -Double.MAX_VALUE, null);
                    double bestSplitGain = -Double.MAX_VALUE;
                    
                    for (int k = 0; k < _k_planes; k++) {
                        SplitCriteria splitCriteria = findSplit(nodeData, seed + i + k);
                        if (splitCriteria._gain > bestSplitGain) {
                            bestSplitCriteria = splitCriteria;
                        }
                    }
                    
                    // if the data cannot be split (all features have one unique value), mark as a leaf node
                    if (bestSplitCriteria._gain == -Double.MAX_VALUE) {
                        node._external = true;
                        node._numRows = nodeData[0].length;
                        node._data = null; // attempt to inform Java GC the data are not longer needed
                        compressedIsolationTree.getNodes()[i] = new CompressedLeaf(node);
                        if (nodeData[0].length == 1)
                            _isolatedPoints++;
                        if (nodeData[0].length > 1)
                            _notIsolatedPoints += node._numRows;
                        _leaves++;
                        continue;
                    }

                    node.splitCriteria = bestSplitCriteria;
                    FilteredData ret = split(nodeData, bestSplitCriteria);

                    compressedIsolationTree.getNodes()[i] = new CompressedNode(node);
                    if (ret.left != null) {
                        _nodes[leftChildIndex(i)] = new Node(ret.left, ret.left[0].length, currentHeight);
                        compressedIsolationTree.getNodes()[leftChildIndex(i)] = new CompressedNode(_nodes[leftChildIndex(i)]);
                    } else {
                        _nodes[leftChildIndex(i)] = new Node(null, 0, currentHeight);
                        _nodes[leftChildIndex(i)]._external = true;
                        compressedIsolationTree.getNodes()[leftChildIndex(i)] = new CompressedLeaf(_nodes[leftChildIndex(i)]);
                        _leaves++;
                        _zeroSplits++;
                    }
                    if (ret.right != null) {
                        _nodes[rightChildIndex(i)] = new Node(ret.right, ret.right[0].length, currentHeight);
                        compressedIsolationTree.getNodes()[rightChildIndex(i)] = new CompressedNode(_nodes[rightChildIndex(i)]);
                    } else {
                        _nodes[rightChildIndex(i)] = new Node(null, 0, currentHeight);
                        _nodes[rightChildIndex(i)]._external = true;
                        compressedIsolationTree.getNodes()[rightChildIndex(i)] = new CompressedLeaf(_nodes[rightChildIndex(i)]);
                        _leaves++;
                        _zeroSplits++;
                    }
                } else {
                    compressedIsolationTree.getNodes()[i] = new CompressedLeaf(node);
                    _leaves++;
                }

                // attempt to inform Java GC the data are not longer needed
                node._data = null; 
                node.splitCriteria._z = null;
            }
        }
        return compressedIsolationTree;
    }
    
    private SplitCriteria findSplit(double[][] nodeData, long seed) {
        Random random = new Random(seed);
        
        double[] z = new double[nodeData[0].length]; // vector for projected data points
        double[] yMeans = new double[nodeData.length]; // feature means for standardization
        double[] yStds = new double[nodeData.length];  // feature stadard deviations for standardization
        double[] normalVector = new double[nodeData.length]; // normal vector used to project the data
        
        Arrays.fill(yMeans, 0.);
        Arrays.fill(yStds, 1.);
        Arrays.fill(normalVector, 0.);
        Arrays.fill(z, 0.);
        
        LinkedList<Integer> featuresToTry = IntStream.range(0, nodeData.length).boxed().collect(Collectors.toCollection(LinkedList::new));
        Collections.shuffle(featuresToTry, random);
        boolean atLeastOneUnique = false;
        int i = 0;
        
        while (i < _extensionLevel + 1) {
            if (featuresToTry.isEmpty()) {
                if (atLeastOneUnique)
                    break;
                else
                    return new SplitCriteria(null, 0., null, null, -Double.MAX_VALUE, null);
            }
            
            int q = featuresToTry.pop();
            double[] y = nodeData[q];
            double yMean = ArrayUtils.avg(y);
            double yStd = ArrayUtils.std(y);

            // don't use the feature if there is only one distinct value
            if (yStd == 0) {
                continue;
            } else {
                atLeastOneUnique = true;
            }

            double c = random.nextGaussian();
            
            // project and standardize data
            for (int j = 0; j < y.length; j++) {
                z[j] += ((y[j] - yMean) / yStd) * c;
            }
            
            yMeans[q] = yMean;
            yStds[q] = yStd;
            normalVector[q] = c;
            i++;
        }

        double bestGain = -Double.MAX_VALUE;
        double bestSplitPoint = 0;
        
        for (i = 1; i < z.length; i++) {
            double intercept = z[i];
            int leftSize = 0, rightSize = 0;

            for (double val : z) {
                if (val < intercept) {
                    leftSize++;
                } else {
                    rightSize++;
                }
            }
            double[] zLeft = new double[leftSize];
            double[] zRight = new double[rightSize];

            for (int j = 0, lIdx = 0, rIdx = 0; j < z.length; j++) {
                if (z[j] < intercept) {
                    zLeft[lIdx] = z[j];
                    lIdx++;
                } else {
                    zRight[rIdx] = z[j];
                    rIdx++;
                }
            }
            
            double leftStd = ArrayUtils.std(zLeft);
            double rightStd = ArrayUtils.std(zRight);
            
            double gain = -(leftSize * leftStd + rightSize * rightStd) / (leftSize + rightSize);
            
            if (gain > bestGain) {
                bestGain = gain;
                bestSplitPoint = intercept;
            }
        }

        return new SplitCriteria(normalVector, bestSplitPoint, yMeans, yStds, bestGain, z);
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
    public void logNodesNumRows(Level level) {
        StringBuilder logMessage = new StringBuilder();
        for (int i = 0; i < _nodes.length; i++) {
            if (_nodes[i] == null)
                logMessage.append(". ");
            else
                logMessage.append(_nodes[i]._numRows + " ");
        }
        LOG.log(level, logMessage.toString());
    }

    /**
     * Helper method. Print height (length of path from root) of each node in trees. Root is 0.
     */
    public void logNodesHeight(Level level) {
        StringBuilder logMessage = new StringBuilder();
        for (int i = 0; i < _nodes.length; i++) {
            if (_nodes[i] == null)
                logMessage.append(". ");
            else
                logMessage.append(_nodes[i]._height + " ");
        }
        LOG.log(level, logMessage.toString());
    }

    public static class SplitCriteria {
        /**
         * Hyperplane normal vector
         */
        protected double[] _normalVector;

        /**
         * Hyperplane intercept
         */
        protected double _intercept;

        /**
         * Means and standard deviations across the features for standardization 
         */
        protected double[] _means;
        protected double[] _stds;

        /**
         * Gain quantifies the quality of the split
         */
        protected double _gain;

        /**
         * Projected data points onto the hyperplane - stored to save computation in splitting
         */
        protected double[] _z;

        public SplitCriteria(double[] _normalVector, double _intercept, double[] _means, double[] _stds, double _gain, double[] _z) {
            this._normalVector = _normalVector;
            this._intercept = _intercept;
            this._means = _means;
            this._stds = _stds;
            this._gain = _gain;
            this._z = _z;
        }
    }

    /**
     * IsolationTree Node.
     * _data should be always null after buildTree() method because only number of rows in data is needed for
     * scoring (evaluation) stage.
     */
    public static class Node {

        /**
         * Data in this node. After computation should be null, because only _numRows is important.
         */
        private double[][] _data;

        private int _height;
        private boolean _external = false;
        private int _numRows;
        
        private SplitCriteria splitCriteria;

        public Node(double[][] data, int numRows, int currentHeight) {
            this._data = data;
            this._numRows = numRows;
            this._height = currentHeight;
            this.splitCriteria = new SplitCriteria(null, 0, null, null, -Double.MAX_VALUE, null);
        }
        
        public double[] getNormalVector() {
            return splitCriteria._normalVector;
        }

        public double getIntercept() {
            return splitCriteria._intercept;
        }
        
        public double[] getMeans() {
            return splitCriteria._means;
        }
        
        public double[] getStds() {
            return splitCriteria._stds;
        }

        public int getHeight() {
            return _height;
        }

        public int getNumRows() {
            return _numRows;
        }
    }

    /**
     * Split the data to two partitions based on the split criteria.
     * 
     * @return Object containing data for Left and Right branch of the tree.
     */
    public static FilteredData split(double[][] data, SplitCriteria splitCriteria) {
        int leftLength = 0;
        int rightLength = 0;
        
        for (int i = 0; i < splitCriteria._z.length; i++) {
            if (splitCriteria._z[i] < splitCriteria._intercept) {
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
            if (splitCriteria._z[row] < splitCriteria._intercept) {
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
    
    public int getIsolatedPoints() {
        return _isolatedPoints;
    }

    public long getNotIsolatedPoints() {
        return _notIsolatedPoints;
    }

    public int getZeroSplits() {
        return _zeroSplits;
    }

    public int getLeaves() {
        return _leaves;
    }

    public int getDepth() {
        return _depth;
    }
}
