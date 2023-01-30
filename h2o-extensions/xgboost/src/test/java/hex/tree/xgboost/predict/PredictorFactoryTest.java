package hex.tree.xgboost.predict;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.tree.RegTree;
import hex.ModelMetricsRegression;
import hex.SplitFrame;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.xgboost.XGBoostModel;
import hex.tree.xgboost.XGBoostTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.H2ORunner;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
public class PredictorFactoryTest extends TestUtil {

    @Test
    public void testMakeTreeSkippingPredictor() {
        Scope.enter();
        try {
            Frame tfr = parseTestFile("./smalldata/prostate/prostate.csv");
            Scope.track(tfr);

            Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));   // Convert CAPSULE to categorical
            Scope.track(tfr.replace(3, tfr.vecs()[3].toCategoricalVec()));   // Convert RACE to categorical
            DKV.put(tfr);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._train = tfr._key;
            parms._response_column = "AGE";
            parms._ignored_columns = new String[]{"ID"};
            parms._ntrees = 10;

            XGBoostModel model = new hex.tree.xgboost.XGBoost(parms).trainModel().get();
            Scope.track_generic(model);
            
            final int treesToSkip = 7;
            Predictor predictor = PredictorFactory.makePredictor(model.model_info()._boosterBytes, null, true, 
                    treesToSkip);
            GradBooster gb = predictor.getBooster();
            assertTrue(gb instanceof GBTree);

            GBTree gbTree = (GBTree) gb;
            assertEquals(1, gbTree.getGroupedTrees().length);
            for (int i = 0; i < gbTree.getGroupedTrees()[0].length; i++) {
                RegTree tree = gbTree.getGroupedTrees()[0][i];
                if (i < treesToSkip) {
                    assertTrue(tree instanceof SkippedRegTree);
                } else {
                    assertFalse(tree instanceof SkippedRegTree);
                }
            }
        } finally {
            Scope.exit();
        }
    }

}
