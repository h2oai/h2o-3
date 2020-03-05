package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.SplitFrame;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.SharedTree;
import water.DKV;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.util.FrameUtils;
import water.util.VecUtils;

import java.util.Arrays;

/**
 * Extended isolation forest implementation by https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends SharedTree<ExtendedIsolationForestModel,
        ExtendedIsolationForestParameters,
        ExtendedIsolationForestOutput> {


    public static final double SUB_SAMPLING_SIZE = 256.0;

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
            
            // create subsamble viz. EIF/IF paper algorithm 1
            double ratio = SUB_SAMPLING_SIZE /_train.numRows();
            SplitFrame sf = new SplitFrame(_train, new double[]{ratio, 1 - ratio}, new Key[] {Key.make("train.hex"), Key.make("test.hex")});
            sf.exec().get();
            Key[] ksplits = sf._destination_frames;
            Frame subSample = DKV.get(ksplits[0]).get();
            
            int heightLimit = (int) Math.ceil(customLog(2, SUB_SAMPLING_SIZE));
            ITree iTree = new ITree(subSample, 0, heightLimit);
        }
    }

    private static double customLog(double base, double logNumber) {
        return Math.log(logNumber) / Math.log(base);
    }
    
    private class ITree {
        private Frame frame;
        private Vec n;
        private Vec p;
        private ITree leftNode;
        private ITree rightNode;

        private int currentHeight;
        private boolean external = false;
        private long size;

        public ITree(Frame frame, int currentHeight, int heightLimit) {
            this.frame = frame;
            this.currentHeight = currentHeight;
            
            if (currentHeight >= heightLimit || frame.numRows() <= 1) {
                external = true;
                size = frame.numRows();
            } else {
                p = VecUtils.uniformDistrFromFrameMR(frame, _parms._seed);
                n = VecUtils.makeGaussianVec(frame.numCols(), frame.numCols() - _parms.extensionLevel - 1, _parms._seed);
                Frame sub = MatrixUtils.subtractionMtv(frame, p);
                Vec mul = MatrixUtils.productMtv2(sub, n);
                Frame left = new FilterLteTask(mul, 0).doAll(frame.types(), frame).outputFrame();
                Frame right = new FilterGtRightTask(mul, 0).doAll(frame.types(), frame).outputFrame();

                leftNode = new ITree(left, currentHeight + 1, heightLimit);
                rightNode = new ITree(right, currentHeight + 1, heightLimit);
            }
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
        
        private void growTree() {
            
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
                    // left
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
                    // right
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }

}
