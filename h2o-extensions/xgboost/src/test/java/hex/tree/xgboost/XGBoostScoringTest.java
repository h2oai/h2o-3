package hex.tree.xgboost;

import hex.tree.xgboost.util.PredictConfiguration;
import org.junit.Test;
import org.junit.Rule;

import org.junit.contrib.java.lang.system.RestoreSystemProperties;
import org.junit.runner.RunWith;
import water.MRTask;
import water.Scope;
import water.TestUtil;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.H2ORunner;
import water.util.Log;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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

    @Test
    public void testTrainingWithPredictionCacheIsFaster() {
        final long durationDefault;
        final long durationNoCache;
        {
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            durationDefault = buildMNISTModel(parms);
        }
        {
            XGBoostModel.XGBoostParameters parms = new XGBoostModel.XGBoostParameters();
            parms._cache_training_predictions = false;
            durationNoCache = buildMNISTModel(parms);
        }
        Log.info("Duration of scoring: " +
                "default (with cache) " + durationDefault + "ms, cache disabled: " + durationNoCache + "ms.");
        assertThat(durationNoCache, greaterThan(durationDefault * 2)); // default is at least 2x faster
    }

    private long buildMNISTModel(XGBoostModel.XGBoostParameters parms) {
        Scope.enter();
        try {
            String response = "C785";
            Frame train = convertDigitsToBinary(parseTestFile("bigdata/laptop/mnist/train.csv.gz"), response)
                    .toCategoricalCol(response);
            Scope.track(train);
            Frame test = convertDigitsToBinary(parseTestFile("bigdata/laptop/mnist/test.csv.gz"), response)
                    .toCategoricalCol(response);
            Scope.track(test);

            parms._ntrees = 100;
            parms._max_depth = 6;
            parms._train = train._key;
            parms._response_column = response;
            parms._seed = 0xCAFE;
            parms._score_each_iteration = true;
            parms._backend = XGBoostModel.XGBoostParameters.Backend.cpu;

            XGBoostModel model = new XGBoost((XGBoostModel.XGBoostParameters) parms.clone()).trainModel().get();
            assertNotNull(model);
            Scope.track_generic(model);

            return model._output._total_scoring_time_ms;
        } finally {
            Scope.exit();
        }
    }

    @SuppressWarnings("rawtypes")
    private Frame convertDigitsToBinary(Frame f, String digitsColumn) { // 0..4 -> 0; 5..9 -> 1
        Vec vec = f.vec(digitsColumn);
        new MRTask() {
            @Override
            public void map(Chunk c) {
                for (int i = 0; i < c._len; i++) {
                    c.set(i, c.atd(i) < 5 ? 0 : 1);
                }
            }
        }.doAll(vec);
        return f;
    }

}
