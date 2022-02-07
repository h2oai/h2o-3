package hex.tree.gbm;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.SharedTreeModel;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class HiggsTest extends TestUtil {

    @Test
    public void testHiggs() {
        GBMModel gbm = null;
        Frame fr = null, fr2= null, vfr=null;
        try {
            Scope.enter();
            fr = parseTestFile("/Users/mkurka/git/h2o/h2o-3/private/datasets/springleaf_train_4.csv");
            int idx = fr.numCols() - 1;
            if (!fr.vecs()[0].isCategorical()) {
                    Scope.track(fr.replace(idx, fr.vecs()[idx].toCategoricalVec()));
                }
            DKV.put(fr);             // Update frame after hacking it

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._response_column = fr._names[idx];
            parms._ntrees = 50;
            parms._distribution = DistributionFamily.bernoulli;
            parms._max_depth = 5;
            parms._learn_rate = .1;
            parms._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();
        } finally {
            if( fr  != null ) fr .remove();
            if( fr2 != null ) fr2.remove();
            if( vfr != null ) vfr.remove();
            if( gbm != null ) gbm.delete();
            Scope.exit();
        }
    }

}
