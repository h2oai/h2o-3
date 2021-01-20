package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.psvm.psvm.MatrixUtils;
import jsr166y.CountedCompleter;
import org.apache.log4j.Logger;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.Random;

/**
 * Extended isolation forest implementation. Algorithm comes from https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends ModelBuilder<ExtendedIsolationForestModel,
        ExtendedIsolationForestModel.ExtendedIsolationForestParameters,
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {

    private static final Logger LOG = Logger.getLogger(ExtendedIsolationForest.class);

    transient IsolationTree[] _iTrees;
    transient Random _rand;

    // Called from an http request
    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms) {
        super(parms);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Key<ExtendedIsolationForestModel> key) {
        super(parms, key);
        init(false);
    }

    public ExtendedIsolationForest(ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms, Job job) {
        super(parms, job);
        init(false);
    }

    public ExtendedIsolationForest(boolean startup_once) {
        super(new ExtendedIsolationForestModel.ExtendedIsolationForestParameters(), startup_once);
    }

    @Override
    public void init(boolean expensive) {
        super.init(expensive);
        if (_parms.train() != null) {
            long extensionLevelMax = _parms.train().numCols() - 1;
            if (_parms.extension_level < 0 || _parms.extension_level > extensionLevelMax) {
                throw new IllegalStateException("Parameter extension_level must be in interval [0, "
                        + extensionLevelMax + "] but it is " + _parms.extension_level);
            }
            long sampleSizeMax = _parms.train().numRows();
            if (_parms._sample_size < 0 || _parms._sample_size > sampleSizeMax) {
                throw new IllegalStateException("Parameter sample_size must be in interval [0, "
                        + sampleSizeMax + "] but it is " + _parms._sample_size);
            }
        }
    }

    @Override
    protected Driver trainModelImpl() {
        return new ExtendedIsolationForestDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[]{
                ModelCategory.AnomalyDetection
        };
    }

    @Override
    public boolean isSupervised() {
        return false;
    }

    @Override
    public boolean havePojo() {
        return false;
    }

    @Override
    public boolean haveMojo() {
        return false;
    }

    protected void addCustomInfo(ExtendedIsolationForestModel.ExtendedIsolationForestOutput out) {
        if (_iTrees != null) {
            out.iTrees = _iTrees;
        }
    }

    private class ExtendedIsolationForestDriver extends Driver {

        @Override
        public void computeImpl() {
            _rand = RandomUtils.getRNG(_parms._seed);
            _iTrees = new IsolationTree[_parms._ntrees];
            ExtendedIsolationForestModel model = new ExtendedIsolationForestModel(dest(), _parms,
                    new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
            model.delete_and_lock(_job);
            IsolationTreeForkJoinTask [] iTreeTasks = new IsolationTreeForkJoinTask[_parms._ntrees];
            for (int t = 0; t < _parms._ntrees; t++) {
                iTreeTasks[t] = new IsolationTreeForkJoinTask(t);
                H2O.submitTask(iTreeTasks[t]);
            }
            for (int t = 0; t < _parms._ntrees; t++) {
                _iTrees[t] = iTreeTasks[t].getResult();
            }
            model.unlock(_job);
            addCustomInfo(model._output);
        }
    }

    /**
     * IsolationTree class implements Algorithm 2 (iTree) and Algorithm 3 (pathLength) 
     * from the Extended Isolation Forest paper.
     * 
     * The binary tree recursive implementation is replaced by array implementation.
     * It is the most simple array implementation of binary tree.
     */
    public static class IsolationTree extends Iced<IsolationTree> {
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
                    LOG.debug(i + " from " + _nodes.length + " is being prepared on tree " + _treeNum);
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
            score += node._height + averagePathLengthOfUnsuccessfullSearch(node._numRows);
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
        public static double averagePathLengthOfUnsuccessfullSearch(long n) {
            if (n <= 0)
                return 0;
            if (n == 2)
                return 1;
            return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0)) / n;
        }
    }

    private class IsolationTreeForkJoinTask extends H2O.H2OCountedCompleter<IsolationTreeForkJoinTask> {

        private ExtendedIsolationForest.IsolationTree iTree;
        private int treeNum;
        
        public IsolationTreeForkJoinTask(int treeNum) {
            super();
            this.treeNum = treeNum;     
        }

        @Override
        public void compute2() {
            try {
                Scope.enter();
                int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));
                int randomUnit = _rand.nextInt();

                Frame subSample = new SubSampleTask(_parms._sample_size, _parms._seed + randomUnit)
                        .doAll(_train.types(), _train.vecs()).outputFrame(Key.make(), _train.names(), _train.domains());
                Scope.track(subSample);

                iTree = new IsolationTree(subSample._key, heightLimit, _parms._seed + randomUnit, _parms.extension_level, treeNum);
                iTree.buildTree();
                if (LOG.isDebugEnabled()) {
                    iTree.logNodesNumRows();
                    iTree.logNodesHeight();
                }
                tryComplete();
            } finally {
                Scope.exit();
            }
        }

        /**
         * Blocking call to obtain a result of computation.
         */
        public ExtendedIsolationForest.IsolationTree getResult() {
            join();
            return this.iTree;
        }

        @Override
        public void onCompletion(CountedCompleter caller) {
            _job.update(1);
            LOG.info("Tree " + treeNum + " is done.");
        }

        @Override
        public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
            LOG.error(ex);
            return true;
        }
    }

}
