package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.SharedTree;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
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

    private class ExtendedIsolationForestDriver extends ModelBuilder<ExtendedIsolationForestModel,
            ExtendedIsolationForestParameters,
            ExtendedIsolationForestOutput>.Driver {

        @Override
        public void computeImpl() {
            Vec p = VecUtils.uniformDistrFromFrameMR(_train, _parms._seed);
            Vec n = VecUtils.makeGaussianVec(_train.numCols(), _train.numCols() - _parms._extension_level - 1, _parms._seed);
            System.out.println(Arrays.toString(FrameUtils.asDoubles(n)));
            System.out.println(n.length());
            Frame res = MatrixUtils.subtractionMtv(_train, p);
            Vec mul = MatrixUtils.workingProductMtv(res, n);
            System.out.println(Arrays.toString(FrameUtils.asDoubles(mul)));
            SplitFrameMRTask task = new SplitFrameMRTask(mul).doAll(_train);
            System.out.println("task.left.length = " + task.left.length);
            for (double [] row : task.left) {
                System.out.println(Arrays.toString(row));
            }
            System.out.println("task.right.length = " + task.right.length);
            for (double [] row : task.right) {
                System.out.println(Arrays.toString(row));
            }
        }
    }

    private class SplitFrameMRTask extends MRTask<SplitFrameMRTask> {
        private Vec mul;

        private double [][] left;
        private double [][] right;

        public SplitFrameMRTask(Vec mul) {
            super();
            this.mul = mul;
        }

        @Override
        public void map(Chunk[] cs) {
            for (int row = 0; row < cs[0]._len; row++) {
                double num = mul.at(cs[0].start() + row);
                if (num < 0) {
                    // left
                    double [] rowValues = new double[cs.length];
                    for (int column = 0; column < cs.length; column++) {
                        rowValues[column] = cs[column].atd(row);
                    }
                    left = ArrayUtils.append(left, rowValues);
                } else {
                    // right
                    double [] rowValues = new double[cs.length];
                    for (int column = 0; column < cs.length; column++) {
                        rowValues[column] = cs[column].atd(row);
                    }
                    right = ArrayUtils.append(right, rowValues);
                }
            }
        }
    }

}
