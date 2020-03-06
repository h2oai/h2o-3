package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.SharedTree;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.MathUtils;
import water.util.RandomUtils;
import water.util.VecUtils;

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
    public ITree [] iTrees;

    public ExtendedIsolationForest(ExtendedIsolationForestParameters parms) {
        super(parms);
        init(false);
    }

    @Override
    protected double score1(Chunk[] chks, double offset, double weight, double[] fs, int row) {
        return 0;
    }

    @Override
    protected ModelBuilder<ExtendedIsolationForestModel,
            ExtendedIsolationForestParameters,
            ExtendedIsolationForestOutput>.Driver trainModelImpl() {
        return new ExtendedIsolationForestDriver();
    }

    @Override
    public ModelCategory[] can_build() {
        return new ModelCategory[0];
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

    private class ExtendedIsolationForestDriver extends ModelBuilder<ExtendedIsolationForestModel,
            ExtendedIsolationForestParameters,
            ExtendedIsolationForestOutput>.Driver {

        @Override
        public void computeImpl() {
            int heightLimit = (int) Math.ceil(MathUtils.log2(SUB_SAMPLING_SIZE));

            _parms._ntrees = 100;
            
            // build Isolation Forest
            iTrees = new ITree[_parms._ntrees];
            for (int i = 0; i < _parms._ntrees; i++) {
                // create subsamble see EIF/IF paper algorithm 1 
                // TODO avalenta make subsampling randomized and guarantee given size for small datasets
                Frame subSample = new SubSampleTask(SUB_SAMPLING_SIZE, _parms._seed + i)
                        .doAll(_train.types(), _train).outputFrame();
//                System.out.println("subSample size: " + subSample.numRows());
                ITree iTree = new ITree(subSample, 0, heightLimit, i);
                iTrees[i] = iTree;
            }

//            iTrees[0].print2DUtil(iTrees[0], 0);
            
            // compute score for given point
            double pathLength = 0;
            for (int i = 0; i < iTrees.length; i++) {
                double iTreeScore = iTrees[i].computePathLength(Vec.makeVec(new double[]{0, 0}, Vec.newKey()), 0);
                pathLength += iTreeScore;
//                System.out.println("iTree " + i + " pathLength = " + iTreeScore);
            }
            pathLength = pathLength / iTrees.length;
            System.out.println("pathLength " + pathLength);
            double anomalyScore = anomalyScore(pathLength);
            System.out.println("Anomaly score " + anomalyScore);
        }
    }
    
    private class ITree {
        private Frame frame; // TODO avalenta not necessaty to save
        private Vec n;
        private Vec p;
        private ITree leftNode;
        private ITree rightNode;

        private int currentHeight;
        private boolean external = false;
        private long size;

        public ITree(Frame frame, int currentHeight, int heightLimit, int treeNum) {
            this.frame = frame;
            this.currentHeight = currentHeight;
            
            if (currentHeight >= heightLimit || frame.numRows() <= 1) {
                external = true;
                size = frame.numRows();
            } else {
                p = VecUtils.uniformDistrFromFrameMR(frame, _parms._seed + currentHeight + treeNum);
                n = VecUtils.makeGaussianVec(frame.numCols(), frame.numCols() - _parms.extensionLevel - 1, _parms._seed + currentHeight + treeNum);
                Frame sub = MatrixUtils.subtractionMtv(frame, p);
                Vec mul = MatrixUtils.productMtv2(sub, n);
                Frame left = new FilterLteTask(mul, 0).doAll(frame.types(), frame).outputFrame();
                Frame right = new FilterGtRightTask(mul, 0).doAll(frame.types(), frame).outputFrame();
                
                leftNode = new ITree(left, currentHeight + 1, heightLimit, treeNum);
                rightNode = new ITree(right, currentHeight + 1, heightLimit, treeNum);
            }
        }
        
        public void print2DUtil(ITree root, int space)
        {
            int COUNT = 10;
            // Base case  
            if (root == null)
                return;

            // Increase distance between levels  
            space += COUNT;

            // Process right child first  
            print2DUtil(root.rightNode, space);

            // Print current node after space  
            // count  
            System.out.print("\n");
            for (int i = COUNT; i < space; i++)
                System.out.print(" ");
            System.out.print(root.frame.numRows() + "\n");

            // Process left child  
            print2DUtil(root.leftNode, space);
        }

        public Frame getFrame() {
            return frame;
        }

        public Vec getN() {
            return n;
        }

        public Vec getP() {
            return p;
        }

        public ITree getLeftNode() {
            return leftNode;
        }

        public ITree getRightNode() {
            return rightNode;
        }
        
        public double computePathLength(Vec x, double score) {
            if (external) {
                return currentHeight + avgPathLengthUnsucSearch(size);
            } else {
                Vec sub = MatrixUtils.subtractionVtv(x, p);
                double mul = MatrixUtils.productVtV(sub,n);
                if (mul <= 0) {
                    return leftNode.computePathLength(x, score);
                } else {
                    return rightNode.computePathLength(x, score);
                }
            }
        }
    }

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

    private class SubSampleTask extends MRTask<SubSampleTask> {

        private int subSampleSize;
        private Random random;
        
        private AtomicInteger currentSubSampleSize = new AtomicInteger(0);

        public SubSampleTask(int subSampleSize, long seed) {
            this.subSampleSize = subSampleSize;
            this.random = RandomUtils.getRNG(seed);
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                if (random.nextBoolean() && (currentSubSampleSize.get() <  subSampleSize)) {
                    currentSubSampleSize.incrementAndGet();
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }

    /**
     * Gives the average path length of unsuccessful search in BST
     * @param n number of elements
     */
    private double avgPathLengthUnsucSearch(long n) {
        if (n <= 0)
            return 0;
        return 2 * MathUtils.harmonicNumberEstimation(n - 1) - (2.0 * (n - 1.0))/n;
    }
    
    private double anomalyScore(double pathLength) {
        return Math.pow(2, -1 * pathLength/MathUtils.harmonicNumberEstimation(_train.numRows()));
    }
}
