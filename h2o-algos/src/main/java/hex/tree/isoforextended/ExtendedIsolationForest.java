package hex.tree.isoforextended;

import hex.ModelCategory;
import hex.ScoreKeeper;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.SharedTree;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.FrameUtils;
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

    transient IsolationTree[] iTrees;

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

    public int k = 0;
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
            int randomUnit =  _rand.nextInt();

            Frame subSample = new SubSampleTask(_parms._sample_size, _parms._seed + randomUnit)
                    .doAll(new byte[]{Vec.T_NUM, Vec.T_NUM} , _train.vecs(new int[]{0,1})).outputFrame(Key.make(), null, null);
//            System.out.println("subSample size: " + subSample.numRows());
            DKV.put(subSample);
            
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

    public static class IsolationTree extends Iced {
        private Node[] nodes;

        private Key<Frame> frameKey;
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
                if (node == null || node.external)
                    continue;
                Frame nodeFrame = node.getFrame();
                int currentHeight = node.height;
                if (node.height >= heightLimit || nodeFrame.numRows() <= 1) {
                    node.external = true;
                    node.size = nodeFrame.numRows();
                    node.height = currentHeight;
                } else {
                    currentHeight++;

                    node.p = VecUtils.uniformDistrFromFrameMR(nodeFrame, seed + i);
                    node.pp = FrameUtils.asDoubles(node.p);
                    node.n = VecUtils.makeGaussianVec(nodeFrame.numCols(), nodeFrame.numCols() - extensionLevel - 1, seed + i);
                    node.nn = FrameUtils.asDoubles(node.n);
                    Frame sub = MatrixUtils.subtractionMtv(nodeFrame, node.p);
                    Vec mul = MatrixUtils.productMtv2(sub, node.n);
                    Frame left = new FilterLtTask(mul, 0).doAll(nodeFrame.types(), nodeFrame).outputFrame(Key.make(), null, null);
                    Frame right = new FilterGteRightTask(mul, 0).doAll(nodeFrame.types(), nodeFrame).outputFrame(Key.make(), null, null);
                    DKV.remove(node.frameKey);
                    DKV.put(left);
                    DKV.put(right);

                    if ((2 * i + 1) < nodes.length) {
                        nodes[2 * i + 1] = new Node(left._key, left.numRows(), currentHeight);
                        nodes[2 * i + 2] = new Node(right._key, right.numRows(), currentHeight);
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

        public double computePathLength(Vec row) {
            int position = 0;
            Node node = nodes[0];
            double score = 0;
            while (!node.external) {
                Vec sub = MatrixUtils.subtractionVtv(row, Vec.makeVec(node.pp, Vec.newKey()));
                double mul = MatrixUtils.productVtV(sub, Vec.makeVec(node.nn, Vec.newKey()));
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

        private static class Node extends Iced {
            private Key<Frame> frameKey;
            private Vec n;
            private double[] nn;
            public double[] pp;
            private Vec p;

            int height;
            boolean external = false;
            long size;

            public Node(Key<Frame> frameKey, long size, int currentHeight) {
                this.frameKey = frameKey;
                this.height = currentHeight;
                this.size = size;
            }

            Frame getFrame() {
                return DKV.get(frameKey).get();
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
