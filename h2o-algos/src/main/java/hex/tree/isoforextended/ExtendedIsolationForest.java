package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ScoreKeeper;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.DTree;
import hex.tree.SharedTree;
import hex.tree.SharedTreeModel;
import hex.tree.isofor.IsolationForest;
import hex.tree.isofor.IsolationForestModel;
import water.Iced;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.IcedInt;
import water.util.MathUtils;
import water.util.RandomUtils;
import water.util.VecUtils;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extended isolation forest implementation by https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends SharedTree<ExtendedIsolationForestModel,
        ExtendedIsolationForestParameters,
        ExtendedIsolationForestOutput> {

    public static final int SUB_SAMPLING_SIZE = 256;
//    public ITree[] iTrees;

    public ExtendedIsolationForest(ExtendedIsolationForestParameters parms) {
        super(parms);
        init(false);
    }

    @Override
    protected double score1(Chunk[] chks, double offset, double weight, double[] fs, int row) {
        System.out.println("score1");
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
    public boolean isSupervised() {
        return false;
    }

    private static class PrintRowsMRTask extends MRTask<PrintRowsMRTask> {

        @Override
        public void map(Chunk[] cs) {
            for (int row = 0; row < cs[0]._len; row++) {
                for (int column = 0; column < cs.length; column++) {
                    System.out.print(cs[column].atd(row) + " ");
                }
                System.out.println("");
            }
        }
    }

    private class ExtendedIsolationForestDriver extends Driver {
        
        
        @Override
        public void computeImpl() {
            super.computeImpl();
        }

        @Override
        protected ExtendedIsolationForestModel makeModel(Key<ExtendedIsolationForestModel> modelKey, ExtendedIsolationForestParameters parms) {
            return new ExtendedIsolationForestModel(modelKey, parms, new ExtendedIsolationForestOutput(ExtendedIsolationForest.this));
        }

        @Override
        protected boolean doOOBScoring() {
            return true;
        }

        int k;
        @Override
        protected boolean buildNextKTrees() {
            int heightLimit = (int) Math.ceil(MathUtils.log2(SUB_SAMPLING_SIZE));


            // create subsamble see EIF/IF paper algorithm 1 
            // TODO avalenta make subsampling randomized and guarantee given size for small datasets
            Frame subSample = new SubSampleTask(SUB_SAMPLING_SIZE, _parms._seed + k)
                    .doAll(new byte[]{Vec.T_NUM, Vec.T_NUM} , _train.vecs(new int[]{0,1})).outputFrame();
            System.out.println("subSample size: " + subSample.numRows());

//            ITree iTree = new ITree(subSample, heightLimit, k);
//            iTree.buildTree();
//            iTree.print();
            
            _model._output.test = new Test(); 

//            // compute score for given point
//            double pathLength = 0;
//            double iTreeScore = iTree.computePathLength(Vec.makeVec(new double[]{0, 0}, Vec.newKey()));
//            pathLength += iTreeScore;
//            System.out.println("iTree " + k + " pathLength = " + iTreeScore);
//            pathLength = pathLength / 1;//iTrees.length;
//            System.out.println("pathLength " + pathLength);
//            double anomalyScore = anomalyScore(pathLength);
//            System.out.println("Anomaly score " + anomalyScore);        
//            k++;
            return false;
        }

        @Override
        protected void initializeModelSpecifics() {

        }
    }
    
    public class Test extends Iced {
        int k;
    }
    
//    private class ITree {
//        private Node[] nodes;
//
//        private Frame frame;
//        private int heightLimit;
//        private int treeNum;
//        
//        public ITree(Frame frame, int heightLimit, int treeNum) {
//            this.frame = frame;
//            this.heightLimit = heightLimit;
//            this.treeNum = treeNum;
//
//            this.nodes = new Node[(int) Math.pow(2, heightLimit) - 1];
//        }
//
//        public void buildTree() {
//            int currentHeight = 0;
//            nodes[0] = new Node(frame, 0);
//            for (int i = 0; i < nodes.length; i++) {
//                Node node = nodes[i];
//                if (node == null || node.external)
//                    continue;
//                if (node.currentHeight >= heightLimit || node.frame.numRows() <= 1) {
//                    node.external = true;
//                    node.size = node.frame.numRows();
//                    node.currentHeight = currentHeight;
//                } else {
//                    currentHeight++;
//                    node.p = VecUtils.uniformDistrFromFrameMR(node.frame, _parms._seed + i + treeNum);
//                    node.n = VecUtils.makeGaussianVec(node.frame.numCols(), node.frame.numCols() - _parms.extensionLevel - 1, _parms._seed + i + treeNum);
//                    Frame sub = MatrixUtils.subtractionMtv(node.frame, node.p);
//                    Vec mul = MatrixUtils.productMtv2(sub, node.n);
//                    Frame left = new FilterLteTask(mul, 0).doAll(node.frame.types(), node.frame).outputFrame();
//                    Frame right = new FilterGtRightTask(mul, 0).doAll(node.frame.types(), node.frame).outputFrame();
//
//                    if ((2 * i + 1) < nodes.length) {
//                        nodes[2 * i + 1] = new Node(left, currentHeight);
//                        nodes[2 * i + 2] = new Node(right, currentHeight);
//                    }
//                }
//            }
//        }
//
//        public void print() {
//            for (int i = 0; i < 16; i++) {
//                if (nodes[i] == null)
//                    System.out.print("null ");
//                else
//                    System.out.print(nodes[i].size + " ");
//            }
//            System.out.println("");
//        }
//
//        public double computePathLength(Vec row) {
//            int position = 0;
//            Node node = nodes[0];
//            double score = 0;
//            while (!node.external) {
//                Vec sub = MatrixUtils.subtractionVtv(row, node.p);
//                double mul = MatrixUtils.productVtV(sub, node.n);
//                if (mul <= 0) {
//                    position = 2 * position + 1;
//                } else {
//                    position = 2 * position + 2;
//                }
//                if (position < nodes.length)
//                    node = nodes[position];
//                else
//                    break;
//            }
//            score += node.currentHeight + avgPathLengthUnsucSearch(node.size);
//            return score;
//        }
//    }


    private class FilterLteTask extends MRTask<FilterLteTask> {

        private double value;
        private Vec mul;

        public FilterLteTask(Vec vec, double value) {
            this.mul = vec;
            this.value = value;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                double num = mul.at(cs[0].start() + row);
                if (num <= value) {
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }

    private class FilterGtRightTask extends MRTask<FilterGtRightTask> {

        private double value;
        private Vec mul;

        public FilterGtRightTask(Vec mul, double value) {
            this.mul = mul;
            this.value = value;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                double num = mul.at(cs[0].start() + row);
                if (num > value) {
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }

    private class Node {
        private Frame frame;
        private Vec n;
        private Vec p;

        private int currentHeight;
        private boolean external = false;
        private long size;

        public Node(Frame frame, int currentHeight) {
            this.frame = frame;
            this.currentHeight = currentHeight;
            this.size = frame.numRows();
        }
    }    

    private class SubSampleTask extends MRTask<SubSampleTask> {

        private int subSampleSize;
        private Random random;

        private AtomicInteger currentSubSampleSize = new AtomicInteger(-1);

        public SubSampleTask(int subSampleSize, long seed) {
            this.subSampleSize = subSampleSize;
            this.random = RandomUtils.getRNG(seed);
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                if (random.nextBoolean()  && (currentSubSampleSize.incrementAndGet() < subSampleSize)) {
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }

    /**
     * Gives the average path length of unsuccessful search in BST
     *
     * @param n number of elements
     */
    private double avgPathLengthUnsucSearch(long n) {
        if (n <= 0)
            return 0;
        return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0)) / n;
    }

    private double anomalyScore(double pathLength) {
        return Math.pow(2, -1 * pathLength / MathUtils.harmonicNumberEstimation(_train.numRows()));
    }
}
