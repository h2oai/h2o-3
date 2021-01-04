package hex.tree.xgboost.exec;

import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static org.junit.Assert.assertTrue;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class RemoteXGBoostExecutorTest {

    @Before
    public void configureRemoteXGBoost() {
        System.setProperty("sys.ai.h2o.xgboost.external.address", H2O.getIpPortString());
    }

    @After
    public void revertRemoteXGBoost() {
        System.clearProperty("sys.ai.h2o.xgboost.external.address");
    }

    @Test
    public void prostateRegression() {
        Scope.enter();
        try {
            // Parse frame into H2O
            Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._train = tfr._key;
            parms._response_column = "AGE";
            parms._ignored_columns = new String[]{"ID"};
            parms._ntrees = 10;

            XGBoostModel model = new XGBoost(parms).trainModel().get();
            Scope.track_generic(model);

            Frame preds = Scope.track(model.score(tfr));
            assertTrue(model.testJavaScoring(tfr, preds, 1e-6));
            assertTrue("sigma " + preds.anyVec().sigma() + " should be > 0", preds.anyVec().sigma() > 0);
        } finally {
            Scope.exit();
        }
    }    

}
