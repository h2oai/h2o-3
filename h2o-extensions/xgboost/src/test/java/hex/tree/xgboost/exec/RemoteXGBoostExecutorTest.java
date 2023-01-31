package hex.tree.xgboost.exec;

import hex.ScoreKeeper;
import hex.SplitFrame;
import hex.tree.xgboost.XGBoost;
import hex.tree.xgboost.XGBoostModel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;
import water.util.TwoDimTable;

import static org.junit.Assert.*;
import static water.TestUtil.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class RemoteXGBoostExecutorTest {

    @Before
    public void configureRemoteXGBoost() {
        System.setProperty("sys.ai.h2o.xgboost.external.address", H2O.CLOUD.leader().getIpPortString());
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

            // We want to hit all nodes to confirm distributed XGBoost works
            tfr = ensureDistributed(tfr);

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

    @Test
    public void prostateRegressionWithCustomEarlyStopping() {
        Scope.enter();
        try {
            int ntrees=1000;
            // Parse frame into H2O
            Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            // We want to hit all nodes to confirm distributed XGBoost works
            tfr = ensureDistributed(tfr);

            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._train = tfr._key;
            parms._response_column = "AGE";
            parms._ignored_columns = new String[]{"ID"};
            parms._ntrees = ntrees;
            parms._eval_metric = "rmse";
            parms._stopping_tolerance = 1e-1;
            parms._score_tree_interval = 3;
            parms._stopping_rounds = 2;
            parms._stopping_metric = ScoreKeeper.StoppingMetric.custom;

            XGBoostModel model = new XGBoost(parms).trainModel().get();
            Scope.track_generic(model);

            assertEquals(ntrees, model._input_parms._ntrees);
            assertTrue(model._output._ntrees < model._input_parms._ntrees);
            for (ScoreKeeper sk : model._output.scoreKeepers()) {
                assertEquals(sk._rmse, sk._custom_metric, 1e-5);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void prostateRegressionWithValidationFrameAndEvalMetric() {
        Scope.enter();
        try {
            // Parse frame into H2O
            Frame fr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));

            // We want to hit all nodes to confirm distributed XGBoost works
            fr = ensureDistributed(fr);

            Frame[] splits = SplitFrame.splitFrame(fr, 0.8, 0.2);
            Scope.track(splits);
            
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._train = splits[0]._key;
            parms._valid = splits[1]._key;
            parms._response_column = "AGE";
            parms._ignored_columns = new String[]{"ID"};
            parms._ntrees = 30;
            parms._eval_metric = "rmse";
            parms._score_tree_interval = 3;
            parms._score_eval_metric_only = true;

            XGBoostModel model = new XGBoost(parms).trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            TwoDimTable scoringHistory = model._output._scoring_history;
            ScoreKeeper[] scoreKeepers = model._output.scoreKeepers();
            int customIdx = ArrayUtils.find(scoringHistory.getColHeaders(), "Validation Custom");
            assertTrue(customIdx >= 0);
            assertEquals(10 + 1, scoringHistory.getRowDim());
            assertEquals(scoringHistory.getRowDim(), scoreKeepers.length);

            for (int i = 0; i < scoringHistory.getRowDim(); i++) {
                assertEquals(scoreKeepers[i]._custom_metric, (double) scoringHistory.get(i, customIdx), 1e-5);
            }
        } finally {
            Scope.exit();
        }
    }

}
