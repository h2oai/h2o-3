package hex.tree.isofor;

import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.tools.PredictCsv;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.ParseSetup;
import water.test.util.ConfusionMatrixUtils;
import water.util.ArrayUtils;

import java.io.File;
import java.util.Random;

import static org.junit.Assert.*;

public class IsolationForestTest extends TestUtil {

  @Rule
  public transient TemporaryFolder tempFolder = new TemporaryFolder();

  @BeforeClass() public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testBasic() throws Exception {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._min_rows = 1;
      p._sample_size = 5;

      IsolationForest isofor = new IsolationForest(p);
      IsolationForestModel model = isofor.trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      // trained with no warnings
      assertEquals("", isofor.validationWarnings());

      Frame preds = Scope.track(model.score(train));
      assertArrayEquals(new String[]{"predict", "mean_length"}, preds.names());
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.testJavaScoring(train, preds, 1e-8));

      // check that CSV Predictor works 
      File predictorInCsv = new File(tempFolder.getRoot(), "predictor_in.csv");
      Frame.export(train, predictorInCsv.getAbsolutePath(), train._key.toString(), false, 1).get();
      File predictorOutCsv = tempFolder.newFile("predictor_out.csv");
      PredictCsv predictor = PredictCsv.make(
              new String[]{
                      "--embedded",
                      "--input", predictorInCsv.getAbsolutePath(),
                      "--output", predictorOutCsv.getAbsolutePath(),
                      "--decimal"}, model.toMojo());
      predictor.run();
      Frame predictorOutFrame = Scope.track(parseTestFile(predictorOutCsv.getAbsolutePath()));
      assertTrue(model.testJavaScoring(train, predictorOutFrame, 1e-8));

      assertTrue(model._output._min_path_length < Integer.MAX_VALUE);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testIFMaximumDepth() {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._min_rows = 1;
      p._max_depth = 0;
      p._sample_size = 5;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      Scope.track_generic(model);
      assertEquals(Integer.MAX_VALUE, model._parms._max_depth);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testEarlyStopping() {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 1000;
      p._min_rows = 1;
      p._sample_size = 5;
      p._stopping_rounds = 3;
      p._score_each_iteration = true;
      p._stopping_tolerance = 0.05;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      assertEquals(0, model._output._ntrees, 20); // stops in 20 trees or less
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testEmptyOOB() {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._sample_size = train.numRows(); // => no OOB observations

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame preds = Scope.track(model.score(train));
      assertArrayEquals(new String[]{"predict", "mean_length"}, preds.names());
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.testJavaScoring(train, preds, 1e-8));

      assertTrue(model._output._min_path_length < Integer.MAX_VALUE);
    } finally {
      Scope.exit();
    }
  }
  
  @Test // check that mtries can be set to full number of features (same as mtries = 2)
  public void testPubDev6483() {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/anomaly/ecg_discord_train.csv"));

      // should pass with all features
      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._mtries = train.numCols();

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);
      
      // should fail with #features + 1
      IsolationForestModel.IsolationForestParameters p_invalid = new IsolationForestModel.IsolationForestParameters();
      p_invalid._train = train._key;
      p_invalid._seed = 0xDECAF;
      p_invalid._ntrees = 7;
      p_invalid._mtries = train.numCols() + 1;
      try {
        Scope.track_generic(new IsolationForest(p_invalid).trainModel().get());
        fail();
      } catch (H2OIllegalArgumentException e) {
        assertTrue(e.getMessage().contains("ERRR on field: _mtries: Computed mtries should be -1 or -2 or in interval [1,210] but it is 211"));
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testVarSplits() {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/testng/prostate.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      IsolationForest.VarSplits splits = model._output._var_splits;
      SharedTreeSubgraph tree = model.getSharedTreeSubgraph(0, 0);

      int[] expSplitCounts = new int[model._output.nfeatures()];
      long[] expDepths = new long[model._output.nfeatures()];
      int nSplits = 0;
      for (SharedTreeNode node: tree.nodesArray) {
        if (node.isLeaf())
          continue;
        nSplits++;
        expSplitCounts[node.getColId()]++;
        expDepths[node.getColId()] += node.getDepth() + 1;
      }

      assertEquals(nSplits, ArrayUtils.sum(splits._splitCounts));
      assertArrayEquals(expSplitCounts, splits._splitCounts);
      assertArrayEquals(expDepths, splits._splitDepths);

      for (int i = 0; i < model._output.nfeatures(); i++) {
        assertEquals((long) expSplitCounts[i], model._output._variable_splits.get(i, 0));
        assertEquals(expDepths[i], model._output._variable_splits.get(i, 2));
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testContamination() {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/anomaly/ecg_discord_train.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xDECAF;
      p._ntrees = 7;
      p._min_rows = 1;
      p._sample_size = 5;
      p._contamination = 0.1;

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      assertNotNull(model);
      Scope.track_generic(model);

      Frame preds = Scope.track(model.score(train));
      assertArrayEquals(new String[]{"predict", "score", "mean_length"}, preds.names());
      assertEquals(train.numRows(), preds.numRows());

      assertTrue(model.outputAnomalyFlag());
      assertEquals(0.73, model._output._defaultThreshold, 1e-6);
      assertArrayEquals(new long[]{18L, 2L}, preds.vec("predict").bins());
      
      assertTrue(model.testJavaScoring(train, preds, 1e-8));

      assertTrue(model._output._min_path_length < Integer.MAX_VALUE);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTrainingWithResponse()  {
    try {
      Scope.enter();
      Frame train = Scope.track(parseTestFile("smalldata/testng/airlines.csv"));

      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._seed = 0xFEED;
      p._ntrees = 1;
      p._max_depth = 3;
      // this is a weird case and it shouldn't be allowed but the java API permits it, we might disable it in the future
      p._response_column = "IsDepDelayed";
      p._ignored_columns = new String[] {"Origin", "Dest", "IsDepDelayed"};

      IsolationForestModel model = new IsolationForest(p).trainModel().get();
      Scope.track_generic(model);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testEarlyStoppingOnValidationSet() {
    try {
      Scope.enter();
      Random r = new Random(42);
      final int N = 10000;
      double[] x1s = new double[N];
      double[] x2s = new double[N];
      String[] ys = new String[N];
      for (int i = 0; i < N; i++) {
        x1s[i] = r.nextGaussian();
        x2s[i] = r.nextGaussian();
        ys[i] = Math.sqrt(Math.pow(x1s[i], 2) + Math.pow(x2s[i], 2)) > Math.PI ? "1" : "0";
      }
      final Frame train = new TestFrameBuilder()
              .withColNames("x1", "x2")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, x1s)
              .withDataForCol(1, x2s)
              .build();
      final Frame valid = new TestFrameBuilder()
              .withColNames("x1", "x2", "y")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, x1s)
              .withDataForCol(1, x2s)
              .withDataForCol(2, ys)
              .build();
      
      IsolationForestModel.IsolationForestParameters p = new IsolationForestModel.IsolationForestParameters();
      p._train = train._key;
      p._valid = valid._key;
      p._seed = 0xFEED;
      p._response_column = "y";
      p._stopping_metric = ScoreKeeper.StoppingMetric.mean_per_class_error;
      p._score_tree_interval = 3;
      p._stopping_rounds = 5;
      p._ntrees = 500;

      IsolationForestModel m = new IsolationForest(p).trainModel().get();
      assertNotNull(m);
      Scope.track_generic(m);

      // check we stopped early
      assertTrue(m._output._ntrees < 500);

      // check ability to predict on train (without a response)
      Frame predicted = Scope.track(m.score(train));

      ConfusionMatrix cm = ConfusionMatrixUtils.buildCM(valid.vec("y"), predicted.vec("predict"));
      assertTrue(m._output._validation_metrics instanceof ModelMetricsBinomial);
      assertEquals(((ModelMetricsBinomial) m._output._validation_metrics).mean_per_class_error(), cm.mean_per_class_error(), 1e-6);

      // check that we use correct values for score-keeping (early stopping) 
      assertEquals(
              m._output.scoreKeepers()[m._output.scoreKeepers().length - 1]._mean_per_class_error,
              cm.mean_per_class_error(), 1e-6
      );
    } finally {
      Scope.exit();
    }
  }

    @Test public void testMOJOandPOJOSupportedCategoricalEncodings() throws Exception {
        try {
            Scope.enter();
            final String response = "CAPSULE";
            final String testFile = "./smalldata/logreg/prostate.csv";
            Frame fr = parseTestFile(testFile)
                    .toCategoricalCol("RACE")
                    .toCategoricalCol("GLEASON")
                    .toCategoricalCol(response);
            fr.remove("ID").remove();
            fr.vec("RACE").setDomain(ArrayUtils.append(fr.vec("RACE").domain(), "3"));
            Scope.track(fr);
            DKV.put(fr);

            Model.Parameters.CategoricalEncodingScheme[] supportedSchemes = {
                    Model.Parameters.CategoricalEncodingScheme.AUTO,
                    // TODO EnumLimited, Binary, LabelEncoder, Eigen in PUBDEV-7612
            };

            for (Model.Parameters.CategoricalEncodingScheme scheme : supportedSchemes) {

                IsolationForestModel.IsolationForestParameters parms = new IsolationForestModel.IsolationForestParameters();
                parms._train = fr._key;
                parms._response_column = response;
                parms._categorical_encoding = scheme;

                IsolationForest job = new IsolationForest(parms);
                IsolationForestModel dl = job.trainModel().get();
                Scope.track_generic(dl);

                // Done building model; produce a score column with predictions
                Frame scored = Scope.track(dl.score(fr));

                // Build a POJO & MOJO, validate same results
                Assert.assertTrue(dl.testJavaScoring(fr, scored, 1e-15));

                File mojoScoringOutput = tempFolder.newFile(dl._key + "_scored2.csv");
                MojoModel mojoModel = dl.toMojo();

                PredictCsv predictor = PredictCsv.make(
                        new String[]{
                                "--embedded",
                                "--input", TestUtil.makeNfsFileVec(testFile).getPath(),
                                "--output", mojoScoringOutput.getAbsolutePath(),
                                "--decimal"}, (GenModel) mojoModel);
                predictor.run();
                Frame scoredWithMojo = Scope.track(parseTestFile(mojoScoringOutput.getAbsolutePath(), new ParseSetupTransformer() {
                    @Override
                    public ParseSetup transformSetup(ParseSetup guessedSetup) {
                        return guessedSetup.setCheckHeader(1);
                    }
                }));

                scoredWithMojo.setNames(scored.names());
                assertFrameEquals(scored, scoredWithMojo, 1e-8);
            }
        } finally {
            Scope.exit();
        }
    }
}
