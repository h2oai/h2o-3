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
public class SpringLeafTest extends TestUtil {

    @Test
    public void testSpringLeaf() {
        GBMModel gbm = null;
        Frame fr = null, fr2= null, vfr=null;
        try {
            Scope.enter();
            fr = parseTestFile("/Users/mkurka/git/h2o/h2o-3/private/datasets/springleaf_train_4.csv");
            fr2 = parseTestFile("/Users/mkurka/git/h2o/h2o-3/private/datasets/springleaf_test_4.csv");
            int idx = fr.numCols() - 1;
            if (!fr.vecs()[0].isCategorical()) {
                Scope.track(fr.replace(idx, fr.vecs()[idx].toCategoricalVec()));
                Scope.track(fr2.replace(idx, fr2.vecs()[idx].toCategoricalVec()));
            }
            DKV.put(fr);
            DKV.put(fr2);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._valid = fr2._key;
            parms._response_column = fr._names[idx];
            parms._ntrees = 50;
            parms._distribution = DistributionFamily.bernoulli;
            if (false) {
                parms._max_depth = 8;
                parms._learn_rate = .02;
                //parms._score_tree_interval = parms._ntrees + 1;
                parms._score_tree_interval = 20;
                parms._sample_rate = 0.8;
                parms._col_sample_rate = 0.8;
                /**
                 *  2022-02-11 10:01:18  4 min 30.040 sec              50       0.37531          0.44598      0.83421         0.68522       4.25807                       0.18234         0.39343            0.47915        0.76324           0.49873         3.25223                         0.28260
                 */
            } else {
                parms._max_depth = 5;
                parms._learn_rate = .1;
                parms._score_tree_interval = 20;
            }
            parms._seed = 42;
            //parms._score_each_iteration = true;
            parms._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.UniformRobust;

            /**
UniformAdaptive (random seed, not 42)
2022-02-09 17:31:59         0.103 sec               0       0.42245          0.54233      0.50000         0.23254       1.00000                       0.76746         0.42246            0.54235        0.50000           0.23255         1.00000                         0.76745
2022-02-09 17:41:03  9 min  4.310 sec             200       0.33176          0.36579      0.88851         0.78744       4.30027                       0.13971         0.38297            0.45549        0.77157           0.52026         3.60810                         0.27127

UniformRobust (seed 42)
2022-02-09 17:58:22 14 min 52.542 sec             180       0.33050          0.36405      0.89030         0.79415       4.30027                       0.13143         0.38170            0.45262        0.77669           0.52556         3.53890                         0.26979
2022-02-09 17:59:54 16 min 24.950 sec             200       0.32613          0.35631      0.89698         0.80565       4.30027                       0.13153         0.38118            0.45143        0.77767           0.52734         3.52901                         0.26903

QuantilesGlobal (seed 42)
2022-02-09 19:14:33 27 min 34.132 sec             180       0.32944          0.36210      0.89263         0.79802       4.30027                       0.12969         0.38146            0.45222        0.77697           0.52622         3.55867                         0.26689
2022-02-09 19:17:09 30 min 10.239 sec             200       0.32483          0.35393      0.89975         0.80994       4.30027                       0.12763         0.38098            0.45108        0.77786           0.52792         3.59821                         0.26569
             */
            
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
