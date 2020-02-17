package hex.tree.isoforextended;

import hex.ModelBuilder;
import hex.ModelCategory;
import hex.psvm.psvm.MatrixUtils;
import hex.tree.SharedTree;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
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
        }
    }
    
    private class SplitFrameMRTask extends MRTask<SplitFrameMRTask> {
        private Vec p;
        private Vec n;
        
        private Frame left;
        private Frame right;
        
        public SplitFrameMRTask(Vec p, Vec n) {
            this.p = p;
            this.n = n;
        }
        
        @Override
        public void map(Chunk[] cs) {
            for (int i = 0; i < cs.length; i++) {
                for (int j = 0; j < cs[i]._len; j++) {
                    System.out.println(cs[i].atd(j));
                }
            }
        }

        @Override
        public void reduce(SplitFrameMRTask mrt) {
            // nothing to do
        }
    }
    
}
