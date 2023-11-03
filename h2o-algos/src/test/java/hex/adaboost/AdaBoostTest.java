package hex.adaboost;

import hex.Model;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.tree.drf.DRFModel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.FrameUtils;

import java.io.File;
import java.util.Arrays;

import static org.junit.Assert.*;

@CloudSize(1)
@RunWith(H2ORunner.class)
public class AdaBoostTest extends TestUtil {

    @Rule
    public EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Before
    public void beforeClass() {
        final File h2oHomeDir = new File(System.getProperty("user.dir")).getParentFile();
        environmentVariables.set("H2O_FILES_SEARCH_PATH", h2oHomeDir.getAbsolutePath());
    }

    @Test
    public void testBasicTrain() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            String response = "CAPSULE";
            int nlearners = 50;
            train.toCategoricalCol(response);
            Scope.track(train);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = nlearners;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
            assertEquals("Model should contain all the weak learners", nlearners, adaBoostModel._output.models.length);

            for (int i = 0; i < adaBoostModel._output.models.length; i++) {
                System.out.println("Tree = " + i);
                DRFModel drfModel = DKV.getGet(adaBoostModel._output.models[i]);
                SharedTreeSubgraph tree = drfModel.getSharedTreeSubgraph(0, 0);
                if (tree.rootNode.getColName() == null) {
                    // FIXME - why are some of the trees empty? Are all of the columns bad for split?
                    System.out.println("    Empty tree");
                    continue;
                }
                System.out.println("    Root = " + tree.rootNode.getColName() + " " + tree.rootNode.getSplitValue());
                System.out.println("    Left = " + tree.rootNode.getLeftChild().isLeaf() + " " + tree.rootNode.getLeftChild().getPredValue());
                System.out.println("    Right = " + tree.rootNode.getRightChild().isLeaf() + " " + tree.rootNode.getRightChild().getPredValue());
                assertNotNull(tree.rootNode.getColName());
                assertTrue(tree.rootNode.getLeftChild().isLeaf());
                assertTrue(tree.rootNode.getRightChild().isLeaf());
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainGLM() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            Scope.track(train);
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._weak_learner = AdaBoostModel.Algorithm.GLM;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainDeepLearning() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            Scope.track(train);
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._nlearners = 20;
            p._train = train._key;
            p._seed = 0xDECAF;
            p._weak_learner = AdaBoostModel.Algorithm.DEEP_LEARNING;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainLarge() {
        try {
            Scope.enter();
            Frame train = parseTestFile("bigdata/laptop/creditcardfraud/creditcardfraud.csv");
            Scope.track(train);
            String response = "Class";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScore() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            Scope.track(train);
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            System.out.println("train.toTwoDimTable() = " + train.toTwoDimTable());

            Frame score = adaBoostModel.score(train);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreCategorical() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            Scope.track(train);
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            train.toCategoricalCol("RACE");
            train.toCategoricalCol("DPROS");
            train.toCategoricalCol("DCAPS");
            train.toCategoricalCol("GLEASON");
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._response_column = response;
            p._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            System.out.println("train.toTwoDimTable() = " + train.toTwoDimTable());

            Frame score = adaBoostModel.score(train);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreLarge() {
        try {
            Scope.enter();
            Frame train = parseTestFile("bigdata/laptop/creditcardfraud/creditcardfraud.csv");
            Scope.track(train);
            String response = "Class";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(train);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAirlines() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/testng/airlines_train_preprocessed.csv");
            Scope.track(train);
            Frame test = parseTestFile("smalldata/testng/airlines_test_preprocessed.csv");
            Scope.track(test);
            String response = "IsDepDelayed";
            train.toCategoricalCol(response);
            test.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(test);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainHiggs() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/higgs/higgs_train_5k.csv");
            Scope.track(train);
            Frame test = parseTestFile("smalldata/higgs/higgs_test_5k.csv");
            Scope.track(test);
            String response = "response";
            train.toCategoricalCol(response);
            test.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(test);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCountWe() {
        Scope.enter();
        try {
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ard(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
                    .withDataForCol(1, ar("0", "0", "0", "0", "0", "1", "1", "1", "1", "1"))
                    .withDataForCol(2, ar("1", "1", "1", "1", "1", "0", "0", "0", "0", "0"))
                    .build();
            train = ensureDistributed(train);
            Scope.track(train);

            CountWeTask countWeTask = new CountWeTask().doAll(train);
            assertEquals("Sum of weights is not correct", 10, countWeTask.W, 0);
            assertEquals("Sum of error weights is not correct", 10, countWeTask.We, 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testUpdateWeights() {
        Scope.enter();
        try {
            Frame train = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_CAT)
                    .withDataForCol(0, ard(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0))
                    .withDataForCol(1, ar("1", "0", "0", "0", "0", "1", "1", "1", "1", "1"))
                    .withDataForCol(2, ar("1", "1", "1", "1", "1", "0", "0", "0", "0", "0"))
                    .build();
            train = ensureDistributed(train);
            Scope.track(train);

            double alpha = 2;
            UpdateWeightsTask updateWeightsTask = new UpdateWeightsTask(alpha);
            updateWeightsTask.doAll(train);

            Vec weightsExpected = Vec.makeCon(Math.exp(alpha), train.numRows());
            weightsExpected.set(0, Math.exp(-alpha));
            System.out.println("weights = ");
            System.out.println(new Frame(train.vec(0)).toTwoDimTable(0, (int) train.numRows(), false));
            assertVecEquals("Weights are not correctly updated", weightsExpected, train.vec(0), 0);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreWithExternalWeightsColumn() {
        try {
            // Train reference model
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            Scope.track(train);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 10;
            p._response_column = response;

            AdaBoost adaBoostReference = new AdaBoost(p);
            AdaBoostModel adaBoostReferenceModel = adaBoostReference.trainModel().get();
            Scope.track_generic(adaBoostReferenceModel);
            assertNotNull(adaBoostReferenceModel);

            // Add weights column to frame and train different model
            Vec weights = train.anyVec().makeCons(1, 1, null, null)[0];
            train.add("weights", weights);
            DKV.put(train);
            Scope.track(train);
            p._weights_column = "weights";

            AdaBoost adaBoostWithExternalWeights = new AdaBoost(p);
            AdaBoostModel adaBoostModelWithExternalWeights = adaBoostWithExternalWeights.trainModel().get();
            Scope.track_generic(adaBoostModelWithExternalWeights);
            assertNotNull(adaBoostModelWithExternalWeights);

            // Check that output is identical
            Frame scoreReference = adaBoostReferenceModel.score(train);
            Scope.track(scoreReference);
            Frame scoreWithExternalWeights = adaBoostModelWithExternalWeights.score(train);
            Scope.track(scoreWithExternalWeights);
            assertFrameEquals(scoreReference, scoreWithExternalWeights, 0); // output should be identical
            assertFalse("Weights column should be change in the training", weights.isConst());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreWithCustomWeightsColumn() {
        try {
            // Train reference model
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            Scope.track(train);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 10;
            p._response_column = response;

            AdaBoost adaBoostReference = new AdaBoost(p);
            AdaBoostModel adaBoostReferenceModel = adaBoostReference.trainModel().get();
            Scope.track_generic(adaBoostReferenceModel);
            assertNotNull(adaBoostReferenceModel);

            // Set custom weights column
            p._weights_column = "RACE";
            double maxReference = train.vec("RACE").max(); // for future assert
            AdaBoost adaBoostWithExternalWeights = new AdaBoost(p);
            AdaBoostModel adaBoostModelWithExternalWeights = adaBoostWithExternalWeights.trainModel().get();
            Scope.track_generic(adaBoostModelWithExternalWeights);
            assertNotNull(adaBoostModelWithExternalWeights);

            // Check that output is identical
            Frame scoreReference = adaBoostReferenceModel.score(train);
            Scope.track(scoreReference);
            Frame scoreWithExternalWeights = adaBoostModelWithExternalWeights.score(train);
            Scope.track(scoreWithExternalWeights);
            // output should be different since the weights are not initialize on purpose
            assertFalse(Arrays.equals(FrameUtils.asDoubles(scoreReference.vec("predict")), FrameUtils.asDoubles(scoreWithExternalWeights.vec("predict"))));
            assertNotEquals("RACE column should be changed in the training", maxReference, train.vec("RACE").max());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreWithDuplicatedWeightsColumn() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            // Add weights column to frame
            Vec weights = train.anyVec().makeCons(1, 1, null, null)[0];
            train.add("weights", weights);
            DKV.put(train);
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            Scope.track(train);

            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 10;
            p._response_column = response;
            p._ignore_const_cols = false;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            // Check that output is identical
            Frame score = adaBoostModel.score(train);
            Scope.track(score);
            assertTrue("Weights column should not be changed in the training", weights.isConst());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreGLM() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._weak_learner = AdaBoostModel.Algorithm.GLM;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(train);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreGBM() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._weak_learner = AdaBoostModel.Algorithm.GBM;
            p._response_column = response;

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(train);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreDeepLearning() {
        try {
            Scope.enter();
            Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
            String response = "CAPSULE";
            train.toCategoricalCol(response);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = 50;
            p._weak_learner = AdaBoostModel.Algorithm.DEEP_LEARNING;
            p._response_column = response;
            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            Frame score = adaBoostModel.score(train);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testTrainWithCustomWeakLearners() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            String response = "CAPSULE";
            int nlearners = 50;
            train.toCategoricalCol(response);
            Scope.track(train);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = nlearners;
            p._response_column = response;
            p._weak_learner_params = "{'ntrees':3}";

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);

            assertEquals("Model should contain all the weak learners", nlearners, adaBoostModel._output.models.length);

            for (int i = 0; i < adaBoostModel._output.models.length; i++) {
                System.out.println("Tree = " + i);
                DRFModel drfModel = DKV.getGet(adaBoostModel._output.models[i]);
                assertEquals(3, drfModel._output._ntrees);
            }

            Frame score = adaBoostModel.score(train);
            Scope.track(score);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testBasicTrainAndScoreWeakLearnersEqualToDefault() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            String response = "CAPSULE";
            int nlearners = 50;
            train.toCategoricalCol(response);
            Scope.track(train);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = nlearners;
            p._response_column = response;

            AdaBoost adaBoostReference = new AdaBoost(p);
            AdaBoostModel adaBoostModelReference = adaBoostReference.trainModel().get();
            Scope.track_generic(adaBoostModelReference);
            assertNotNull(adaBoostModelReference);
            Frame scoreReference = adaBoostModelReference.score(train);
            Scope.track(scoreReference);

            p._weak_learner_params = "{'ntrees':1, 'mtries':1, 'min_rows':1, 'sample_rate':1, 'max_depth':1}";
            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
            assertEquals("Model should contain all the weak learners", nlearners, adaBoostModel._output.models.length);

            for (int i = 0; i < adaBoostModel._output.models.length; i++) {
                System.out.println("Tree = " + i);
                DRFModel drfModel = DKV.getGet(adaBoostModel._output.models[i]);
                SharedTreeSubgraph tree = drfModel.getSharedTreeSubgraph(0, 0);
                if (tree.rootNode.getColName() == null) {
                    // FIXME - why are some of the trees empty? Are all of the columns bad for split?
                    System.out.println("    Empty tree");
                    continue;
                }
                System.out.println("    Root = " + tree.rootNode.getColName() + " " + tree.rootNode.getSplitValue());
                System.out.println("    Left = " + tree.rootNode.getLeftChild().isLeaf() + " " + tree.rootNode.getLeftChild().getPredValue());
                System.out.println("    Right = " + tree.rootNode.getRightChild().isLeaf() + " " + tree.rootNode.getRightChild().getPredValue());
                assertNotNull(tree.rootNode.getColName());
                assertTrue(tree.rootNode.getLeftChild().isLeaf());
                assertTrue(tree.rootNode.getRightChild().isLeaf());
            }

            Frame score = adaBoostModel.score(train);
            Scope.track(score);

            assertFrameEquals(scoreReference, score, 0.0, 0.0);
        } finally {
            Scope.exit();
        }
    }

    @Test(expected = H2OModelBuilderIllegalArgumentException.class)
    public void testCustomWeakLearnerError() {
        try {
            Scope.enter();
            Frame train = parseTestFile("smalldata/prostate/prostate.csv");
            String response = "CAPSULE";
            int nlearners = 50;
            train.toCategoricalCol(response);
            Scope.track(train);
            AdaBoostModel.AdaBoostParameters p = new AdaBoostModel.AdaBoostParameters();
            p._train = train._key;
            p._seed = 0xDECAF;
            p._nlearners = nlearners;
            p._response_column = response;
            p._weak_learner_params = "{'ntrees':3, ";

            AdaBoost adaBoost = new AdaBoost(p);
            AdaBoostModel adaBoostModel = adaBoost.trainModel().get();
            Scope.track_generic(adaBoostModel);
            assertNotNull(adaBoostModel);
            assertEquals("Model should contain all the weak learners", nlearners, adaBoostModel._output.models.length);
        } finally {
            Scope.exit();
        }
    }
}
