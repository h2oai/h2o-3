package hex.tree.isoforextended;

import hex.ModelCategory;
import hex.ScoreKeeper;
import hex.splitframe.ShuffleSplitFrame;
import hex.tree.SharedTree;
import water.Iced;
import water.Key;
import water.MRTask;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.MathUtils;

/**
 * Extended isolation forest implementation by https://arxiv.org/pdf/1811.02141.pdf paper.
 *
 * @author Adam Valenta
 */
public class ExtendedIsolationForest extends SharedTree<ExtendedIsolationForestModel,
        ExtendedIsolationForestParameters,
        ExtendedIsolationForestOutput> {

    public IsolationTree[] iTrees;
    public int k = 0;

    public ExtendedIsolationForest(ExtendedIsolationForestParameters parms) {
        super(parms);
        init(false);
        iTrees = new IsolationTree[_parms._ntrees];
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
                System.out.println();
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

        @Override
        protected boolean buildNextKTrees() {
            int heightLimit = (int) Math.ceil(MathUtils.log2(_parms.sampleSize));

            int randomUnit =  _rand.nextInt();
            
            Frame subSample = new SubSampleTask(_parms.sampleSize, _parms._seed + randomUnit)
                    .doAll(new byte[]{Vec.T_NUM, Vec.T_NUM} , _train.vecs(new int[]{0,1})).outputFrame();
//            System.out.println("subSample size: " + subSample.numRows());
            
            IsolationTree iTree = new IsolationTree(subSample, heightLimit, _parms._seed + randomUnit, _parms.extensionLevel);
            iTree.buildTree();
//            iTree.print();
//            iTree.printHeight();
            iTrees[k] = iTree;
            k++;
            return false;
        }

        @Override
        protected void initializeModelSpecifics() {

        }
    }

}
