package hex.tree.isoforextended;

import hex.ModelCategory;
import hex.ScoreKeeper;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.SharedTree;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.VecUtils;

/**
 * Extended isolation forest implementation by https://arxiv.org/pdf/1811.02141.pdf paper.
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

//    TODO avalenta uncomment this method broke Python API generating
//    @Override
//    public void init(boolean expensive) {
//        super.init(expensive);
//        System.out.println("init");
//        long extensionLevelMax = _parms.train().numCols() - 1;
//        if (_parms.extension_level < 0 || _parms.extension_level > extensionLevelMax) {
//            throw new IllegalStateException("Parameter extension_level must be in interval [0, "
//                    + extensionLevelMax + "] but it is " + _parms.extension_level);
//        }
//        long sampleSizeMax = _parms.train().numRows();
//        if (_parms._sample_size < 0 || _parms._sample_size > sampleSizeMax) {
//            throw new IllegalStateException("Parameter sample_size must be in interval [0, "
//                    + sampleSizeMax + "] but it is " + _parms._sample_size);
//        }        
//    }                

    @Override
    protected double score1(Chunk[] chks, double offset, double weight, double[] fs, int row) {
        return 0;
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
        if (iTrees != null) {
            out.iTrees = iTrees;
        }
    }

    public int _k = 0;

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
        protected boolean buildNextKTrees() {
            int heightLimit = (int) Math.ceil(MathUtils.log2(_parms._sample_size));
            int randomUnit = _rand.nextInt();

            // remove auto generated features
            byte[] subTypes = ArrayUtils.subarray(_train.types(), 0, _train.numCols() - 4);
            Vec[] subFrame = ArrayUtils.subarray(_train.vecs(), 0, _train.numCols() - 4);

            Frame subSample = new SubSampleTask(_parms._sample_size, _parms._seed + randomUnit)
                    .doAll(subTypes, subFrame).outputFrame(Key.make(), null, null);

            IsolationTree iTree = new IsolationTree(subSample._key, heightLimit, _parms._seed + randomUnit, _parms.extension_level);
            iTree.buildTree();
//            iTree.clean();
//            iTree.print();
//            iTree.printHeight();
            iTrees[k] = iTree;
            k++;
            return false;
        }

        @Override
        protected void initializeModelSpecifics() {
            iTrees = new IsolationTree[_parms._ntrees];
        }

    }

    public static class IsolationTree extends Iced<IsolationTree> {
        private Node[] nodes;

        private Key<Frame> _frameKey;
        private int _heightLimit;
        private long _seed;
        private int _extensionLevel;
        private int heightLimit;
        private long seed;
        private int extensionLevel;

        public IsolationTree(Key<Frame> frame, int heightLimit, long seed, int extensionLevel) {
            this.frameKey = frame;
            this.heightLimit = heightLimit;
            this.seed = seed;
            this.extensionLevel = extensionLevel;

            this.nodes = new Node[(int) Math.pow(2, heightLimit) - 1];
        }

        public void buildTree() {
            Frame frame = DKV.get(frameKey).get();
            nodes[0] = new Node(frame._key, frame.numRows(), 0);
            for (int i = 0; i < nodes.length; i++) {
                Node node = nodes[i];
                if (node == null || node.external) {
                    continue;
                }
                Frame nodeFrame = node.getFrame();
                int currentHeight = node.height;
                if (node.height >= heightLimit || nodeFrame.numRows() <= 1) {
                    node.external = true;
                    node.size = nodeFrame.numRows();
                    node.height = currentHeight;
                    DKV.remove(node.frameKey);
                } else {
                    currentHeight++;

                    node.p = VecUtils.uniformDistrFromFrame(nodeFrame, seed + i);
                    node.n = ArrayUtils.gaussianVector(nodeFrame.numCols(), seed + i, nodeFrame.numCols() - extensionLevel - 1);
                    Frame sub = MatrixUtils.subtractionMtv(nodeFrame, node.p);
                    Vec mul = MatrixUtils.productMtvMath(sub, node.n);
                    Frame left = new FilterLtTask(mul, 0).doAll(nodeFrame.types(), nodeFrame).outputFrame(Key.make(), null, null);
                    Frame right = new FilterGteTask(mul, 0).doAll(nodeFrame.types(), nodeFrame).outputFrame(Key.make(), null, null);
                    DKV.remove(nodeFrame._key);

                    if ((2 * i + 2) < nodes.length) {
                        nodes[2 * i + 1] = new Node(left._key, left.numRows(), currentHeight);
                        nodes[2 * i + 2] = new Node(right._key, right.numRows(), currentHeight);
                    } else {
                        DKV.remove(left._key);
                        DKV.remove(right._key);
                    }
                }
            }
        }

        public void print() {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] == null)
                    System.out.print(". ");
                else
                    System.out.print(nodes[i].size + " ");
            }
            System.out.println("");
        }

        public void printHeight() {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] == null)
                    System.out.print(". ");
                else
                    System.out.print(nodes[i].height + " ");
            }
            System.out.println("");
        }

        public double computePathLength(double[] row) {
            int position = 0;
            Node node = nodes[0];
            double score = 0;
            while (!node.external) {
                double[] sub = ArrayUtils.subtract(row, node.p);
                double mul = ArrayUtils.innerProduct(sub, node.n);
                if (mul <= 0) {
                    position = 2 * position + 1;
                } else {
                    position = 2 * position + 2;
                }
                if (position < nodes.length)
                    node = nodes[position];
                else
                    break;
            }
            score += node.height + averagePathLengthOfUnsuccesfullSearch(node.size);
            return score;
        }

        private static class Node extends Iced<Node> {
            private Key<Frame> frameKey;
            private double[] n;
            public double[] p;

            int height;
            boolean external = false;
            long size;

            public Node(Key<Frame> frameKey, long size, int currentHeight) {
                this.frameKey = frameKey;
                this.height = currentHeight;
                this.size = size;
            }

            Frame getFrame() {
                return DKV.getGet(frameKey);
            }
        }

        /**
         * Gives the average path length of unsuccessful search in BST
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

    private static class PrintRowsMRTask extends MRTask<PrintRowsMRTask> {

        @Override
        public void map(Chunk[] cs) {
            for (int row = 0; row < cs[0]._len; row++) {
                for (int column = 0; column < cs.length; column++) {
                    System.out.print(cs[column].atd(row) + " ");
                }
                System.out.println();
            }
        }
    }

}
