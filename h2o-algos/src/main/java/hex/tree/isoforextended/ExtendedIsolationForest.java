package hex.tree.isoforextended;

import hex.FrameSplitter;
import hex.ModelCategory;
import hex.ScoreKeeper;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.SharedTree;
import jsr166y.CountedCompleter;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;

import java.util.Arrays;

/**
 * Extended isolation forest implementation. Algorithm comes from https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends SharedTree<ExtendedIsolationForestModel,
        ExtendedIsolationForestModel.ExtendedIsolationForestParameters,
        ExtendedIsolationForestModel.ExtendedIsolationForestOutput> {

    transient IsolationTree[] _iTrees;

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
    protected double score1(Chunk[] chks, double offset, double weight, double[] fs, int row) {
        // not used in the current implementation of Extended Isolation Forest
        throw H2O.unimpl();
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
    protected ScoreKeeper.ProblemType getProblemType() {
        return ScoreKeeper.ProblemType.anomaly_detection;
    }

    @Override
    public boolean scoreZeroTrees() {
        return false;
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
        return true;
    }

    @Override
    protected void addCustomInfo(ExtendedIsolationForestModel.ExtendedIsolationForestOutput out) {
        if (_iTrees != null) {
            out.iTrees = _iTrees;
        }
    }

    private class ExtendedIsolationForestDriver extends Driver {

        @Override
        protected ExtendedIsolationForestModel makeModel(Key<ExtendedIsolationForestModel> modelKey,
                                                         ExtendedIsolationForestModel.ExtendedIsolationForestParameters parms) {
            return new ExtendedIsolationForestModel(modelKey, parms, new ExtendedIsolationForestModel.ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
        }

        @Override
        protected boolean doOOBScoring() {
            return true;
        }

        @Override
        protected void scoreAndBuildTrees(boolean oob) {
            IsolationTreeForkJoinTask [] iTreeTasks = new IsolationTreeForkJoinTask[_parms._ntrees];
            for (int t = 0; t < _parms._ntrees; t++) {
                iTreeTasks[t] = new IsolationTreeForkJoinTask();
                H2O.submitTask(iTreeTasks[t]);
            }
            for (int t = 0; t < _parms._ntrees; t++) {
                _iTrees[t] = iTreeTasks[t].getResult();
            }
            addCustomInfo(_model._output);
        }

        @Override
        protected boolean buildNextKTrees() {
            // this method is not used in the current implementation
            // tree building is implemented in scoreAndBuildTrees method
            return false;
        }

        @Override
        protected void initializeModelSpecifics() {
            _iTrees = new IsolationTree[_parms._ntrees];
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
        private Node[] _nodes;

        private Key<Frame> _frameKey;
        private int _heightLimit;
        private long _seed;
        private int _extensionLevel;

        public IsolationTree(Key<Frame> frame, int _heightLimit, long _seed, int _extensionLevel) {
            this._frameKey = frame;
            this._heightLimit = _heightLimit;
            this._seed = _seed;
            this._extensionLevel = _extensionLevel;

            int maxNumNodesInTree = (int) Math.pow(2, _heightLimit) - 1;
            this._nodes = new Node[maxNumNodesInTree]; 
        }

        /**
         * Implementation of Algorithm 2 (iTree) from paper.
         * Algorithm not need exact row vectors in scoring (evaluation) stage but just number of rows in node.
         * Therefore nodeFrames are removed from DKV after filtering.
         */
        public void buildTree() {
            Frame frame = DKV.get(_frameKey).get();
            _nodes[0] = new Node(frame._key, frame.numRows(), 0);
            for (int i = 0; i < _nodes.length; i++) {
                Node node = _nodes[i];
                if (node == null || node._external) {
                    continue;
                }
                Frame nodeFrame = node.getFrame();
                int currentHeight = node._height;
                if (node._height >= _heightLimit || nodeFrame.numRows() <= 1) {
                    node._external = true;
                    node._numRows = nodeFrame.numRows();
                    node._height = currentHeight;
                    DKV.remove(node._frameKey);
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
                    DKV.remove(nodeFrame._key);

                    if (rightChildIndex(i) < _nodes.length) {
                        _nodes[leftChildIndex(i)] = new Node(left._key, left.numRows(), currentHeight);
                        _nodes[rightChildIndex(i)] = new Node(right._key, right.numRows(), currentHeight);
                    } else {
                        DKV.remove(left._key);
                        DKV.remove(right._key);
                    }
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
         * Helper method. Print nodes size of the tree.
         */
        public void print() {
            for (int i = 0; i < _nodes.length; i++) {
                if (_nodes[i] == null)
                    Log.debug(". ");
                else
                    Log.debug(_nodes[i]._numRows + " ");
            }
            Log.debug("");
        }

        /**
         * Helper method. Print height of each node in trees. Root is 0.
         */
        public void printHeight() {
            for (int i = 0; i < _nodes.length; i++) {
                if (_nodes[i] == null)
                    Log.debug(". ");
                else
                    Log.debug(_nodes[i]._height + " ");
            }
            Log.debug("");
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
            score += node._height + averagePathLengthOfUnsuccesfullSearch(node._numRows);
            return score;
        }

        /**
         * IsolationTree Node. Naming convetion comes from Algorithm 2 (iTree) in paper.
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
        public static double averagePathLengthOfUnsuccesfullSearch(long n) {
            if (n <= 0)
                return 0;
            if (n == 2)
                return 1;
            return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0)) / n;
        }
    }

    private class IsolationTreeForkJoinTask extends H2O.H2OCountedCompleter<IsolationTreeForkJoinTask> {

        private ExtendedIsolationForest.IsolationTree iTree;

        @Override
        public void compute2() {
            try {
                Scope.enter();
                int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));
                int randomUnit = _rand.nextInt();

                // remove auto generated features
                byte[] subTypes = ArrayUtils.subarray(_train.types(), 0, _train.numCols() - 4);
                Vec[] subFrame = ArrayUtils.subarray(_train.vecs(), 0, _train.numCols() - 4);
                String[] subNames = ArrayUtils.subarray(_train.names(), 0, _train.numCols() - 4);
                String[][] subDomains = ArrayUtils.subarray2DLazy(_train.domains(), 0, _train.numCols() - 4);

                Frame subSample = new SubSampleTask(_parms._sample_size, _parms._seed + randomUnit)
                        .doAll(subTypes, subFrame).outputFrame(Key.make(), subNames, subDomains);

                iTree = new IsolationTree(subSample._key, heightLimit, _parms._seed + randomUnit, _parms.extension_level);
                iTree.buildTree();
                if (Log.isLoggingFor(Log.DEBUG)) {
                    iTree.print();
                    iTree.printHeight();   
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
        }

        @Override
        public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
            Log.err(ex);
            return true;
        }
    }

}
