package hex.tree.xgboost;

import hex.tree.xgboost.util.PredictConfiguration;
import org.junit.Test;
import org.junit.Rule;

import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.H2ORunner;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
public class XGBoostScoringTest extends TestUtil {

    @Rule
    public final RestoreSystemProperties restoreSystemProperties = new RestoreSystemProperties();

    @Test
    public void testScoringOnWideDataset() { // compare results of xgboost native and Java predictor scoring
        Scope.enter();
        try {
            String response = "C785";
            Frame train = parseTestFile("bigdata/laptop/mnist/train.csv.gz")
                    .toCategoricalCol(response);
            Scope.track(train);
            Frame test = parseTestFile("bigdata/laptop/mnist/test.csv.gz")
                    .toCategoricalCol(response);
            Scope.track(test);
            
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._ntrees = 10;
            parms._max_depth = 3;
            parms._train = train._key;
            parms._response_column = response;
            parms._seed = 0xCAFE;
            parms._score_each_iteration = true;
            parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;

            System.setProperty(PredictConfiguration.PREDICT_JAVA_PROP, "false");
            System.setProperty(PredictConfiguration.PREDICT_NATIVE_PROP, "true");
            assertFalse(PredictConfiguration.useJavaScoring());
            
            XGBoostModel modelNative = new XGBoost((XGBoostModel.XGBoostParameters) parms.clone()).trainModel().get();
            assertNotNull(modelNative);
            Scope.track_generic(modelNative);

            Frame predsNative = modelNative.score(test);
            Scope.track(predsNative);

            System.setProperty(PredictConfiguration.PREDICT_JAVA_PROP, "true");
            System.setProperty(PredictConfiguration.PREDICT_NATIVE_PROP, "false");
            assertTrue(PredictConfiguration.useJavaScoring());
            
            XGBoostModel modelJava = new XGBoost((XGBoostModel.XGBoostParameters) parms.clone()).trainModel().get();
            assertNotNull(modelJava);
            Scope.track_generic(modelJava);

            Frame predsJava = modelJava.score(test);
            Scope.track(predsJava);

            // predictions should match
            assertFrameEquals(predsNative, predsJava, 1e-7);
            // scoring history should be the same
            assertArrayEquals(modelNative._output.scoreKeepers(), modelJava._output.scoreKeepers());
        } finally {
            Scope.exit();
        }
    }

}
