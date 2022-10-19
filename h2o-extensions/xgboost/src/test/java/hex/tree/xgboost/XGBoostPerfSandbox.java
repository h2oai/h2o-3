package hex.tree.xgboost;

import hex.SplitFrame;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.xgboost.task.XGBoostUpdater;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class XGBoostPerfSandbox extends TestUtil {

    @Parameterized.Parameter
    public boolean uglyTrick;
    
    @Parameterized.Parameters
    public static Collection params() {
        return Arrays.asList(new Object[][] {
                { false },
                { true },
                { false },
                { true },
        });
    }
    @BeforeClass
    public static void setup() {
        stall_till_cloudsize(1);
    }

    @Test
    public void testImportancesAreSame() {
        Scope.enter();
        try {
            XGBoostUpdater.UGLY_TRICK = uglyTrick;
            XGBoostUpdater.UGLY_TRICK_OVERHEAD = 0;
            Frame tfr = Scope.track(parseTestFile("/home/kurkami/higgs_head_2M.csv"));
            //Frame tfr = Scope.track(parseTestFile("../data/higgs.csv.gz"));
            Scope.track(tfr.replace(0, tfr.vecs()[0].toCategoricalVec()));
            DKV.put(tfr);
            
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;
            parms._booster = XGBoostModel.XGBoostParameters.Booster.gbtree;
            parms._distribution = DistributionFamily.bernoulli;
            parms._max_depth = 10;
            parms._train = tfr._key;
            parms._response_column = "C1";
            parms._learn_rate = 0.1;
            parms._quiet_mode = true;
            parms._gpu_id = new int[] {0};
            parms._tree_method = XGBoostModel.XGBoostParameters.TreeMethod.hist;
            parms._ntrees = 20;
            parms._score_tree_interval = parms._ntrees + 1;
            
            long start_java = System.currentTimeMillis();
            XGBoostModel javaModel = new XGBoost(parms).trainModel().get();
            long end_java = System.currentTimeMillis();
            Scope.track_generic(javaModel);

            System.out.println("Training took (ms): " + (end_java-start_java + XGBoostUpdater.UGLY_TRICK_OVERHEAD));

        } finally {
            Scope.exit();
        }
    }
}
