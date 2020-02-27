package hex.tree.isoforextended;

import hex.FrameSplitter;
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
import water.util.ArrayUtils;
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
            System.out.println(this);
            for (int i = 0; i < cs.length; i++) {
                for (int j = 0; j < cs[i]._len; j++) {
                    System.out.println(cs[i].atd(j));
                }
            }
        }

        @Override
        public void reduce(PrintRowsMRTask mrt) {
            // nothing to do
        }
    }

    private class ExtendedIsolationForestDriver extends ModelBuilder<ExtendedIsolationForestModel,
            ExtendedIsolationForestParameters,
            ExtendedIsolationForestOutput>.Driver {

        @Override
        public void computeImpl() {
            
            // create subsamble viz. EIF/IF paper algorithm 1
            double ratio = 256.0/_train.numRows();
            SplitFrame sf = new SplitFrame(_train, new double[]{ratio, 1 - ratio}, new Key[] {Key.make("train.hex"), Key.make("test.hex")});
            sf.exec().get();
            Key[] ksplits = sf._destination_frames;
            Frame tr = DKV.get(ksplits[0]).get();

            System.out.println("a");
            
            // create first split
            Vec p = VecUtils.uniformDistrFromFrameMR(tr, _parms._seed);
            Vec n = VecUtils.makeGaussianVec(tr.numCols(), tr.numCols() - _parms.extensionLevel - 1, _parms._seed);
            System.out.println(Arrays.toString(FrameUtils.asDoubles(n)));
            System.out.println(n.length());
            Frame res = MatrixUtils.subtractionMtv(tr, p);
            Vec mul = MatrixUtils.productMtv2(res, n);
            System.out.println(Arrays.toString(FrameUtils.asDoubles(mul)));
            Frame left = new FilterFrameLeftTask(mul).doAll(tr.types(), tr).outputFrame();
            Frame right = new FilterFrameRightTask(mul).doAll(tr.types(), tr).outputFrame();
            System.out.println("a");
        }
    }

    private class FilterFrameLeftTask extends MRTask<FilterFrameLeftTask> {
        private Vec mul;

        public FilterFrameLeftTask(Vec mul) {
            super();
            this.mul = mul;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                double num = mul.at(cs[0].start() + row);
                if (num <= 0) {
                    // left
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }

    private class FilterFrameRightTask extends MRTask<FilterFrameRightTask> {
        private Vec mul;

        public FilterFrameRightTask(Vec mul) {
            super();
            this.mul = mul;
        }

        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
            for (int row = 0; row < cs[0]._len; row++) {
                double num = mul.at(cs[0].start() + row);
                if (num > 0) {
                    // right
                    for (int column = 0; column < cs.length; column++) {
                        ncs[column].addNum(cs[column].atd(row));
                    }
                }
            }
        }
    }

}
