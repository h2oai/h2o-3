package hex.tree.gbm;

import com.google.common.io.Files;
import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.gbm.GbmMojoModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.exception.PredictException;
import hex.genmodel.easy.prediction.BinomialModelPrediction;
import hex.genmodel.easy.prediction.MultinomialModelPrediction;
import hex.genmodel.tools.PredictCsv;
import hex.genmodel.tools.PrintMojo;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.*;
import org.hamcrest.number.OrderingComparison;
import org.junit.*;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.*;
import water.api.StreamingSchema;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.parser.BufferedString;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.util.*;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static hex.genmodel.utils.DistributionFamily.*;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;
import static water.fvec.FVecFactory.makeByteVec;

@RunWith(Parameterized.class)
public class GBMTest extends TestUtil {

  @Rule
  public transient ExpectedException expectedException = ExpectedException.none();

  @Rule
  public transient TemporaryFolder temporaryFolder = new TemporaryFolder();
  
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Parameterized.Parameters(name = "{index}: gbm({0})")
  public static Iterable<?> data() {
    if (MINCLOUDSIZE > 1) {
      return Collections.singletonList("Default");
    } else {
      // only run scenario "EmulateConstraints" for cloud size 1 (to avoid too long test execution)
      return Arrays.asList("Default", "EmulateConstraints");
    }
  }
  
  @Parameterized.Parameter
  public String test_type;
  
  // Note: use this method to create an instance of GBMParameters (needed for Parameterized test)
  private GBMModel.GBMParameters makeGBMParameters() {
    if ("EmulateConstraints".equals(test_type)) {
      return new GBMModel.GBMParameters() {
        @Override
        Constraints emptyConstraints(int numCols) {
          if (_distribution == DistributionFamily.gaussian || _distribution == DistributionFamily.bernoulli || _distribution == DistributionFamily.tweedie) {
            return new Constraints(new int[numCols], DistributionFactory.getDistribution(this), true);
          } else 
            return null;
        }
      };
    } else 
      return new GBMModel.GBMParameters();
  }

  private abstract class PrepData { abstract int prep(Frame fr); }

  static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn", "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsDepDelayed"};

  @Test public void testGBMRegressionGaussian() {
    GBMModel gbm = null;
    Frame fr = null, fr2 = null;
    try {
      fr = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = fr._key;
      parms._distribution = gaussian;
      parms._response_column = fr._names[1]; // Row in col 0, dependent in col 1, predictor in col 2
      parms._ntrees = 1;
      parms._max_depth = 1;
      parms._min_rows = 1;
      parms._nbins = 20;
      // Drop ColV2 0 (row), keep 1 (response), keep col 2 (only predictor), drop remaining cols
      String[] xcols = parms._ignored_columns = new String[fr.numCols()-2];
      xcols[0] = fr._names[0];
      System.arraycopy(fr._names,3,xcols,1,fr.numCols()-3);
      parms._learn_rate = 1.0f;
      parms._score_each_iteration=true;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();
      Assert.assertTrue(job.isStopped()); //HEX-1817

      // Done building model; produce a score column with predictions
      fr2 = gbm.score(fr);
      //job.response() can be used in place of fr.vecs()[1] but it has been rebalanced
      double sq_err = new MathUtils.SquareError().doAll(fr.vecs()[1],fr2.vecs()[0])._sum;
      double mse = sq_err/fr2.numRows();
      assertEquals(79152.12337641386,mse,0.1);
      assertEquals(79152.12337641386,gbm._output._scored_train[1]._mse,0.1);
      assertEquals(79152.12337641386,gbm._output._scored_train[1]._mean_residual_deviance,0.1);
    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( gbm != null ) gbm.remove();
    }
  }

  @Test public void testGBMMaximumDepth() {
    GBMModel gbm = null;
    Frame fr = null, fr2 = null;
    try {
      fr = parseTestFile("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = fr._key;
      parms._distribution = gaussian;
      parms._response_column = fr._names[1]; // Row in col 0, dependent in col 1, predictor in col 2
      parms._ntrees = 1;
      parms._max_depth = 0;
      parms._min_rows = 1;
      parms._nbins = 20;
      // Drop ColV2 0 (row), keep 1 (response), keep col 2 (only predictor), drop remaining cols
      String[] xcols = parms._ignored_columns = new String[fr.numCols()-2];
      xcols[0] = fr._names[0];
      System.arraycopy(fr._names,3,xcols,1,fr.numCols()-3);
      parms._learn_rate = 1.0f;
      parms._score_each_iteration=true;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();
      
      assertEquals(Integer.MAX_VALUE, gbm._parms._max_depth);
    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( gbm != null ) gbm.remove();
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
              Model.Parameters.CategoricalEncodingScheme.OneHotExplicit,
              Model.Parameters.CategoricalEncodingScheme.SortByResponse,
              Model.Parameters.CategoricalEncodingScheme.EnumLimited,
              Model.Parameters.CategoricalEncodingScheme.Enum,
              Model.Parameters.CategoricalEncodingScheme.Binary,
              Model.Parameters.CategoricalEncodingScheme.LabelEncoder,
              Model.Parameters.CategoricalEncodingScheme.Eigen
      };
      
      for (Model.Parameters.CategoricalEncodingScheme scheme : supportedSchemes) {

        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = fr._key;
        parms._response_column = response;
        parms._ntrees = 5;
        parms._categorical_encoding = scheme;
        if (scheme == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
          parms._max_categorical_levels = 3;
        }

        GBM job = new GBM(parms);
        GBMModel gbm = job.trainModel().get();
        Scope.track_generic(gbm);

        // Done building model; produce a score column with predictions
        Frame scored = Scope.track(gbm.score(fr));

        // Build a POJO & MOJO, validate same results
        Assert.assertTrue(gbm.testJavaScoring(fr, scored, 1e-15));

        File pojoScoringOutput = temporaryFolder.newFile(gbm._key + "_scored.csv");

        String modelName = JCodeGen.toJavaId(gbm._key.toString());
        String pojoSource = gbm.toJava(false, true);
        Class pojoClass = JCodeGen.compile(modelName, pojoSource);

        PredictCsv predictor = PredictCsv.make(
                new String[]{
                        "--embedded",
                        "--input", TestUtil.makeNfsFileVec(testFile).getPath(),
                        "--output", pojoScoringOutput.getAbsolutePath(),
                        "--decimal"}, (GenModel) pojoClass.newInstance());
        predictor.run();
        Frame scoredWithPojo = Scope.track(parseTestFile(pojoScoringOutput.getAbsolutePath(), new ParseSetupTransformer() {
          @Override
          public ParseSetup transformSetup(ParseSetup guessedSetup) {
            return guessedSetup.setCheckHeader(1);
          }
        }));

        scoredWithPojo.setNames(scored.names());
        assertFrameEquals(scored, scoredWithPojo, 1e-8);

        File mojoScoringOutput = temporaryFolder.newFile(gbm._key + "_scored2.csv");
        MojoModel mojoModel = gbm.toMojo();
        
        predictor = PredictCsv.make(
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

  @Test public void testBasicGBM() {
    // Regression tests
    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, gaussian);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, DistributionFamily.poisson);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, DistributionFamily.gamma);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, DistributionFamily.tweedie);

    // Classification tests
    basicGBM("./smalldata/junit/test_tree.csv",
            new PrepData() { int prep(Frame fr) { return 1; }
            },
            false, DistributionFamily.multinomial);

    basicGBM("./smalldata/junit/test_tree_minmax.csv",
            new PrepData() { int prep(Frame fr) { return fr.find("response"); }
            },
            false, DistributionFamily.bernoulli);

    basicGBM("./smalldata/logreg/prostate.csv",
            new PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
            },
            false, DistributionFamily.bernoulli);

    basicGBM("./smalldata/logreg/prostate.csv",
            new PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
            },
            false, DistributionFamily.multinomial);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr) { fr.remove("name").remove(); return fr.find("cylinders"); }
            },
            false, DistributionFamily.multinomial);

    basicGBM("./smalldata/gbm_test/alphabet_cattest.csv",
            new PrepData() { int prep(Frame fr) { return fr.find("y"); }
            },
            false, DistributionFamily.bernoulli);

//    basicGBM("./smalldata/gbm_test/alphabet_cattest.csv",
//            new PrepData() { int prep(Frame fr) { return fr.find("y"); }
//            },
//            false, DistributionFamily.modified_huber);

    basicGBM("./smalldata/airlines/allyears2k_headers.zip",
            new PrepData() { int prep(Frame fr) {
              for( String s : ignored_aircols ) fr.remove(s).remove();
              return fr.find("IsArrDelayed"); }
            },
            false, DistributionFamily.bernoulli);
//    // Bigger Tests
//    basicGBM("../datasets/98LRN.CSV",
//             new PrepData() { int prep(Frame fr ) {
//               fr.remove("CONTROLN").remove();
//               fr.remove("TARGET_D").remove();
//               return fr.find("TARGET_B"); }});

//    basicGBM("../datasets/UCI/UCI-large/covtype/covtype.data",
//             new PrepData() { int prep(Frame fr) { return fr.numCols()-1; } });
  }

  @Test public void testBasicGBMFamily() {
    Scope.enter();
    // Classification with Bernoulli family
    basicGBM("./smalldata/logreg/prostate.csv",
            new PrepData() {
              int prep(Frame fr) {
                fr.remove("ID").remove(); // Remove not-predictive ID
                int ci = fr.find("RACE"); // Change RACE to categorical
                Scope.track(fr.replace(ci,fr.vecs()[ci].toCategoricalVec()));
                return fr.find("CAPSULE"); // Prostate: predict on CAPSULE
              }
            }, false, DistributionFamily.bernoulli);
    Scope.exit();
  }

  // ==========================================================================
  public GBMModel.GBMOutput basicGBM(String fname, PrepData prep, boolean validation, DistributionFamily family) {
    GBMModel gbm = null;
    Frame fr = null, fr2= null, vfr=null;
    try {
      Scope.enter();
      fr = parseTestFile(fname);
      int idx = prep.prep(fr); // hack frame per-test
      if (family == DistributionFamily.bernoulli || family == DistributionFamily.multinomial || family == DistributionFamily.modified_huber) {
        if (!fr.vecs()[idx].isCategorical()) {
          Scope.track(fr.replace(idx, fr.vecs()[idx].toCategoricalVec()));
        }
      }
      DKV.put(fr);             // Update frame after hacking it

      GBMModel.GBMParameters parms = makeGBMParameters();
      if( idx < 0 ) idx = ~idx;
      parms._train = fr._key;
      parms._response_column = fr._names[idx];
      parms._ntrees = 5;
      parms._distribution = family;
      parms._max_depth = 4;
      parms._min_rows = 1;
      parms._nbins = 50;
      parms._learn_rate = .2f;
      parms._score_each_iteration = true;
      if( validation ) {        // Make a validation frame that's a clone of the training data
        vfr = new Frame(fr);
        DKV.put(vfr);
        parms._valid = vfr._key;
      }

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      // Done building model; produce a score column with predictions
      fr2 = gbm.score(fr);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(fr,fr2,1e-15));

      Assert.assertTrue(job.isStopped()); //HEX-1817
      return gbm._output;

    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( vfr != null ) vfr.remove();
      if( gbm != null ) gbm.delete();
      Scope.exit();
    }
  }

  // Test-on-Train.  Slow test, needed to build a good model.
  @Test public void testGBMTrainTest() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    try {
      Scope.enter();
      parms._valid = parseTestFile("smalldata/gbm_test/ecology_eval.csv")._key;
      Frame  train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");    // Convert response to categorical
      Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));
      DKV.put(train);                    // Update frame after hacking it
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._min_rows = 10;
      parms._nbins = 100;
      parms._learn_rate = .2f;
      parms._distribution = DistributionFamily.multinomial;

      gbm = new GBM(parms).trainModel().get();

      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(gbm,parms.valid());
      double auc = mm._auc._auc;
      Assert.assertTrue(0.83 <= auc && auc < 0.87); // Sanely good model
      double[][] cm = mm._auc.defaultCM();
      Assert.assertArrayEquals(ard(ard(349, 44), ard(43, 64)), cm);
    } finally {
      parms._train.remove();
      parms._valid.remove();
      if( gbm != null ) gbm.delete();
      Scope.exit();
    }
  }

  // Predict with no actual, after training
  @Test public void testGBMPredict() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame pred=null, res=null;
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");
      Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(train);                    // Update frame after hacking it
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = DistributionFamily.multinomial;

      gbm = new GBM(parms).trainModel().get();

      pred = parseTestFile("smalldata/gbm_test/ecology_eval.csv" );
      pred.remove("Angaus").remove();    // No response column during scoring
      res = gbm.score(pred);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(pred, res, 1e-15));

    } finally {
      parms._train.remove();
      if( gbm  != null ) gbm .delete();
      if( pred != null ) pred.remove();
      if( res  != null ) res .remove();
      Scope.exit();
    }
  }

  // Scoring should output original probabilities and probabilities calibrated by Platt Scaling
  @Test public void testGBMPredictWithCalibration() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      Frame calib = parseTestFile("smalldata/gbm_test/ecology_eval.csv");

      // Fix training set
      train.remove("Site").remove();     // Remove unique ID
      Scope.track(train.vec("Angaus"));
      train.replace(train.find("Angaus"), train.vecs()[train.find("Angaus")].toCategoricalVec());
      Scope.track(train);
      DKV.put(train); // Update frame after hacking it

      // Fix calibration set (the same way as training)
      Scope.track(calib.vec("Angaus"));
      calib.replace(calib.find("Angaus"), calib.vecs()[calib.find("Angaus")].toCategoricalVec());
      Scope.track(calib);
      DKV.put(calib); // Update frame after hacking it

      parms._train = train._key;
      parms._calibrate_model = true;
      parms._calibration_frame = calib._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = DistributionFamily.multinomial;

      gbm = new GBM(parms).trainModel().get();

      Frame pred = parseTestFile("smalldata/gbm_test/ecology_eval.csv");
      pred.remove("Angaus").remove();    // No response column during scoring
      Scope.track(pred);
      Frame res = Scope.track(gbm.score(pred));

      assertArrayEquals(new String[]{"predict", "p0", "p1", "cal_p0", "cal_p1"}, res._names);
      assertEquals(res.vec("cal_p0").mean(), 0.7860, 1e-4);
      assertEquals(res.vec("cal_p1").mean(), 0.2139, 1e-4);
    } finally {
      if (gbm != null)
        gbm.remove();
      Scope.exit();
    }
  }

  // Adapt a trained model to a test dataset with different categoricals
  @Test public void testModelAdaptMultinomial() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    try {
      Scope.enter();
      Frame v;
      parms._train = (  parseTestFile("smalldata/junit/mixcat_train.csv"))._key;
      parms._valid = (v= parseTestFile("smalldata/junit/mixcat_test.csv" ))._key;
      parms._response_column = "Response"; // Train on the outcome
      parms._ntrees = 1; // Build a CART tree - 1 tree, full learn rate, down to 1 row
      parms._learn_rate = 1.0f;
      parms._min_rows = 1;
      parms._distribution = DistributionFamily.multinomial;

      gbm = new GBM(parms).trainModel().get();

      Frame res = gbm.score(v);

      int[] ps = new int[(int)v.numRows()];
      Vec.Reader vr = res.vecs()[0].new Reader();
      for( int i=0; i<ps.length; i++ ) ps[i] = (int)vr.at8(i);
      // Expected predictions are X,X,Y,Y,X,Y,Z,X,Y
      // Never predicts W, the extra class in the test set.
      // Badly predicts Z because 1 tree does not pick up that feature#2 can also
      // be used to predict Z, and instead relies on factor C which does not appear
      // in the test set.
      Assert.assertArrayEquals("", ps, new int[]{1, 1, 2, 2, 1, 2, 3, 1, 2});

      hex.ModelMetricsMultinomial mm = hex.ModelMetricsMultinomial.getFromDKV(gbm,parms.valid());

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(v,res,1e-15));

      res.remove();

    } finally {
      parms._train.remove();
      parms._valid.remove();
      if( gbm != null ) gbm.delete();
      Scope.exit();
    }
  }

  @Test public void testPredictLeafNodeAssignment() {
    Scope.enter();
    try {
      final Key<Frame> target = Key.make();
      Frame train = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv"));
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");
      Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(train);                    // Update frame after hacking it

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = DistributionFamily.multinomial;

      GBMModel gbm = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());

      Frame pred = Scope.track(parseTestFile("smalldata/gbm_test/ecology_eval.csv"));
      pred.remove("Angaus").remove();    // No response column during scoring

      Frame nodeIds = Scope.track(gbm.scoreLeafNodeAssignment(pred, Model.LeafNodeAssignment.LeafNodeAssignmentType.Node_ID, target));
      Frame nodePaths = Scope.track(gbm.scoreLeafNodeAssignment(pred, Model.LeafNodeAssignment.LeafNodeAssignmentType.Path, target));

      assertArrayEquals(nodePaths._names, nodeIds._names);
      assertEquals(nodePaths.numRows(), nodeIds.numRows());

      for (int i = 0; i < nodePaths.numCols(); i++) {
        String[] paths = nodePaths.vec(i).domain();
        Vec.Reader pathVecRdr = nodePaths.vec(i).new Reader();
        Vec.Reader nodeIdVecRdr = nodeIds.vec(i).new Reader();
        SharedTreeSubgraph tree = gbm.getSharedTreeSubgraph(i, 0);
        for (long j = 0; j < nodePaths.numRows(); j++) {
          String path = paths[(int) pathVecRdr.at8(j)];
          int nodeId = (int) nodeIdVecRdr.at8(j);
          SharedTreeNode node = tree.walkNodes(path);
          assertEquals(node.getNodeNumber(), nodeId);
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUpdateAuxTreeWeights_regression() {
    Scope.enter();
    try {
      String response = "AGE";
      Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._ntrees = 10;
      parms._response_column = response;
      parms._distribution = gaussian;

      GBMModel gbm = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
      checkUpdateAuxTreeWeights(gbm, train);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUpdateAuxTreeWeights_binomial() {
    Scope.enter();
    try {
      String response = "CAPSULE";
      Frame train = Scope.track(parseTestFile("smalldata/prostate/prostate.csv", new int[]{0}));
      train.toCategoricalCol(response);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._ntrees = 10;
      parms._response_column = response;
      parms._distribution = bernoulli;

      GBMModel gbm = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
      checkUpdateAuxTreeWeights(gbm, train);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUpdateAuxTreeWeights_multinomial() {
    Scope.enter();
    try {
      String response = "Angaus";
      Frame train = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv", new int[]{0}));
      train.toCategoricalCol(response);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._ntrees = 10;
      parms._response_column = response;
      parms._distribution = DistributionFamily.multinomial;

      GBMModel gbm = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
      checkUpdateAuxTreeWeights(gbm, train);
    } finally {
      Scope.exit();
    }
  }

  @SuppressWarnings("unchecked")
  private void checkUpdateAuxTreeWeights(GBMModel gbm, Frame frame) {
    Map<Integer, SharedTreeMojoModel.AuxInfo>[][] originalAuxInfos = visitTrees(
            gbm._output._treeKeysAux, Map.class, CompressedTree::toAuxInfos);

    frame = ensureDistributed(frame);
    Frame fr = new Frame(frame);
    fr.add("weights", fr.anyVec().makeCon(2));
    gbm.updateAuxTreeWeights(fr, "weights");

    Map<Integer, SharedTreeMojoModel.AuxInfo>[][] updatedAuxInfos = visitTrees(
            gbm._output._treeKeysAux, Map.class, CompressedTree::toAuxInfos);

    for (int treeId = 0; treeId < originalAuxInfos.length; treeId++) {
      for (int classId = 0; classId < originalAuxInfos[treeId].length; classId++) {
        if (originalAuxInfos[treeId][classId] != null) {
          for (Integer nodeId : originalAuxInfos[treeId][classId].keySet()) {
            SharedTreeMojoModel.AuxInfo orig = originalAuxInfos[treeId][classId].get(nodeId);
            SharedTreeMojoModel.AuxInfo upd = updatedAuxInfos[treeId][classId].get(nodeId);
            assertEquals(2 * orig.weightR, upd.weightR, 0f);
            assertEquals(2 * orig.weightL, upd.weightL, 0f);
          }
        } else {
          assertNull(updatedAuxInfos[treeId][classId]);
        }
      }
    }
  }
  
  @SuppressWarnings("unchecked")
  public static <T> T[][] visitTrees(Key<CompressedTree>[][] treeKeys, Class<? extends T> outputClass,
                                     Function<CompressedTree, T> visitor) {
    T[][] output = (T[][]) Array.newInstance(outputClass, treeKeys.length, 0);
    for (int treeId = 0; treeId < treeKeys.length; treeId++) {
      output[treeId] = (T[]) Array.newInstance(outputClass, treeKeys[treeId].length);
      for (int classId = 0; classId < output[treeId].length; classId++) {
        if (treeKeys[treeId][classId] != null)
          output[treeId][classId] = visitor.apply(treeKeys[treeId][classId].get());
      }
    }
    return output;
  }

  /**
   * Staged predictions test (prediction probabilities of trees per iteration) - binomial data.
   */
  @Test public void testPredictStagedProbabilitiesBinomial() {
    Scope.enter();
    try {
      final Key<Frame> target = Key.make();
      Frame train = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv"));
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");
      Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(train);                    // Update frame after hacking it

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = DistributionFamily.bernoulli;

      GBMModel gbm = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
      Frame stagedProbabilities = Scope.track(gbm.scoreStagedPredictions(train, target));
      Frame predictions = gbm.score(train);
      try {
        GbmMojoModel mojoModel = (GbmMojoModel) gbm.toMojo();
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(
                new EasyPredictModelWrapper.Config().setModel(mojoModel).setEnableStagedProbabilities(true)
        );
        // test for the first 10 rows in training data
        for(int r = 0; r < 10; r++) {
          double[] stagedProbabilitiesRow = new double[stagedProbabilities.numCols()];
          for(int c = 0; c < stagedProbabilities.numCols(); c++) {
            stagedProbabilitiesRow[c] = stagedProbabilities.vec(c).at(r);
          }
          RowData tmpRow = new RowData();
          BufferedString bStr = new BufferedString();
          for (int c = 0; c < train.numCols(); c++) {
            if (train.vec(c).isCategorical()) {
              tmpRow.put(train.names()[c], train.vec(c).atStr(bStr, r).toString());
            } else {
              tmpRow.put(train.names()[c], train.vec(c).at(r));
            }
          }
          BinomialModelPrediction tmpPrediction = model.predictBinomial(tmpRow);
          double[] mojoStageProbabilitiesRow = tmpPrediction.stageProbabilities;
          assertArrayEquals(stagedProbabilitiesRow, mojoStageProbabilitiesRow, 1e-15);
          
          double final_prediction = predictions.vec(1).at(r);
          assertEquals(final_prediction, stagedProbabilitiesRow[stagedProbabilitiesRow.length-1], 1e-15);
        }
        } catch(IOException | PredictException ex){
          fail(ex.toString());
        } finally{
          gbm.delete();
          if (stagedProbabilities != null) stagedProbabilities.delete();
          if (predictions != null) predictions.delete();
      }
    } finally {
      Scope.exit();
    }
  }

  /**
   * Staged predictions test (prediction probabilities of trees per iteration) - multinomial data.
   */
  @Test public void testPredictStagedProbabilitiesMultinomial() {
    Scope.enter();
    try {
      final Key<Frame> target = Key.make();
      Frame train = Scope.track(parseTestFile("./smalldata/logreg/prostate.csv"));
      train.remove("ID").remove();     // Remove unique ID
      int ci = train.find("RACE");
      Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(train);                    // Update frame after hacking it

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "RACE"; // Train on the outcome
      parms._distribution = DistributionFamily.multinomial;

      GBMModel gbm = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
      Frame stagedProbabilities = Scope.track(gbm.scoreStagedPredictions(train, target));
      Frame predictions = gbm.score(train);
      try {
        GbmMojoModel mojoModel = (GbmMojoModel) gbm.toMojo();
        EasyPredictModelWrapper model = new EasyPredictModelWrapper(
                new EasyPredictModelWrapper.Config().setModel(mojoModel).setEnableStagedProbabilities(true)
        );
        // test for the first 10 rows in training data
        for(int r = 0; r < 10; r++) {
          double[] stagedProbabilitiesRow = new double[stagedProbabilities.numCols()];
          for (int c = 0; c < stagedProbabilities.numCols(); c++) {
            stagedProbabilitiesRow[c] = stagedProbabilities.vec(c).at(r);
          }

          RowData tmpRow = new RowData();
          BufferedString bStr = new BufferedString();
          for (int c = 0; c < train.numCols(); c++) {
            if (train.vec(c).isCategorical()) {
              tmpRow.put(train.names()[c], train.vec(c).atStr(bStr, r).toString());
            } else {
              tmpRow.put(train.names()[c], train.vec(c).at(r));
            }
          }
          
          MultinomialModelPrediction tmpPrediction = model.predictMultinomial(tmpRow);
          double[] mojoStageProbabilitiesRow = tmpPrediction.stageProbabilities;
          assertArrayEquals(stagedProbabilitiesRow, mojoStageProbabilitiesRow, 1e-15);

          double[] final_prediction = {predictions.vec(1).at(r), predictions.vec(2).at(r), predictions.vec(3).at(r)};
          assertArrayEquals(final_prediction, Arrays.copyOfRange(stagedProbabilitiesRow, stagedProbabilitiesRow.length-3, stagedProbabilitiesRow.length), 1e-15);
        }
      } catch (IOException | PredictException ex) {
        fail(ex.toString());
      } finally {
        gbm.delete();
        if (stagedProbabilities != null) stagedProbabilities.delete();
        if (predictions != null) predictions.delete();
      }
    } finally {
      Scope.exit();
    }
  }

  // A test of locking the input dataset during model building.
  @Test public void testModelLock() {
    Scope.enter();
    try {
      GBMModel.GBMParameters parms = makeGBMParameters();
      Frame fr = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv"));
      fr.remove("Site").remove();        // Remove unique ID
      int ci = fr.find("Angaus");
      Scope.track(fr.replace(ci, fr.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(fr);                       // Update after hacking
      parms._train = fr._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._ntrees = 50;
      parms._max_depth = 10;
      parms._min_rows = 1;
      parms._learn_rate = .2f;
      parms._seed = 42L;
      parms._distribution = DistributionFamily.multinomial;
      GBM gbm = new GBM(parms);
      
      gbm.trainModel();
      try { Thread.sleep(100); }
      catch( Exception e ) { e.printStackTrace(); } // just in case

      boolean delete_ok = false;
      try {
        Log.info("Trying illegal frame delete.");
        fr.delete();            // Attempted delete while model-build is active
        delete_ok = true;
        Log.err("Frame " + fr._key + " was deleted while it should have been locked!");
      } catch( IllegalArgumentException ignore ) {
      } catch( RuntimeException re ) {
        assertTrue( re.getCause() instanceof IllegalArgumentException);
      }
      
      Log.info("Getting model"); // in order to clean it up
      Scope.track_generic(gbm.get());
      Assert.assertTrue(gbm.isStopped()); //HEX-1817
      
      Assert.assertFalse("Frame " + fr._key + " was deleted while it should have been locked!", delete_ok);
    } finally {
      Scope.exit();
    }
  }

  //  MSE generated by GBM with/without validation dataset should be same
  @Test public void testModelScoreKeeperEqualityOnProstateBernoulli() {
    final PrepData prostatePrep = new PrepData() { @Override int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, false, bernoulli)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, true , bernoulli)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnProstateGaussian() {
    final PrepData prostatePrep = new PrepData() { @Override int prep(Frame fr) { fr.remove("ID").remove(); return ~fr.find("CAPSULE"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, false, gaussian)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, true , gaussian)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnProstateQuasibinomial() {
    final PrepData prostatePrep = new PrepData() { @Override int prep(Frame fr) { fr.remove("ID").remove(); return ~fr.find("CAPSULE"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, false, quasibinomial)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, true , quasibinomial)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnProstateMultinomial() {
    final PrepData prostatePrep = new PrepData() { @Override int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("RACE"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, false, multinomial)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, true , multinomial)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnTitanicGaussian() {
    final PrepData titanicPrep = new PrepData() { @Override int prep(Frame fr) { return fr.find("age"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, false, gaussian)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, true , gaussian)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnTitanicBernoulli() {
    final PrepData titanicPrep = new PrepData() { @Override int prep(Frame fr) { return fr.find("survived"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, false, bernoulli)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, true , bernoulli)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnTitanicQuasibinomial() {
    final PrepData titanicPrep = new PrepData() { @Override int prep(Frame fr) { return fr.find("survived"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, false, quasibinomial)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, true , quasibinomial)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnTitanicMultinomial() {
    final PrepData titanicPrep = new PrepData() { @Override int prep(Frame fr) { return fr.find("survived"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, false, multinomial)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, true , multinomial)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testBigCat() {
    final PrepData prep = new PrepData() { @Override int prep(Frame fr) { return fr.find("y"); } };
    basicGBM("./smalldata/gbm_test/50_cattest_test.csv" , prep, false, bernoulli);
    basicGBM("./smalldata/gbm_test/50_cattest_train.csv", prep, false, bernoulli);
    basicGBM("./smalldata/gbm_test/swpreds_1000x3.csv", prep, false, bernoulli);
  }

  // Test uses big data and is too slow for a pre-push
  @Test @Ignore public void testKDDTrees() {
    Frame tfr=null, vfr=null;
    String[] cols = new String[] {"DOB", "LASTGIFT", "TARGET_D"};
    try {
      // Load data, hack frames
      Frame inF1 = parseTestFile("bigdata/laptop/usecases/cup98LRN_z.csv");
      Frame inF2 = parseTestFile("bigdata/laptop/usecases/cup98VAL_z.csv");
      tfr = inF1.subframe(cols); // Just the columns to train on
      vfr = inF2.subframe(cols);
      inF1.remove(cols).remove(); // Toss all the rest away
      inF2.remove(cols).remove();
      tfr.replace(0, tfr.vec("DOB").toCategoricalVec());     // Convert 'DOB' to categorical
      vfr.replace(0, vfr.vec("DOB").toCategoricalVec());
      DKV.put(tfr);
      DKV.put(vfr);

      // Same parms for all
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "TARGET_D";
      parms._ntrees = 3;
      parms._distribution = gaussian;
      // Build a first model; all remaining models should be equal
      GBM job1 = new GBM(parms);
      GBMModel gbm1 = job1.trainModel().get();
      // Validation MSE should be equal
      ScoreKeeper[] firstScored = gbm1._output._scored_valid;

      // Build 10 more models, checking for equality
      for( int i=0; i<10; i++ ) {
        GBM job2 = new GBM(parms);
        GBMModel gbm2 = job2.trainModel().get();
        ScoreKeeper[] secondScored = gbm2._output._scored_valid;
        // Check that MSE's from both models are equal
        int j;
        for( j=0; j<firstScored.length; j++ )
          if (firstScored[j] != secondScored[j])
            break;              // Not Equals Enough
        // Report on unequal
        if( j < firstScored.length ) {
          System.out.println("=== =============== ===");
          System.out.println("=== ORIGINAL  MODEL ===");
          for( int t=0; t<parms._ntrees; t++ )
            System.out.println(gbm1._output.toStringTree(t,0));
          System.out.println("=== DIFFERENT MODEL ===");
          for( int t=0; t<parms._ntrees; t++ )
            System.out.println(gbm2._output.toStringTree(t,0));
          System.out.println("=== =============== ===");
          Assert.assertArrayEquals("GBM should have the exact same MSEs for identical parameters", firstScored, secondScored);
        }
        gbm2.delete();
      }
      gbm1.delete();

    } finally {
      if (tfr  != null) tfr.remove();
      if (vfr  != null) vfr.remove();
    }
  }


  // Test uses big data and is too slow for a pre-push
  @Test @Ignore public void testMNIST() {
    Frame tfr=null, vfr=null;
    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parseTestFile("bigdata/laptop/mnist/train.csv.gz");
      Scope.track(tfr.replace(784, tfr.vecs()[784].toCategoricalVec()));   // Convert response 'C785' to categorical
      DKV.put(tfr);

      vfr = parseTestFile("bigdata/laptop/mnist/test.csv.gz");
      Scope.track(vfr.replace(784, vfr.vecs()[784].toCategoricalVec()));   // Convert response 'C785' to categorical
      DKV.put(vfr);

      // Same parms for all
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "C785";
      parms._ntrees = 2;
      parms._max_depth = 4;
      parms._distribution = DistributionFamily.multinomial;
      // Build a first model; all remaining models should be equal
      GBMModel gbm = new GBM(parms).trainModel().get();

      Frame pred = gbm.score(vfr);
      double sq_err = new MathUtils.SquareError().doAll(vfr.lastVec(),pred.vecs()[0])._sum;
      double mse = sq_err/pred.numRows();
      assertEquals(3.0199, mse, 1e-15); //same results
      gbm.delete();
    } finally {
      if (tfr  != null) tfr.remove();
      if (vfr  != null) vfr.remove();
      Scope.exit();
    }
  }

  // GH issue https://github.com/h2oai/private-h2o-3/issues/480: Check reproducibility for the same # of
  // chunks (i.e., same # of nodes) and same parameters
  @Test public void testReprodubility() {
    Frame tfr=null;
    final int N = 5;
    double[] mses = new double[N];

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parseTestFile("smalldata/covtype/covtype.20k.data");

      // rebalance to 256 chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, 256);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toCategoricalVec())._key);
//      DKV.put(tfr);

      for (int i=0; i<N; ++i) {
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = "C55";
        parms._nbins = 1000;
        parms._ntrees = 5;
        parms._max_depth = 8;
        parms._learn_rate = 0.1f;
        parms._min_rows = 10;
//        parms._distribution = Family.multinomial;
        parms._distribution = gaussian;

        // Build a first model; all remaining models should be equal
        GBMModel gbm = new GBM(parms).trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        gbm.delete();
      }
    } finally{
      if (tfr != null) tfr.remove();
    }
    Scope.exit();

    for( double mse : mses )
      System.out.println(mse);
    for( double mse : mses )
      assertEquals(mse, mses[0], 1e-15);
  }

  // PUBDEV-557: Test dependency on # nodes (for small number of bins, but fixed number of chunks)
  @Test public void testReprodubilityAirline() {
    Frame tfr=null;
    final int N = 5;
    double[] mses = new double[N];

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parseTestFile("./smalldata/airlines/allyears2k_headers.zip");

      // rebalance to fixed number of chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, 256);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toCategoricalVec())._key);
//      DKV.put(tfr);
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      for (int i=0; i<N; ++i) {
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = "IsDepDelayed";
        parms._nbins = 10;
        parms._nbins_cats = 500;
        parms._ntrees = 7;
        parms._max_depth = 5;
        parms._min_rows = 10;
        parms._distribution = DistributionFamily.bernoulli;
        parms._balance_classes = true;
        parms._seed = 0;

        // Build a first model; all remaining models should be equal
        GBMModel gbm = new GBM(parms).trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        gbm.delete();
      }
    } finally {
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    System.out.println("MSEs start");
    for(double d:mses)
      System.out.println(d);
    System.out.println("MSEs End");
    System.out.flush();
    for( double mse : mses )
      assertEquals(0.21694215729861027, mse, 1e-8); //check for the same result on 1 nodes and 5 nodes (will only work with enough chunks), mse, 1e-8); //check for the same result on 1 nodes and 5 nodes (will only work with enough chunks)
  }

  @Test public void testReprodubilityAirlineSingleNode() {
    Frame tfr=null;
    final int N = 10;
    double[] mses = new double[N];

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parseTestFile("./smalldata/airlines/allyears2k_headers.zip");

      // rebalance to fixed number of chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, 256);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toCategoricalVec())._key);
//      DKV.put(tfr);
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      for (int i=0; i<N; ++i) {
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = "IsDepDelayed";
        parms._nbins = 10;
        parms._nbins_cats = 500;
        parms._ntrees = 7;
        parms._max_depth = 5;
        parms._min_rows = 10;
        parms._distribution = DistributionFamily.bernoulli;
        parms._balance_classes = true;
        parms._seed = 0;
        parms._build_tree_one_node = true;

        // Build a first model; all remaining models should be equal
        GBMModel gbm = new GBM(parms).trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        gbm.delete();
      }
    } finally {
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    System.out.println("MSE");
    for(double d:mses)
      System.out.println(d);
    for( double mse : mses )
      assertEquals(0.21694215729861027, mse, 1e-8); //check for the same result on 1 nodes and 5 nodes (will only work with enough chunks)
  }

  // GH issue: https://github.com/h2oai/private-h2o-3/issues/457
  @Test public void testCategorical() {
    Frame tfr=null;
    final int N = 1;
    double[] mses = new double[N];

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/gbm_test/alphabet_cattest.csv");
      Scope.track(tfr.replace(1, tfr.vecs()[1].toCategoricalVec()));
      DKV.put(tfr);
      for (int i=0; i<N; ++i) {
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = "y";
        parms._ntrees = 1;
        parms._max_depth = 1;
        parms._learn_rate = 1;
        parms._distribution = DistributionFamily.bernoulli;

        // Build a first model; all remaining models should be equal
        GBMModel gbm = new GBM(parms).trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(gbm,parms.train());
        double auc = mm._auc._auc;
        Assert.assertTrue(1 == auc);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        gbm.delete();
      }
    } finally{
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    for( double mse : mses ) assertEquals(0.0142093, mse, 1e-6);
  }

  // Test uses big data and is too slow for a pre-push
  @Test @Ignore public void testCUST_A() {
    Frame tfr=null, vfr=null, t_pred=null, v_pred=null;
    GBMModel gbm=null;
    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parseTestFile("./bigdata/covktr.csv");
      vfr = parseTestFile("./bigdata/covkts.csv");
      int idx = tfr.find("V55");
      Scope.track(tfr.replace(idx, tfr.vecs()[idx].toCategoricalVec()));
      Scope.track(vfr.replace(idx, vfr.vecs()[idx].toCategoricalVec()));
      DKV.put(tfr);
      DKV.put(vfr);

      // Build model
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "V55";
      parms._ntrees = 10;
      parms._max_depth = 1;
      parms._nbins = 20;
      parms._min_rows = 10;
      parms._learn_rate = 0.01f;
      parms._distribution = DistributionFamily.multinomial;
      gbm = new GBM(parms).trainModel().get();

      // Report AUC from training
      hex.ModelMetricsBinomial tmm = hex.ModelMetricsBinomial.getFromDKV(gbm,tfr);
      hex.ModelMetricsBinomial vmm = hex.ModelMetricsBinomial.getFromDKV(gbm,vfr);
      double t_auc = tmm._auc._auc;
      double v_auc = vmm._auc._auc;
      System.out.println("train_AUC= "+t_auc+" , validation_AUC= "+v_auc);

      // Report AUC from scoring
      t_pred = gbm.score(tfr);
      v_pred = gbm.score(vfr);
      hex.ModelMetricsBinomial tmm2 = hex.ModelMetricsBinomial.getFromDKV(gbm,tfr);
      hex.ModelMetricsBinomial vmm2 = hex.ModelMetricsBinomial.getFromDKV(gbm,vfr);
      assert tmm != tmm2;
      assert vmm != vmm2;
      double t_auc2 = tmm._auc._auc;
      double v_auc2 = vmm._auc._auc;
      System.out.println("train_AUC2= "+t_auc2+" , validation_AUC2= "+v_auc2);
      t_pred.remove();
      v_pred.remove();

      // Compute the perfect AUC
      double t_auc3 = AUC2.perfectAUC(t_pred.vecs()[2], tfr.vec("V55"));
      double v_auc3 = AUC2.perfectAUC(v_pred.vecs()[2], vfr.vec("V55"));
      System.out.println("train_AUC3= "+t_auc3+" , validation_AUC3= "+v_auc3);
      Assert.assertEquals(t_auc3, t_auc , 1e-6);
      Assert.assertEquals(t_auc3, t_auc2, 1e-6);
      Assert.assertEquals(v_auc3, v_auc , 1e-6);
      Assert.assertEquals(v_auc3, v_auc2, 1e-6);

    } finally {
      if (tfr  != null) tfr.remove();
      if (vfr  != null) vfr.remove();
      if( t_pred != null ) t_pred.remove();
      if( v_pred != null ) v_pred.remove();
      if (gbm  != null) gbm.delete();
      Scope.exit();
    }
  }
  static double _AUC = 1;
  static double _MSE = 0.24850374695598948;
  static double _LogLoss = 0.690155;

  @Test
  public void testNoRowWeights() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/no_weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      Frame pred = gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);
      pred.remove();
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) gbm.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeightsOne() {
    Frame tfr = null, vfr = null;

    Scope.enter();
    GBMModel gbm = null;
    try {
      tfr = parseTestFile("smalldata/junit/weights_all_ones.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      Frame pred = gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);
      pred.remove();

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) gbm.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeightsTwo() {
    Frame tfr = null, vfr = null;

    Scope.enter();
    GBMModel gbm = null;
    try {
      tfr = parseTestFile("smalldata/junit/weights_all_twos.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 2; //Must be adapted to the weights
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      Frame pred = gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);
      pred.remove();

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) gbm.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeightsTiny() {
    Frame tfr = null, vfr = null;

    Scope.enter();
    GBMModel gbm = null;
    try {
      tfr = parseTestFile("smalldata/junit/weights_all_tiny.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 0.01242; //Must be adapted to the weights
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      Frame pred = gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);
      pred.remove();

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) gbm.delete();
      Scope.exit();
    }
  }

  @Test
  public void testNoRowWeightsShuffled() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/no_weights_shuffled.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      Frame pred = gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);
      pred.remove();

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) gbm.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeights() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      Frame pred = gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);
      pred.remove();

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) gbm.delete();
      Scope.exit();
    }
  }

  @Test
  public void testNFold() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 123;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._nfolds = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;
      parms._keep_cross_validation_predictions = true;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._cross_validation_metrics;
      assertEquals(0.6296296296296297, mm.auc_obj()._auc, 1e-8);
      assertEquals(0.28640022521234304, mm.mse(), 1e-8);
      assertEquals(0.7674117059335286, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) {
        gbm.deleteCrossValidationModels();
        gbm.delete();
        for (Key k : gbm._output._cross_validation_predictions) k.remove();
        gbm._output._cross_validation_holdout_predictions_frame_id.remove();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsOneVsRest() {
    Frame tfr = null;
    GBMModel gbm1 = null;
    GBMModel gbm2 = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._nfolds = (int) tfr.numRows();
      parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
      parms._ntrees = 3;
      parms._seed = 12345;
      parms._learn_rate = 1e-3f;

      gbm1 = new GBM(parms).trainModel().get();

      //parms._nfolds = (int) tfr.numRows() + 1; //This is now an error
      gbm2 = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm1 = (ModelMetricsBinomial)gbm1._output._cross_validation_metrics;
      ModelMetricsBinomial mm2 = (ModelMetricsBinomial)gbm2._output._cross_validation_metrics;
      assertEquals(mm1.auc_obj()._auc, mm2.auc_obj()._auc, 1e-12);
      assertEquals(mm1.mse(), mm2.mse(), 1e-12);
      //assertEquals(mm1.r2(), mm2.r2(), 1e-12);
      assertEquals(mm1.logloss(), mm2.logloss(), 1e-12);

      //TODO: add check: the correct number of individual models were built. PUBDEV-1690

    } finally {
      if (tfr != null) tfr.remove();
      if (gbm1 != null) {
        gbm1.deleteCrossValidationModels();
        gbm1.delete();
      }
      if (gbm2 != null) {
        gbm2.deleteCrossValidationModels();
        gbm2.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsInvalidValues() {
    Frame tfr = null;
    GBMModel gbm1 = null;
    GBMModel gbm2 = null;
    GBMModel gbm3 = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._min_rows = 1;
      parms._seed = 12345;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      parms._nfolds = 0;
      gbm1 = new GBM(parms).trainModel().get();

      parms._nfolds = 1;
      try {
        Log.info("Trying nfolds==1.");
        gbm2 = new GBM(parms).trainModel().get();
        Assert.fail("Should toss H2OModelBuilderIllegalArgumentException instead of reaching here");
      } catch(H2OModelBuilderIllegalArgumentException e) {}

      parms._nfolds = -99;
      try {
        Log.info("Trying nfolds==-99.");
        gbm3 = new GBM(parms).trainModel().get();
        Assert.fail("Should toss H2OModelBuilderIllegalArgumentException instead of reaching here");
      } catch(H2OModelBuilderIllegalArgumentException e) {}

    } finally {
      if (tfr != null) tfr.remove();
      if (gbm1 != null) gbm1.delete();
      if (gbm2 != null) gbm2.delete();
      if (gbm3 != null) gbm3.delete();
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsCVAndValidation() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights.csv");
      vfr = parseTestFile("smalldata/junit/weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "response";
      parms._seed = 12345;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._nfolds = 3;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      try {
        Log.info("Trying N-fold cross-validation AND Validation dataset provided.");
        gbm = new GBM(parms).trainModel().get();
      } catch(H2OModelBuilderIllegalArgumentException e) {
        Assert.fail("Should not toss H2OModelBuilderIllegalArgumentException.");
      }

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) {
        gbm.deleteCrossValidationModels();
        gbm.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsConsecutiveModelsSame() {
    Frame tfr = null;
    Vec old = null;
    GBMModel gbm1 = null;
    GBMModel gbm2 = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/cars_20mpg.csv");
      tfr.remove("name").remove(); // Remove unique id
      tfr.remove("economy").remove();
      old = tfr.remove("economy_20mpg");
      tfr.add("economy_20mpg", old.toCategoricalVec()); // response to last column
      DKV.put(tfr);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "economy_20mpg";
      parms._min_rows = 1;
      parms._seed = 12345;
      parms._max_depth = 2;
      parms._nfolds = 3;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      gbm1 = new GBM(parms).trainModel().get();

      gbm2 = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm1 = (ModelMetricsBinomial)gbm1._output._cross_validation_metrics;
      ModelMetricsBinomial mm2 = (ModelMetricsBinomial)gbm2._output._cross_validation_metrics;
      assertEquals(mm1.auc_obj()._auc, mm2.auc_obj()._auc, 1e-12);
      assertEquals(mm1.mse(), mm2.mse(), 1e-12);
      //assertEquals(mm1.r2(), mm2.r2(), 1e-12);
      assertEquals(mm1.logloss(), mm2.logloss(), 1e-12);

    } finally {
      if (tfr != null) tfr.remove();
      if (old != null) old.remove();
      if (gbm1 != null) {
        gbm1.deleteCrossValidationModels();
        gbm1.delete();
      }
      if (gbm2 != null) {
        gbm2.deleteCrossValidationModels();
        gbm2.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsColumn() {
    Frame tfr = null;
    GBMModel gbm1 = null;

    try {
      tfr = parseTestFile("smalldata/junit/cars_20mpg.csv");
      tfr.remove("name").remove(); // Remove unique id
      DKV.put(tfr);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "economy_20mpg";
      parms._fold_column = "cylinders";
      Vec old = tfr.remove("cylinders");
      tfr.add("cylinders",old.toCategoricalVec());
      DKV.put(tfr);
      parms._ntrees = 10;
      parms._keep_cross_validation_fold_assignment = true;
      parms._keep_cross_validation_models = true;

      GBM job1 = new GBM(parms);
      gbm1 = job1.trainModel().get();
      Assert.assertTrue(gbm1._output._cross_validation_models.length == 5);
      old.remove();
    } finally {
      if (tfr != null) tfr.remove();
      if (gbm1 != null) {
        gbm1.deleteCrossValidationModels();
        gbm1.delete();
        gbm1._output._cross_validation_fold_assignment_frame_id.remove();
      }
    }
  }

  @Test
  public void testNfoldsColumnNumbersFrom0() {
    Frame tfr = null;
    Vec old = null;
    GBMModel gbm1 = null;

    try {
      tfr = parseTestFile("smalldata/junit/cars_20mpg.csv");
      tfr.remove("name").remove(); // Remove unique id
      new MRTask() {
        @Override
        public void map(Chunk c) {
          for (int i=0;i<c.len();++i) {
            if (c.at8(i) == 3) c.set(i, 0);
            if (c.at8(i) == 4) c.set(i, 1);
            if (c.at8(i) == 5) c.set(i, 2);
            if (c.at8(i) == 6) c.set(i, 3);
            if (c.at8(i) == 8) c.set(i, 4);
          }
        }
      }.doAll(tfr.vec("cylinders"));
      DKV.put(tfr);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "economy_20mpg";
      parms._fold_column = "cylinders";
      parms._ntrees = 10;
      parms._keep_cross_validation_models = true;

      GBM job1 = new GBM(parms);
      gbm1 = job1.trainModel().get();
      Assert.assertTrue(gbm1._output._cross_validation_models.length == 5);
    } finally {
      if (tfr != null) tfr.remove();
      if (old != null) old.remove();
      if (gbm1 != null) {
        gbm1.deleteCrossValidationModels();
        gbm1.delete();
      }
    }
  }

  @Test
  public void testNfoldsColumnCategorical() {
    Frame tfr = null;
    Vec old = null;
    GBMModel gbm1 = null;

    try {
      tfr = parseTestFile("smalldata/junit/cars_20mpg.csv");
      tfr.remove("name").remove(); // Remove unique id
      old = tfr.remove("cylinders");
      tfr.add("folds", old.toCategoricalVec());
      old.remove();
      DKV.put(tfr);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "economy_20mpg";
      parms._fold_column = "folds";
      parms._ntrees = 10;
      parms._keep_cross_validation_models = true;

      GBM job1 = new GBM(parms);
      gbm1 = job1.trainModel().get();
      Assert.assertTrue(gbm1._output._cross_validation_models.length == 5);
    } finally {
      if (tfr != null) tfr.remove();
      if (old != null) old.remove();
      if (gbm1 != null) {
        gbm1.deleteCrossValidationModels();
        gbm1.delete();
      }
    }
  }

  @Test
  public void testNFoldAirline() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parseTestFile("./smalldata/airlines/allyears2k_headers.zip");
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "IsDepDelayed";
      parms._seed = 234;
      parms._min_rows = 2;
      parms._nfolds = 3;
      parms._max_depth = 5;
      parms._ntrees = 5;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._cross_validation_metrics;
      assertEquals(0.7309795467719639, mm.auc_obj()._auc, 1e-4); // 1 node
      assertEquals(0.22511756378273942, mm.mse(), 1e-4);
      assertEquals(0.6425515048581261, mm.logloss(), 1e-4);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) {
        gbm.deleteCrossValidationModels();
        gbm.delete();
      }
      Scope.exit();
    }
  }

  // just a simple sanity check - not a golden test
  @Test
  public void testDistributions() throws Exception {
    Frame tfr = null, vfr = null, res= null;
    GBMModel gbm = null;

    for (DistributionFamily dist : new DistributionFamily[]{
            DistributionFamily.AUTO,
            gaussian,
            DistributionFamily.poisson,
            DistributionFamily.gamma,
            DistributionFamily.tweedie
    }) {
      Scope.enter();
      try {
        tfr = parseTestFile("smalldata/glm_test/cancar_logIn.csv");
        vfr = parseTestFile("smalldata/glm_test/cancar_logIn.csv");
        for (String s : new String[]{
                "Merit", "Class"
        }) {
          Scope.track(tfr.replace(tfr.find(s), tfr.vec(s).toCategoricalVec()));
          Scope.track(vfr.replace(vfr.find(s), vfr.vec(s).toCategoricalVec()));
        }
        DKV.put(tfr);
        DKV.put(vfr);
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = "Cost";
        parms._seed = 0xdecaf;
        parms._distribution = dist;
        parms._min_rows = 1;
        parms._ntrees = 30;
        parms._offset_column = "logInsured"; // POJO scoring not supported for offsets (only MOJO will be tested)
        parms._learn_rate = 1e-3f;

        // Build a first model; all remaining models should be equal
        gbm = new GBM(parms).trainModel().get();

        assertEquals("logInsured", gbm.toMojo().getOffsetName());
        
        res = gbm.score(vfr);
        Assert.assertTrue(gbm.testJavaScoring(vfr,res,1e-15));
        res.remove();

        ModelMetricsRegression mm = (ModelMetricsRegression)gbm._output._training_metrics;

      } finally {
        if (tfr != null) tfr.remove();
        if (vfr != null) vfr.remove();
        if (res != null) res.remove();
        if (gbm != null) gbm.delete();
        Scope.exit();
      }
    }
  }

  @Test
  public void testStochasticGBM() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;
    float[] sample_rates = new float[]{0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
    float[] col_sample_rates = new float[]{0.2f, 0.4f, 0.6f, 0.8f, 1.0f};

    Map<Double, Pair<Float,Float>> hm = new TreeMap<>();
    for (float sample_rate : sample_rates) {
      for (float col_sample_rate : col_sample_rates) {
        Scope.enter();
        try {
          tfr = parseTestFile("./smalldata/gbm_test/ecology_model.csv");
          DKV.put(tfr);
          GBMModel.GBMParameters parms = makeGBMParameters();
          parms._train = tfr._key;
          parms._response_column = "Angaus"; //regression
          parms._seed = 123;
          parms._min_rows = 2;
          parms._max_depth = 10;
          parms._ntrees = 2;
          parms._col_sample_rate = col_sample_rate;
          parms._sample_rate = sample_rate;

          // Build a first model; all remaining models should be equal
          gbm = new GBM(parms).trainModel().get();

          ModelMetricsRegression mm = (ModelMetricsRegression)gbm._output._training_metrics;
          hm.put(mm.mse(), new Pair<>(sample_rate, col_sample_rate));

        } finally {
          if (tfr != null) tfr.remove();
          if (vfr != null) vfr.remove();
          if (gbm != null) gbm.delete();
          Scope.exit();
        }
      }
    }
    double fullDataMSE = hm.entrySet().iterator().next().getKey();
    Iterator<Map.Entry<Double, Pair<Float, Float>>> it;
    int i=0;
    Pair<Float, Float> last = null;
    // iterator over results (min to max MSE) - best to worst
    for (it=hm.entrySet().iterator(); it.hasNext(); ++i) {
      Map.Entry<Double, Pair<Float,Float>> n = it.next();
      if (i>0) Assert.assertTrue(n.getKey() > fullDataMSE); //any sampling should make training set MSE worse
      Log.info( "MSE: " + n.getKey() + ", "
              + ", row sample: " + ((Pair)n.getValue())._1()
              + ", col sample: " + ((Pair)n.getValue())._2());
      last=n.getValue();
    }
    // worst training MSE should belong to the most sampled case
    Assert.assertTrue(last._1()==sample_rates[0]);
    Assert.assertTrue(last._2()==col_sample_rates[0]);
  }

  @Test
  public void testStochasticGBMHoldout() {
    Frame tfr = null;
    Key[] ksplits = new Key[0];
    try{
      tfr= parseTestFile("./smalldata/gbm_test/ecology_model.csv");
      SplitFrame sf = new SplitFrame(tfr,new double[] { 0.5, 0.5 },new Key[] { Key.make("train.hex"), Key.make("test.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;

      GBMModel gbm = null;
      float[] sample_rates = new float[]{0.2f, 0.4f, 0.8f, 1.0f};
      float[] col_sample_rates = new float[]{0.4f, 0.8f, 1.0f};
      float[] col_sample_rates_per_tree = new float[]{0.4f, 0.6f, 1.0f};

      Map<Double, Triple<Float>> hm = new TreeMap<>();
      for (float sample_rate : sample_rates) {
        for (float col_sample_rate : col_sample_rates) {
          for (float col_sample_rate_per_tree : col_sample_rates_per_tree) {
            Scope.enter();
            try {
              GBMModel.GBMParameters parms = makeGBMParameters();
              parms._train = ksplits[0];
              parms._valid = ksplits[1];
              parms._response_column = "Angaus"; //regression
              parms._seed = 42;
              parms._min_rows = 2;
              parms._max_depth = 12;
              parms._ntrees = 6;
              parms._col_sample_rate = col_sample_rate;
              parms._col_sample_rate_per_tree = col_sample_rate_per_tree;
              parms._sample_rate = sample_rate;

              // Build a first model; all remaining models should be equal
              gbm = new GBM(parms).trainModel().get();

              // too slow, but passes (now)
//            // Build a POJO, validate same results
//            Frame pred = gbm.score(tfr);
//            Assert.assertTrue(gbm.testJavaScoring(tfr,pred,1e-15));
//            pred.remove();

              ModelMetricsRegression mm = (ModelMetricsRegression)gbm._output._validation_metrics;
              hm.put(mm.mse(), new Triple<>(sample_rate, col_sample_rate, col_sample_rate_per_tree));

            } finally {
              if (gbm != null) gbm.delete();
              Scope.exit();
            }
          }
        }
      }
      Iterator<Map.Entry<Double, Triple<Float>>> it;
      Triple<Float> last = null;
      // iterator over results (min to max MSE) - best to worst
      for (it=hm.entrySet().iterator(); it.hasNext();) {
        Map.Entry<Double, Triple<Float>> n = it.next();
        Log.info( "MSE: " + n.getKey()
            + ", row sample: " + n.getValue().v1
            + ", col sample: " + n.getValue().v2
            + ", col sample per tree: " + n.getValue().v3);
        last=n.getValue();
      }
      // worst validation MSE should belong to the most overfit case (1.0, 1.0, 1.0)
//      Assert.assertTrue(last.v1==sample_rates[sample_rates.length-1]);
//      Assert.assertTrue(last.v2==col_sample_rates[col_sample_rates.length-1]);
//      Assert.assertTrue(last.v3==col_sample_rates_per_tree[col_sample_rates_per_tree.length-1]);
    } finally {
      if (tfr != null) tfr.remove();
      for (Key k : ksplits)
        if (k!=null) k.remove();
    }
  }

  // PUBDEV-2476 Check reproducibility for the same # of chunks (i.e., same # of nodes) and same parameters
  @Test public void testChunks() {
    Frame tfr;
    int[] chunks = new int[]{1,2,2,39,39,500};
    final int N = chunks.length;
    double[] mses = new double[N];
    for (int i=0; i<N; ++i) {
      Scope.enter();
      // Load data, hack frames
      tfr = parseTestFile("smalldata/covtype/covtype.20k.data");

      // rebalance to a given number of chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, chunks[i]);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
      assertEquals(tfr.vec(0).nChunks(), chunks[i]);
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toCategoricalVec())._key);
      DKV.put(tfr);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "C55";
      parms._seed = 1234;
      parms._auto_rebalance = false;
      parms._col_sample_rate_per_tree = 0.5f;
      parms._col_sample_rate = 0.3f;
      parms._ntrees = 5;
      parms._max_depth = 5;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      GBMModel drf = job.trainModel().get();
      assertEquals(drf._output._ntrees, parms._ntrees);

      mses[i] = drf._output._scored_train[drf._output._scored_train.length-1]._mse;
      drf.delete();
      if (tfr != null) tfr.remove();
      Scope.exit();
    }
    for (int i=0; i<mses.length; ++i) {
      Log.info("trial: " + i + " -> MSE: " + mses[i]);
    }
    for(double mse : mses)
      assertEquals(mse, mses[0], 1e-10);
  }

  @Test public void testLaplace2() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame pred=null, res=null;
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      train.remove("Site").remove();     // Remove unique ID
      train.remove("Method").remove();   // Remove categorical
      DKV.put(train);                    // Update frame after hacking it
      parms._train = train._key;
      parms._response_column = "DSDist"; // Train on the outcome
      parms._distribution = laplace;
      parms._sample_rate = 0.6f;
      parms._col_sample_rate = 0.8f;
      parms._col_sample_rate_per_tree = 0.8f;
      parms._seed = 1234;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      pred = parseTestFile("smalldata/gbm_test/ecology_eval.csv" );
      res = gbm.score(pred);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(pred, res, 1e-15));
      Assert.assertTrue(Math.abs(((ModelMetricsRegression)gbm._output._training_metrics)._mean_residual_deviance - 23.05805) < 1e-4);

    } finally {
      parms._train.remove();
      if( gbm  != null ) gbm .delete();
      if( pred != null ) pred.remove();
      if( res  != null ) res .remove();
      Scope.exit();
    }
  }

  @Test public void testQuantileRegression() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame pred=null, res=null;
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      train.remove("Site").remove();     // Remove unique ID
      train.remove("Method").remove();   // Remove categorical
      DKV.put(train);                    // Update frame after hacking it
      parms._train = train._key;
      parms._response_column = "DSDist"; // Train on the outcome
      parms._distribution = DistributionFamily.quantile;
      parms._quantile_alpha = 0.4;
      parms._sample_rate = 0.6f;
      parms._col_sample_rate = 0.8f;
      parms._col_sample_rate_per_tree = 0.8f;
      parms._seed = 1234;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      pred = parseTestFile("smalldata/gbm_test/ecology_eval.csv" );
      res = gbm.score(pred);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(pred, res, 1e-15));
      Assert.assertEquals( 10.89264, ((ModelMetricsRegression)gbm._output._training_metrics)._mean_residual_deviance,1e-1);

    } finally {
      parms._train.remove();
      if( gbm  != null ) gbm .delete();
      if( pred != null ) pred.remove();
      if( res  != null ) res .remove();
      Scope.exit();
    }
  }

  @Test public void missingAndUnseenValues() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame train=null, test=null, train_preds=null, test_preds=null;
    Scope.enter();
    try {
      {
        CreateFrame cf = new CreateFrame();
        cf.rows = 100;
        cf.cols = 10;
        cf.integer_range = 1000;
        cf.categorical_fraction = 1.0;
        cf.integer_fraction = 0.0;
        cf.binary_fraction = 0.0;
        cf.time_fraction = 0.0;
        cf.string_fraction = 0.0;
        cf.binary_ones_fraction = 0.0;
        cf.missing_fraction = 0.2;
        cf.factors = 3;
        cf.response_factors = 2;
        cf.positive_response = false;
        cf.has_response = true;
        cf.seed = 1235;
        cf.seed_for_column_types = 1234;
        train = cf.execImpl().get();
      }

      {
        CreateFrame cf = new CreateFrame();
        cf.rows = 100;
        cf.cols = 10;
        cf.integer_range = 1000;
        cf.categorical_fraction = 1.0;
        cf.integer_fraction = 0.0;
        cf.binary_fraction = 0.0;
        cf.time_fraction = 0.0;
        cf.string_fraction = 0.0;
        cf.binary_ones_fraction = 0.0;
        cf.missing_fraction = 0.2;
        cf.factors = 3;
        cf.response_factors = 2;
        cf.positive_response = false;
        cf.has_response = true;
        cf.seed = 4321; //different test set
        cf.seed_for_column_types = 1234;
        test = cf.execImpl().get();
      }

      parms._train = train._key;
      parms._response_column = "response"; // Train on the outcome
      parms._distribution = DistributionFamily.multinomial;
      parms._max_depth = 20;
      parms._min_rows = 1;
      parms._ntrees = 5;
      parms._seed = 1;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      train_preds = gbm.score(train);
      test_preds = gbm.score(test);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(train, train_preds, 1e-15));
      Key old = gbm._key;
      gbm._key = Key.make(gbm._key + "ha");
      Assert.assertTrue(gbm.testJavaScoring(test, test_preds, 1e-15));
      DKV.remove(old);

    } finally {
      if( gbm  != null ) gbm .delete();
      if( train != null ) train.remove();
      if( test != null ) test.remove();
      if( train_preds  != null ) train_preds .remove();
      if( test_preds  != null ) test_preds .remove();
      Scope.exit();
    }
  }

  @Test public void minSplitImprovement() {
    Frame tfr = null;
    Key[] ksplits = null;
    GBMModel gbm = null;
    try {
      Scope.enter();
      tfr = parseTestFile("smalldata/covtype/covtype.20k.data");
      int resp = 54;
//      tfr = parseTestFile("bigdata/laptop/mnist/train.csv.gz");
//      int resp = 784;
      Scope.track(tfr.replace(resp, tfr.vecs()[resp].toCategoricalVec()));
      DKV.put(tfr);
      SplitFrame sf = new SplitFrame(tfr, new double[]{0.5, 0.5}, new Key[]{Key.make("train.hex"), Key.make("valid.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;
      double[] msi = new double[]{0, 1e-1};
      final int N = msi.length;
      double[] loglosses = new double[N];
      for (int i = 0; i < N; ++i) {
        // Load data, hack frames
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = ksplits[0];
        parms._valid = ksplits[1];
        parms._response_column = tfr.names()[resp];
        parms._learn_rate = 0.05f;
        parms._min_split_improvement = msi[i];
        parms._ntrees = 10;
        parms._score_tree_interval = parms._ntrees;
        parms._max_depth = 5;

        GBM job = new GBM(parms);
        gbm = job.trainModel().get();
        loglosses[i] = gbm._output._scored_valid[gbm._output._scored_valid.length - 1]._logloss;
        if (gbm!=null) gbm.delete();
      }
      for (int i = 0; i < msi.length; ++i) {
        Log.info("min_split_improvement: " + msi[i] + " -> validation logloss: " + loglosses[i]);
      }
      int idx = ArrayUtils.minIndex(loglosses);
      Log.info("Optimal min_split_improvement: " + msi[idx]);
      assertTrue(0 == idx);
    } finally {
      if (gbm!=null) gbm.delete();
      if (tfr!=null) tfr.delete();
      if (ksplits[0]!=null) ksplits[0].remove();
      if (ksplits[1]!=null) ksplits[1].remove();
      Scope.exit();
    }
  }

  @Test public void histoTypes() {
    Frame tfr = null;
    Key[] ksplits = null;
    GBMModel gbm = null;
    try {
      Scope.enter();
      tfr = parseTestFile("smalldata/covtype/covtype.20k.data");
      int resp = 54;
//      tfr = parseTestFile("bigdata/laptop/mnist/train.csv.gz");
//      int resp = 784;
      Scope.track(tfr.replace(resp, tfr.vecs()[resp].toCategoricalVec()));
      DKV.put(tfr);
      SplitFrame sf = new SplitFrame(tfr, new double[]{0.5, 0.5}, new Key[]{Key.make("train.hex"), Key.make("valid.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;
      SharedTreeModel.SharedTreeParameters.HistogramType[] histoType = SharedTreeModel.SharedTreeParameters.HistogramType.values();
      final int N = histoType.length;
      double[] loglosses = new double[N];
      for (int i = 0; i < N; ++i) {
        // Load data, hack frames
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = ksplits[0];
        parms._valid = ksplits[1];
        parms._response_column = tfr.names()[resp];
        parms._learn_rate = 0.05f;
        parms._histogram_type = histoType[i];
        parms._ntrees = 10;
        parms._score_tree_interval = parms._ntrees;
        parms._max_depth = 5;
        parms._seed = 0xDECAFFEE;

        GBM job = new GBM(parms);
        gbm = job.trainModel().get();
        loglosses[i] = gbm._output._scored_valid[gbm._output._scored_valid.length - 1]._logloss;
        if (gbm!=null) gbm.delete();
      }
      for (int i = 0; i < histoType.length; ++i) {
        Log.info("histoType: " + histoType[i] + " -> validation logloss: " + loglosses[i]);
      }
      int idx = ArrayUtils.minIndex(loglosses);
      Log.info("Optimal randomization: " + histoType[idx]);
      assertTrue(4 == idx);
    } finally {
      if (tfr!=null) tfr.delete();
      if (ksplits[0]!=null) ksplits[0].remove();
      if (ksplits[1]!=null) ksplits[1].remove();
      Scope.exit();
    }
  }

  @Test public void sampleRatePerClass() {
    Frame tfr = null;
    Key[] ksplits = null;
    GBMModel gbm = null;
    try {
      Scope.enter();
      tfr = parseTestFile("smalldata/covtype/covtype.20k.data");
      int resp = 54;
      Scope.track(tfr.replace(resp, tfr.vecs()[resp].toCategoricalVec()));
      DKV.put(tfr);
      SplitFrame sf = new SplitFrame(tfr, new double[]{0.5, 0.5}, new Key[]{Key.make("train.hex"), Key.make("valid.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = ksplits[0];
      parms._valid = ksplits[1];
      parms._response_column = tfr.names()[resp];
      parms._learn_rate = 0.05f;
      parms._min_split_improvement = 1e-5;
      parms._ntrees = 10;
      parms._score_tree_interval = parms._ntrees;
      parms._max_depth = 5;
      parms._sample_rate_per_class = new double[]{0.1f,0.1f,0.2f,0.4f,1f,0.3f,0.2f};

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();
      if (gbm!=null) gbm.delete();
    } finally {
      if (gbm!=null) gbm.delete();
      if (tfr!=null) tfr.delete();
      if (ksplits[0]!=null) ksplits[0].remove();
      if (ksplits[1]!=null) ksplits[1].remove();
      Scope.exit();
    }
  }

  // PUBDEV-2822
  @Test public void testNA() {
    String xy = ",0\n1,0\n2,0\n3,0\n4,-10\n,0";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    assertEquals(0, preds.vec(0).at(0), 1e-6);
    assertEquals(0, preds.vec(0).at(1), 1e-6);
    assertEquals(0, preds.vec(0).at(2), 1e-6);
    assertEquals(0, preds.vec(0).at(3), 1e-6);
    assertEquals(-10, preds.vec(0).at(4), 1e-6);
    assertEquals(0, preds.vec(0).at(5), 1e-6);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testNARight() {
    String xy = ",10\n1,0\n2,0\n3,0\n4,10\n,10";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    assertEquals(10, preds.vec(0).at(0), 0);
    assertEquals(0, preds.vec(0).at(1), 0);
    assertEquals(0, preds.vec(0).at(2), 0);
    assertEquals(0, preds.vec(0).at(3), 0);
    assertEquals(10, preds.vec(0).at(4), 0);
    assertEquals(10, preds.vec(0).at(5), 0);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testNALeft() {
    String xy = ",0\n1,0\n2,0\n3,0\n4,10\n,0";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    assertEquals(0, preds.vec(0).at(0), 1e-6);
    assertEquals(0, preds.vec(0).at(1), 1e-6);
    assertEquals(0, preds.vec(0).at(2), 1e-6);
    assertEquals(0, preds.vec(0).at(3), 1e-6);
    assertEquals(10, preds.vec(0).at(4), 1e-6);
    assertEquals(0, preds.vec(0).at(5), 1e-6);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testNAvsRest() {
    String xy = ",5\n1,0\n2,0\n3,0\n4,0\n,3";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    Assert.assertTrue(Math.abs(preds.vec(0).at(0) - 4) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(1) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(2) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(3) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(4) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(5) - 4) < 1e-6);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testOnevsRest() {
    String xy = "-9,5\n1,0\n2,0\n3,0\n4,0\n-9,3";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    Assert.assertTrue(Math.abs(preds.vec(0).at(0) - 4) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(1) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(2) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(3) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(4) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(5) - 4) < 1e-6);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testNACategorical() {
    String xy = ",0\nA,0\nB,0\nA,0\nD,-10\n,0";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Log.info(df.toTwoDimTable());
    Frame preds = gbm.score(df);
    Log.info(preds.toTwoDimTable());
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    Assert.assertTrue(Math.abs(preds.vec(0).at(0) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(1) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(2) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(3) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(4) - -10) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(5) - 0) < 1e-6);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testNARightCategorical() {
    String xy = ",10\nA,0\nB,0\nA,0\n4,10\n,10";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    Assert.assertTrue(preds.vec(0).at(0) == 10);
    Assert.assertTrue(preds.vec(0).at(1) == 0);
    Assert.assertTrue(preds.vec(0).at(2) == 0);
    Assert.assertTrue(preds.vec(0).at(3) == 0);
    Assert.assertTrue(preds.vec(0).at(4) == 10);
    Assert.assertTrue(preds.vec(0).at(5) == 10);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testNALeftCategorical() {
    String xy = ",0\nA,0\nB,0\nA,0\nD,10\n,0";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    Assert.assertTrue(Math.abs(preds.vec(0).at(0) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(1) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(2) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(3) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(4) - 10) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(5) - 0) < 1e-6);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testNAvsRestCategorical() {
    String xy = ",5\nA,0\nB,0\nA,0\nD,0\n,3";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));
    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Frame preds = gbm.score(df);
    Log.info(df);
    Log.info(preds);
    Assert.assertTrue(gbm.testJavaScoring(df,preds,1e-15));
    Assert.assertTrue(Math.abs(preds.vec(0).at(0) - 4) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(1) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(2) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(3) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(4) - 0) < 1e-6);
    Assert.assertTrue(Math.abs(preds.vec(0).at(5) - 4) < 1e-6);
    preds.remove();
    gbm.remove();
    df.remove();
  }

  // PUBDEV-2822
  @Test public void testUnseenNACategorical() {
    try {
      Scope.enter();
      String trainData = "B,-5\nA,0\nB,0\nA,0\nD,0\nA,3";
      Frame train = ParseDataset.parse(Key.make("train"), makeByteVec(Key.make("trainBytes"), trainData));
      Scope.track(train);

      String testData = ",5\n,0\nB,0\n,0\nE,0\n,3";
      Frame test = ParseDataset.parse(Key.make("test"), makeByteVec(Key.make("testBytes"), testData));
      Scope.track(test);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "C2";
      parms._min_rows = 1;
      parms._learn_rate = 1;
      parms._ntrees = 1;
      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();
      Scope.track_generic(gbm);

      Frame trainPreds = gbm.score(train);
      Scope.track(trainPreds);
      Log.info(train);
      Log.info(trainPreds);

      Frame testPreds = gbm.score(test);
      Scope.track(testPreds);
      Log.info(test);
      Log.info(testPreds);

      Assert.assertTrue(gbm.testJavaScoring(train, trainPreds, 1e-15));
      Model.JavaScoringOptions options = new Model.JavaScoringOptions();
      options._disable_pojo = !Boolean.getBoolean("reproduce.PUBDEV-8263"); // FIXME - doesn't work unless POJO is disabled
      Assert.assertTrue(gbm.testJavaScoring(test, testPreds, 1e-15, options));
      Assert.assertTrue(Math.abs(trainPreds.vec(0).at(0) - -2.5) < 1e-6);
      Assert.assertTrue(Math.abs(trainPreds.vec(0).at(1) - 1) < 1e-6);
      Assert.assertTrue(Math.abs(trainPreds.vec(0).at(2) - -2.5) < 1e-6);
      Assert.assertTrue(Math.abs(trainPreds.vec(0).at(3) - 1) < 1e-6);
      Assert.assertTrue(Math.abs(trainPreds.vec(0).at(4) - 0) < 1e-6);
      Assert.assertTrue(Math.abs(trainPreds.vec(0).at(5) - 1) < 1e-6);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUnseenCategoricalWithNAvsRestSplit() {
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withColNames("CatFeature", "Response")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, new String[]{null, "A"})
              .withDataForCol(1, new double[]{0.0, 1.0})
              .build();

      Frame test = new TestFrameBuilder()
              .withColNames("CatFeature")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, new String[]{null, "B"})
              .build();

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "Response";
      parms._min_rows = 1;
      parms._learn_rate = 1;
      parms._ntrees = 1;

      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();
      Scope.track_generic(gbm);

      assertTrue(gbm.getSharedTreeSubgraph(0, 0).rootNode.isNaVsRest());
      
      Frame scoredTrain = gbm.score(train);
      Scope.track(scoredTrain);
      assertTrue(gbm.testJavaScoring(train, scoredTrain, 1e-8));

      Frame scoredTest = gbm.score(test);
      Scope.track(scoredTest);
      assertTrue(gbm.testJavaScoring(test, scoredTest, 1e-8));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUnseenCategoricalSplit() {
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withColNames("CatFeature", "Response")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, new String[]{"A", "B"})
              .withDataForCol(1, new double[]{0.0, 1.0})
              .build();

      Frame test = new TestFrameBuilder()
              .withColNames("CatFeature")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, new String[]{null, "B"})
              .build();

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "Response";
      parms._min_rows = 1;
      parms._learn_rate = 1;
      parms._ntrees = 1;

      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();
      Scope.track_generic(gbm);

      assertTrue(gbm.getSharedTreeSubgraph(0, 0).rootNode.isBitset());

      Frame scoredTrain = gbm.score(train);
      Scope.track(scoredTrain);
      assertTrue(gbm.testJavaScoring(train, scoredTrain, 1e-8));

      Frame scoredTest = gbm.score(test);
      Scope.track(scoredTest);
      assertTrue(gbm.testJavaScoring(test, scoredTest, 1e-8));
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUnseenCategoricalSplitLeftward() {
    try {
      Scope.enter();
      Frame train = new TestFrameBuilder()
              .withColNames("CatFeature", "Response")
              .withVecTypes(Vec.T_CAT, Vec.T_NUM)
              .withDataForCol(0, new String[]{"A", "B", null})
              .withDataForCol(1, new double[]{0.0, 1.0, 0.0})
              .build();

      Frame test = new TestFrameBuilder()
              .withColNames("CatFeature")
              .withVecTypes(Vec.T_CAT)
              .withDataForCol(0, new String[]{null, "B"})
              .build();

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "Response";
      parms._min_rows = 1;
      parms._learn_rate = 1;
      parms._ntrees = 1;

      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();
      Scope.track_generic(gbm);

      assertTrue(gbm.getSharedTreeSubgraph(0, 0).rootNode.isBitset());
      assertTrue(gbm.getSharedTreeSubgraph(0, 0).rootNode.isLeftward());

      Frame scoredTrain = gbm.score(train);
      Scope.track(scoredTrain);
      assertTrue(gbm.testJavaScoring(train, scoredTrain, 1e-8));

      Frame scoredTest = gbm.score(test);
      Scope.track(scoredTest);
      assertTrue(gbm.testJavaScoring(test, scoredTest, 1e-8));
    } finally {
      Scope.exit();
    }
  }
  
  @Test public void unseenMissing() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame train=null, test=null, train_preds=null, test_preds=null;
    Scope.enter();
    try {
      {
        CreateFrame cf = new CreateFrame();
        cf.rows = 100;
        cf.cols = 10;
        cf.integer_range = 1000;
        cf.categorical_fraction = 1.0;
        cf.integer_fraction = 0.0;
        cf.binary_fraction = 0.0;
        cf.time_fraction = 0.0;
        cf.string_fraction = 0.0;
        cf.binary_ones_fraction = 0.0;
        cf.missing_fraction = 0.0;
        cf.factors = 3;
        cf.response_factors = 2;
        cf.positive_response = false;
        cf.has_response = true;
        cf.seed = 1235;
        cf.seed_for_column_types = 1234;
        train = cf.execImpl().get();
      }

      {
        CreateFrame cf = new CreateFrame();
        cf.rows = 100;
        cf.cols = 10;
        cf.integer_range = 1000;
        cf.categorical_fraction = 1.0;
        cf.integer_fraction = 0.0;
        cf.binary_fraction = 0.0;
        cf.time_fraction = 0.0;
        cf.string_fraction = 0.0;
        cf.binary_ones_fraction = 0.0;
        cf.missing_fraction = 0.8;
        cf.factors = 3;
        cf.response_factors = 2;
        cf.positive_response = false;
        cf.has_response = true;
        cf.seed = 4321; //different test set
        cf.seed_for_column_types = 1234;
        test = cf.execImpl().get();
      }

      parms._train = train._key;
      parms._response_column = "response"; // Train on the outcome
      parms._distribution = DistributionFamily.multinomial;
      parms._max_depth = 20;
      parms._min_rows = 1;
      parms._ntrees = 5;
      parms._seed = 1;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      train_preds = gbm.score(train);
      test_preds = gbm.score(test);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(train, train_preds, 1e-15));
      Key old = gbm._key;
      gbm._key = Key.make(gbm._key + "ha");
      Assert.assertTrue(gbm.testJavaScoring(test, test_preds, 1e-15));
      DKV.remove(old);

    } finally {
      if( gbm  != null ) gbm .delete();
      if( train != null ) train.remove();
      if( test != null ) test.remove();
      if( train_preds  != null ) train_preds .remove();
      if( test_preds  != null ) test_preds .remove();
      Scope.exit();
    }
  }

  //PUBDEV-3066
  @Test public void testAnnealingStop() {
    Frame tfr=null;
    final int N = 1;

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parseTestFile("./smalldata/airlines/allyears2k_headers.zip");
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      for (int i=0; i<N; ++i) {
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = "IsDepDelayed";
        parms._nbins = 10;
        parms._nbins_cats = 500;
        parms._ntrees = 100;
        parms._learn_rate_annealing = 0.5;
        parms._max_depth = 5;
        parms._min_rows = 10;
        parms._distribution = DistributionFamily.bernoulli;
        parms._balance_classes = true;
        parms._seed = 0;

        // Build a first model; all remaining models should be equal
        GBMModel gbm = new GBM(parms).trainModel().get();
        Assert.assertNotEquals(gbm._output._ntrees, parms._ntrees);
        gbm.delete();
      }
    } finally {
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
  }

  @Ignore
  public void testModifiedHuber() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parseTestFile("./smalldata/airlines/allyears2k_headers.zip");
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = "IsDepDelayed";
      parms._seed = 1234;
      parms._distribution = DistributionFamily.modified_huber;
      parms._min_rows = 1;
      parms._learn_rate = .1;
      parms._max_depth = 5;
      parms._ntrees = 10;

      // Build a first model; all remaining models should be equal
      gbm = new GBM(parms).trainModel().get();

      Frame train_preds = gbm.score(tfr);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(tfr, train_preds, 1e-15));
      train_preds.remove();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
//      assertEquals(0.59998, mm.auc_obj()._auc, 1e-4); // 1 node
//      assertEquals(0.31692, mm.mse(), 1e-4);
//      assertEquals(0.79069, mm.logloss(), 1e-4);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) {
        gbm.deleteCrossValidationModels();
        gbm.delete();
      }
      Scope.exit();
    }
  }

  @Ignore
  public void testModifiedHuberStability() {
    String xy = "A,Y\nB,N\nA,N\nB,N\nA,Y\nA,Y";
    Key tr = Key.make("train");
    Frame df = ParseDataset.parse(tr, makeByteVec(Key.make("xy"), xy));

    String test = "A,Y\nB,N\nA,N\nB,N\nA,Y\nA,Y";
    Key te = Key.make("test");
    Frame df2 = ParseDataset.parse(te, makeByteVec(Key.make("te"), test));

    GBMModel.GBMParameters parms = makeGBMParameters();
    parms._train = tr;
    parms._response_column = "C2";
    parms._min_rows = 1;
    parms._learn_rate = 1;
    parms._distribution = DistributionFamily.modified_huber;
    parms._ntrees = 1;
    GBM job = new GBM(parms);
    GBMModel gbm = job.trainModel().get();
    Scope.enter(); //AdaptTestTrain leaks when it does inplace Vec adaptation, need a Scope to catch that stuff
    Frame preds = gbm.score(df);
    Frame preds2 = gbm.score(df2);
    Log.info(df);
    Log.info(preds);
    Log.info(df2);
    Log.info(preds2);
    Assert.assertTrue(gbm.testJavaScoring(df, preds, 1e-15));
    Assert.assertTrue(gbm.testJavaScoring(df2, preds2, 1e-15));
//    Assert.assertTrue(Math.abs(preds.vec(0).at(0) - -2.5) < 1e-6);
//    Assert.assertTrue(Math.abs(preds.vec(0).at(1) - 1) < 1e-6);
//    Assert.assertTrue(Math.abs(preds.vec(0).at(2) - -2.5) < 1e-6);
//    Assert.assertTrue(Math.abs(preds.vec(0).at(3) - 1) < 1e-6);
//    Assert.assertTrue(Math.abs(preds.vec(0).at(4) - 0) < 1e-6);
//    Assert.assertTrue(Math.abs(preds.vec(0).at(5) - 1) < 1e-6);
    preds.remove();
    preds2.remove();
    gbm.remove();
    df.remove();
    df2.remove();
    Scope.exit();
  }

  @Test public void testHuber2() {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame pred=null, res=null;
    Scope.enter();
    try {
      Frame train = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      train.remove("Site").remove();     // Remove unique ID
      train.remove("Method").remove();   // Remove categorical
      DKV.put(train);                    // Update frame after hacking it
      parms._train = train._key;
      parms._response_column = "DSDist"; // Train on the outcome
      parms._distribution = huber;
      parms._huber_alpha = 0.5;
      parms._sample_rate = 0.6f;
      parms._col_sample_rate = 0.8f;
      parms._col_sample_rate_per_tree = 0.8f;
      parms._seed = 1234;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      pred = parseTestFile("smalldata/gbm_test/ecology_eval.csv" );
      res = gbm.score(pred);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(pred, res, 1e-15));
      Assert.assertEquals( 1485, ((ModelMetricsRegression)gbm._output._training_metrics)._MSE,50);
      Assert.assertEquals(289, ((ModelMetricsRegression)gbm._output._training_metrics)._mean_residual_deviance, 1);

    } finally {
      parms._train.remove();
      if( gbm  != null ) gbm .delete();
      if( pred != null ) pred.remove();
      if( res  != null ) res .remove();
      Scope.exit();
    }
  }

  @Test
  public void testLaplace() {
    Frame tfr = null;
    GBMModel gbm = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._seed = 0xdecaf;
      parms._distribution = laplace;

      gbm = new GBM(parms).trainModel().get();

      Assert.assertEquals(8.05716257,((ModelMetricsRegression)gbm._output._training_metrics)._MSE,1e-5);
      Assert.assertEquals(1.42298/*MAE*/,((ModelMetricsRegression)gbm._output._training_metrics)._mean_residual_deviance,1e-5);

    } finally {
      if (tfr != null) tfr.delete();
      if (gbm != null) gbm.deleteCrossValidationModels();
      if (gbm != null) gbm.delete();
    }
  }

  @Test
  public void testGaussian() {
    Frame tfr = null;
    GBMModel gbm = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._seed = 0xdecaf;
      parms._distribution = gaussian;

      gbm = new GBM(parms).trainModel().get();

      if ("Default".equals(test_type)) {
        Assert.assertEquals(2.9423857564, ((ModelMetricsRegression) gbm._output._training_metrics)._MSE, 1e-5);
        Assert.assertEquals(2.9423857564, ((ModelMetricsRegression) gbm._output._training_metrics)._mean_residual_deviance, 1e-5);
      } else if ("EmulateConstraints".equals(test_type)) {
        // This demonstrates the artificially constrained models are slightly different
        // This is because we directly re-use the split predictions instead using values in Gamma Pass
        // also see Split#splat
        Assert.assertEquals(2.9422145249, ((ModelMetricsRegression) gbm._output._training_metrics)._MSE, 1e-5);
        Assert.assertEquals(2.9422145249, ((ModelMetricsRegression) gbm._output._training_metrics)._mean_residual_deviance, 1e-5);
      } else {
        fail();
      }

    } finally {
      if (tfr != null) tfr.delete();
      if (gbm != null) gbm.deleteCrossValidationModels();
      if (gbm != null) gbm.delete();
    }
  }

  @Test
  public void testHuberDeltaLarge() {
    Frame tfr = null;
    GBMModel gbm = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._seed = 0xdecaf;
      parms._distribution = huber;
      parms._huber_alpha = 1; // nothing is an outlier - same as gaussian

      gbm = new GBM(parms).trainModel().get();

      Assert.assertEquals(2.9423857564,((ModelMetricsRegression) gbm._output._training_metrics)._MSE,1e-2);
      // huber loss with delta -> max(error) goes to MSE
      Assert.assertEquals(2.9423857564,((ModelMetricsRegression) gbm._output._training_metrics)._mean_residual_deviance,1e-2);

    } finally {
      if (tfr != null) tfr.delete();
      if (gbm != null) gbm.deleteCrossValidationModels();
      if (gbm != null) gbm.delete();
    }
  }

  @Test
  public void testHuberDeltaTiny() {
    Frame tfr = null;
    GBMModel gbm = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._seed = 0xdecaf;
      parms._distribution = huber;
      parms._huber_alpha = 1e-2; //everything is an outlier and we should get laplace loss
      gbm = new GBM(parms).trainModel().get();
      Assert.assertEquals(8.05716257,((ModelMetricsRegression)gbm._output._training_metrics)._MSE,0.3);

      // Huber loss can be derived from MAE since no obs weights
      double delta = 0.0047234; //hardcoded from output
      double MAE = 1.42298; //see laplace above
      Assert.assertEquals((2*MAE-delta)*delta,((ModelMetricsRegression)gbm._output._training_metrics)._mean_residual_deviance,1e-1);

    } finally {
      if (tfr != null) tfr.delete();
      if (gbm != null) gbm.deleteCrossValidationModels();
      if (gbm != null) gbm.delete();
    }
  }

  @Test
  public void testHuber() {
    Frame tfr = null;
    GBMModel gbm = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._seed = 0xdecaf;
      parms._distribution = huber;
      parms._huber_alpha = 0.9; //that's the default

      gbm = new GBM(parms).trainModel().get();

      Assert.assertEquals(4.447062185,((ModelMetricsRegression)gbm._output._training_metrics)._MSE,1e-5);
      Assert.assertEquals(2.488248962,((ModelMetricsRegression) gbm._output._training_metrics)._mean_residual_deviance,1e-4);

    } finally {
      if (tfr != null) tfr.delete();
      if (gbm != null) gbm.deleteCrossValidationModels();
      if (gbm != null) gbm.delete();
    }
  }

  @Test
  public void testHuberNoise() {
    Frame tfr = null;
    GBMModel gbm = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._seed = 0xdecaf;
      parms._distribution = huber;
      parms._huber_alpha = 0.9; //that's the default
      parms._pred_noise_bandwidth = 0.2;

      gbm = new GBM(parms).trainModel().get();

      Assert.assertEquals(4.8056900203,((ModelMetricsRegression)gbm._output._training_metrics)._MSE,1e-5);
      Assert.assertEquals(2.5683696486,((ModelMetricsRegression) gbm._output._training_metrics)._mean_residual_deviance,1e-4);

    } finally {
      if (tfr != null) tfr.delete();
      if (gbm != null) gbm.deleteCrossValidationModels();
      if (gbm != null) gbm.delete();
    }
  }

  @Test
  public void testDeviances() {
    for (DistributionFamily dist : DistributionFamily.values()) {
      if (dist == modified_huber || dist == quasibinomial || dist == ordinal || dist == custom ||
              dist == negativebinomial || dist == fractionalbinomial)
        continue;
      Frame tfr = null;
      Frame res = null;
      Frame preds = null;
      GBMModel gbm = null;

      try {
        tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        String resp = tfr.lastVecName();
        if (dist==modified_huber || dist==bernoulli || dist==multinomial) {
          resp = dist==multinomial?"rad":"chas";
          Vec v = tfr.remove(resp);
          tfr.add(resp, v.toCategoricalVec());
          v.remove();
          DKV.put(tfr);
        }
        parms._response_column = resp;
        parms._distribution = dist;

        gbm = new GBM(parms).trainModel().get();
        preds = gbm.score(tfr);

        res = gbm.computeDeviances(tfr,preds,"myDeviances");
        double meanDeviance = res.anyVec().mean();
        if (gbm._output.nclasses()==2)
          Assert.assertEquals(meanDeviance,((ModelMetricsBinomial) gbm._output._training_metrics)._logloss,1e-6*Math.abs(meanDeviance));
        else if (gbm._output.nclasses()>2)
          Assert.assertEquals(meanDeviance,((ModelMetricsMultinomial) gbm._output._training_metrics)._logloss,1e-6*Math.abs(meanDeviance));
        else
          Assert.assertEquals(meanDeviance,((ModelMetricsRegression) gbm._output._training_metrics)._mean_residual_deviance,1e-6*Math.abs(meanDeviance));

      } finally {
        if (tfr != null) tfr.delete();
        if (res != null) res.delete();
        if (preds != null) preds.delete();
        if (gbm != null) gbm.delete();
      }
    }
  }

  @Test
  public void testCatEncoding() {
    for (Model.Parameters.CategoricalEncodingScheme c : Model.Parameters.CategoricalEncodingScheme.values()) {
      if (c != Model.Parameters.CategoricalEncodingScheme.AUTO) continue;
      Frame tfr = null;
      GBMModel gbm = null;
      Frame fr2 = null;

      try {
        tfr = parseTestFile("./smalldata/junit/weather.csv");
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = tfr.lastVecName();
        parms._ntrees = 5;
        parms._categorical_encoding = c;
        gbm = new GBM(parms).trainModel().get();
        // Done building model; produce a score column with predictions
        fr2 = gbm.score(tfr);

        // Build a POJO, validate same results
        Assert.assertTrue(gbm.testJavaScoring(tfr,fr2,1e-15));
      } finally {
        if (tfr != null) tfr.delete();
        if (fr2 != null) fr2.delete();
        if (gbm != null) gbm.deleteCrossValidationModels();
        if (gbm != null) gbm.delete();
      }
    }
  }

  @Test
  public void testCatEncodingCV() {
    for (Model.Parameters.CategoricalEncodingScheme c : Model.Parameters.CategoricalEncodingScheme.values()) {
      if (c != Model.Parameters.CategoricalEncodingScheme.AUTO) continue;
      Frame tfr = null;
      GBMModel gbm = null;

      try {
        tfr = parseTestFile("./smalldata/junit/weather.csv");
        GBMModel.GBMParameters parms = makeGBMParameters();
        parms._train = tfr._key;
        parms._response_column = tfr.lastVecName();
        parms._ntrees = 5;
        parms._categorical_encoding = c;
        parms._nfolds = 3;
        gbm = new GBM(parms).trainModel().get();
      } finally {
        if (tfr != null) tfr.delete();
        if (gbm != null) gbm.deleteCrossValidationModels();
        if (gbm != null) gbm.delete();
      }
    }
  }

  // A test of the validity of categorical splits
  @Test public void testCategoricalSplits() throws FileNotFoundException {
    Frame fr=null;
    GBMModel model = null;
    Scope.enter();
    try {
      GBMModel.GBMParameters parms = makeGBMParameters();
      fr = parseTestFile("smalldata/gbm_test/ecology_model.csv");
      fr.remove("Site").remove();
      fr.remove("SegSumT").remove();
      fr.remove("SegTSeas").remove();
      fr.remove("SegLowFlow").remove();
      fr.remove("DSDist").remove();
      fr.remove("DSMaxSlope").remove();
      fr.remove("USAvgT").remove();
      fr.remove("USRainDays").remove();
      fr.remove("USSlope").remove();
//      fr.remove("USNative").remove();
      fr.remove("DSDam").remove();
//      fr.remove("LocSed").remove();

      fr.remove("Method").remove();
      int ci = fr.find("Angaus");
      Scope.track(fr.replace(ci, fr.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(fr);
      parms._train = fr._key;
      parms._response_column = "Angaus";
      parms._ntrees = 1;
      parms._min_rows = 10;
      parms._max_depth = 13;
      parms._distribution = DistributionFamily.multinomial;
      model = new GBM(parms).trainModel().get();

//      StreamingSchema ss = new StreamingSchema(model.getMojo(), "model.zip");
//      FileOutputStream fos = new FileOutputStream("model.zip");
//      ss.getStreamWriter().writeTo(fos);
    } finally {
      if( model != null ) model.delete();
      if( fr  != null )   fr.remove();
      Scope.exit();
    }
  }

  // A test of the validity of categorical splits
  @Test public void testCategoricalSplits2() throws FileNotFoundException {
    Frame fr=null;
    GBMModel model = null;
    Scope.enter();
    try {
      GBMModel.GBMParameters parms = makeGBMParameters();
      fr = parseTestFile("smalldata/airlines/allyears2k_headers.zip");

      Frame fr2 = new Frame(Key.<Frame>make(), new String[]{"C","R"}, new Vec[]{fr.vec("Origin"),fr.vec("IsDepDelayed")});
      int ci = fr2.find("R");
      Scope.track(fr2.replace(ci, fr2.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(fr2);
      parms._train = fr2._key;
      parms._response_column = "R";
      parms._ntrees = 1;
      parms._min_rows = 1000;
      parms._max_depth = 4;
      parms._distribution = DistributionFamily.bernoulli;
      model = new GBM(parms).trainModel().get();
      DKV.remove(fr2._key);

//      StreamingSchema ss = new StreamingSchema(model.getMojo(), "model.zip");
//      FileOutputStream fos = new FileOutputStream("model.zip");
//      ss.getStreamWriter().writeTo(fos);
    } finally {
      if( model != null ) model.delete();
      if( fr  != null )   fr.remove();
      Scope.exit();
    }
  }

  @Test public void highCardinalityLowNbinsCats() { highCardinality(2000); }
  @Test public void highCardinalityHighNbinsCats() { highCardinality(6000); }

  public void highCardinality(int nbins_cats) {
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame train=null, test=null, train_preds=null, test_preds=null;
    Scope.enter();
    try {
      {
        CreateFrame cf = new CreateFrame();
        cf.rows = 10000;
        cf.cols = 10;
        cf.integer_range = 1000;
        cf.categorical_fraction = 1.0;
        cf.integer_fraction = 0.0;
        cf.binary_fraction = 0.0;
        cf.time_fraction = 0.0;
        cf.string_fraction = 0.0;
        cf.binary_ones_fraction = 0.0;
        cf.missing_fraction = 0.2;
        cf.factors = 3000;
        cf.response_factors = 2;
        cf.positive_response = false;
        cf.has_response = true;
        cf.seed = 1235;
        cf.seed_for_column_types = 1234;
        train = cf.execImpl().get();
      }

      {
        CreateFrame cf = new CreateFrame();
        cf.rows = 10000;
        cf.cols = 10;
        cf.integer_range = 1000;
        cf.categorical_fraction = 1.0;
        cf.integer_fraction = 0.0;
        cf.binary_fraction = 0.0;
        cf.time_fraction = 0.0;
        cf.string_fraction = 0.0;
        cf.binary_ones_fraction = 0.0;
        cf.missing_fraction = 0.2;
        cf.factors = 5000;
        cf.response_factors = 2;
        cf.positive_response = false;
        cf.has_response = true;
        cf.seed = 5321;
        cf.seed_for_column_types = 1234;
        test = cf.execImpl().get();
      }

      parms._train = train._key;
      parms._response_column = "response"; // Train on the outcome
      parms._max_depth = 20; //allow it to overfit
      parms._min_rows = 1;
      parms._ntrees = 1;
      parms._nbins_cats = nbins_cats;
      parms._seed = 0x2834234;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      train_preds = gbm.score(train);

      test_preds = gbm.score(test);

      new MRTask() {
        public void map(Chunk c) {
          for (int i=0;i<c._len;++i)
            if (c.isNA(i))
              c.set(i, 0.5);
        }
      }.doAll(train.vec("response"));

      new MRTask() {
        public void map(Chunk c) {
          for (int i=0;i<c._len;++i)
            if (c.isNA(i))
              c.set(i, 0.5);
        }
      }.doAll(test.vec("response"));

      Log.info("Train AUC: " + ModelMetricsBinomial.make(train_preds.vec(2), train.vec("response")).auc());
      Log.info("Test AUC: " + ModelMetricsBinomial.make(test_preds.vec(2), test.vec("response")).auc());

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(train, train_preds, 1e-15));
      Key old = gbm._key;
      gbm._key = Key.make(gbm._key + "ha");
      Assert.assertTrue(gbm.testJavaScoring(test, test_preds, 1e-15));
      DKV.remove(old);

    } finally {
      if( gbm  != null ) gbm .delete();
      if( train != null ) train.remove();
      if( test != null ) test.remove();
      if( train_preds  != null ) train_preds .remove();
      if( test_preds  != null ) test_preds .remove();
      Scope.exit();
    }
  }

  @Test public void lowCardinality() throws IOException {
    for (boolean sort_cats : new boolean[]{true, false}) {
      int[] vals = new int[]{2,10,20,25,26,27,100};
      double[] maes = new double[vals.length];
      int i=0;
      for (int nbins_cats : vals) {
        GBMModel model = null;
        GBMModel.GBMParameters parms = makeGBMParameters();
        Frame train, train_preds=null;
        Scope.enter();
        train = parseTestFile("smalldata/gbm_test/alphabet_cattest.csv");
        try {
          parms._train = train._key;
          parms._response_column = "y"; // Train on the outcome
          parms._max_depth = 2;
          parms._min_rows = 1;
          parms._ntrees = 1;
          parms._learn_rate = 1;
          parms._nbins_cats = nbins_cats;
          if (sort_cats)
            parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.SortByResponse;

          GBM job = new GBM(parms);
          model = job.trainModel().get();
          StreamingSchema ss = new StreamingSchema(model.getMojo(), "model.zip");
          FileOutputStream fos = new FileOutputStream("model.zip");
          ss.getStreamWriter().writeTo(fos);

          train_preds = model.score(train);
          Assert.assertTrue(model.testJavaScoring(train, train_preds, 1e-15));

          double mae = ModelMetricsRegression.make(train_preds.vec(0), train.vec("y"), gaussian).mae();
          Log.info("Train MAE: " + mae);
          maes[i++] = mae;
          if (nbins_cats >= 25 || sort_cats)
            Assert.assertEquals(0, mae, 1e-8); // sorting of categoricals is enough
          else
            Assert.assertTrue(mae > 0);
        } finally {
          if( model != null ) model.delete();
          if( train != null ) train.remove();
          if( train_preds  != null ) train_preds .remove();
          new File("model.zip").delete();
          Scope.exit();
        }
      }
      Log.info(Arrays.toString(vals));
      Log.info(Arrays.toString(maes));
    }
  }

  @Test
  public void RegressionCars() {
    Frame tfr = null;
    Frame trainFrame = null;
    Frame testFrame = null;
    Frame preds = null;
    GBMModel model = null;
    Scope.enter();
    try {
      // Parse frame into H2O
      tfr = parseTestFile("./smalldata/junit/cars.csv");
      DKV.put(tfr);

      // split into train/test
      SplitFrame sf = new SplitFrame(tfr, new double[] { 0.7, 0.3 }, null);
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      trainFrame = (Frame)ksplits[0].get();
      testFrame = (Frame)ksplits[1].get();

      // define special columns
//      String response = "cylinders"; // passes
      String response = "economy (mpg)"; //fails

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = trainFrame._key;
      parms._valid = testFrame._key;
      parms._response_column = response;
      parms._ignored_columns = new String[]{"name"};
//      parms._dmatrix_type = GBMModel.GBMParameters.DMatrixType.dense;
//      parms._backend = GBMModel.GBMParameters.Backend.cpu;
//      parms._tree_method = GBMModel.GBMParameters.TreeMethod.exact;

      model = new hex.tree.gbm.GBM(parms).trainModel().get();
      Log.info(model);

      preds = model.score(testFrame);
      Assert.assertTrue(model.testJavaScoring(testFrame, preds, 1e-6));
      Assert.assertEquals(
          ((ModelMetricsRegression)model._output._validation_metrics).mae(),
          ModelMetricsRegression.make(preds.anyVec(), testFrame.vec(response), DistributionFamily.gaussian).mae(),
          1e-5
      );
      Assert.assertTrue(preds.anyVec().sigma() > 0);

    } finally {
      Scope.exit();
      if (trainFrame!=null) trainFrame.remove();
      if (testFrame!=null) testFrame.remove();
      if (tfr!=null) tfr.remove();
      if (preds!=null) preds.remove();
      if (model!=null) {
        model.delete();
      }
    }
  }

  @Test
  public void testCustomEarlyStoppingValidation() {
    try {
      Scope.enter();
      Frame training = Scope.track(parseTestFile("./smalldata/junit/cars.csv"));
      String response = "economy (mpg)";

      ScoreKeeper.StoppingMetric[] customStoppingMetrics = new ScoreKeeper.StoppingMetric[]{
              ScoreKeeper.StoppingMetric.custom, ScoreKeeper.StoppingMetric.custom_increasing
      };
      for (ScoreKeeper.StoppingMetric stoppingMetric : customStoppingMetrics) {
        GBMModel model = null;
        try {
          GBMModel.GBMParameters parms = makeGBMParameters();
          parms._train = training._key;
          parms._response_column = response;
          parms._ignored_columns = new String[]{"name"};
          parms._stopping_rounds = 2;
          parms._stopping_metric = stoppingMetric;

          model = new hex.tree.gbm.GBM(parms).trainModel().get();
          fail("Custom stopping " + " shouldn't work without a custom metric");
        } catch (H2OModelBuilderIllegalArgumentException e) {
          if (e.getMessage() == null || !e.getMessage().contains("ERRR on field: _custom_metric_func: " +
                  "Custom metric function needs to be defined in order to use it for early stopping.")) {
            throw e;
          }
          // suppress the expected exception
        } finally {
          if (model != null) {
            model.delete();
          }
        }
      }
    } finally {
      Scope.exit();
    }
  }

  // PUBDEV-3482
  @Test public void testQuasibinomial(){
    Scope.enter();
    // test it behaves like binomial on binary data
    GBMModel model=null, model2=null, model3=null, model4=null, model5=null;
    Frame fr = parseTestFile("smalldata/glm_test/prostate_cat_replaced.csv");
    // turn numeric response 0/1 into a categorical factor
    Frame preds=null, preds2=null, preds3=null, preds4=null, preds5=null;
    Vec r = fr.vec("CAPSULE").toCategoricalVec();
    fr.remove("CAPSULE").remove();
    fr.add("CAPSULE", r);
    DKV.put(fr);

    // same dataset, but keep numeric response 0/1
    Frame fr2 = parseTestFile("smalldata/glm_test/prostate_cat_replaced.csv");

    // same dataset, but make numeric response 0/2, can only be handled by quasibinomial
    Frame fr3 = parseTestFile("smalldata/glm_test/prostate_cat_replaced.csv");
    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i=0;i<cs[0]._len;++i) {
          cs[1].set(i, cs[1].at8(i) == 1 ? 2 : 0);
        }
      }
    }.doAll(fr3);

    // same dataset, but make numeric response -1/2, can only be handled by quasibinomial
    Frame fr4 = parseTestFile("smalldata/glm_test/prostate_cat_replaced.csv");
    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i=0;i<cs[0]._len;++i) {
          cs[1].set(i, cs[1].at8(i) == 1 ? 2 : -1);
        }
      }
    }.doAll(fr4);

    // same dataset, but make numeric response 0/2.2, can only be handled by quasibinomial
    Frame fr5 = parseTestFile("smalldata/glm_test/prostate_cat_replaced.csv");
    new MRTask() {
      @Override
      public void map(Chunk[] cs) {
        for (int i=0;i<cs[0]._len;++i) {
          cs[1].set(i, cs[1].at8(i) == 1 ? 2.2 : 2.4);
        }
      }
    }.doAll(fr5);

    try {
      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._seed = 5;
      params._ntrees = 100;
      params._nfolds = 3;
      params._learn_rate = 0.01;
      params._min_rows = 1;
      params._min_split_improvement = 0;
      params._stopping_rounds = 10;
      params._stopping_tolerance = 0;
      params._score_tree_interval = 10;

      // binomial - categorical response, optimize logloss
      params._train = fr._key;
      params._distribution = DistributionFamily.bernoulli;
      GBM gbm = new GBM(params);
      model = gbm.trainModel().get();
      preds = model.score(fr);

      // quasibinomial - numeric response 0/1, minimize deviance (negative log-likelihood)
      params._distribution = DistributionFamily.quasibinomial;
      params._train = fr2._key;
      GBM gbm2 = new GBM(params);
      model2 = gbm2.trainModel().get();
      preds2 = model2.score(fr2);

      // quasibinomial - numeric response 0/2, minimize deviance (negative log-likelihood)
      params._distribution = DistributionFamily.quasibinomial;
      params._train = fr3._key;
      GBM gbm3 = new GBM(params);
      model3 = gbm3.trainModel().get();
      preds3 = model3.score(fr3);

      // quasibinomial - numeric response -1/2, minimize deviance (negative log-likelihood)
      params._distribution = DistributionFamily.quasibinomial;
      params._train = fr4._key;
      GBM gbm4 = new GBM(params);
      model4 = gbm4.trainModel().get();
      preds4 = model4.score(fr4);

      // quasibinomial - numeric response 0/2.2, minimize deviance (negative log-likelihood)
      params._distribution = DistributionFamily.quasibinomial;
      params._train = fr5._key;
      GBM gbm5 = new GBM(params);
      model5 = gbm5.trainModel().get();
      preds5 = model5.score(fr5);

      // Done building model; produce a score column with predictions
      if (preds!=null)
        Log.info(preds.toTwoDimTable());
      if (preds2!=null)
        Log.info(preds2.toTwoDimTable());
      if (preds3!=null)
        Log.info(preds3.toTwoDimTable());
      if (preds4!=null)
        Log.info(preds4.toTwoDimTable());
      if (preds5!=null)
        Log.info(preds5.toTwoDimTable());
      
      if (model!=null && model2!=null) {
        System.out.println("Compare training metrics of both distributions.");
        assertEquals(
                ((ModelMetricsBinomial) model._output._training_metrics).logloss(),
                ((ModelMetricsBinomial) model2._output._training_metrics).logloss(), 2e-3);
        
        System.out.println("Compare CV metrics of both distributions.");
        assertEquals(
            ((ModelMetricsBinomial) model._output._cross_validation_metrics).logloss(),
            ((ModelMetricsBinomial) model2._output._cross_validation_metrics).logloss(), 1e-3);
      }
      
      // Build a POJO/MOJO, validate same results
      if (model2!=null)
        System.out.println("Build a POJO/MOJO, validate same results - model2");
        
      if (model3!=null)
        System.out.println("Build a POJO/MOJO, validate same results - model3");
        Assert.assertTrue(model3.testJavaScoring(fr3,preds3,1e-15));

      if (model4!=null)
        System.out.println("Build a POJO/MOJO, validate same results - model4");
        Assert.assertTrue(model4.testJavaScoring(fr4,preds4,1e-15));

      if (model5!=null)
        System.out.println("Build a POJO/MOJO, validate same results - model5");
      Assert.assertTrue(model5.testJavaScoring(fr5,preds5,1e-15));

      // compare training predictions of both models (just compare probs)
      if (preds!=null && preds2!=null) {
        Scope.track(preds.remove(0));
        Scope.track(preds2.remove(0));
        System.out.println("Compare training predictions of both models (just compare probs)");
        assertIdenticalUpToRelTolerance(preds, preds2, 1e-2);
      }
    } finally {
      if (preds!=null) preds.delete();
      if (preds2!=null) preds2.delete();
      if (preds3!=null) preds3.delete();
      if (preds4!=null) preds4.delete();
      if (preds5!=null) preds5.delete();
      if (fr!=null) fr.delete();
      if (fr2!=null) fr2.delete();
      if (fr3!=null) fr3.delete();
      if (fr4!=null) fr4.delete();
      if (fr5!=null) fr5.delete();
      if(model != null){
        model.delete();
      }
      if(model2 != null){
        model2.delete();
      }
      if(model3 != null){
        model3.delete();
      }
      if(model4 != null){
        model4.delete();
      }
      if(model5 != null){
        model5.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testMonotoneConstraintsInverse() {
    Scope.enter();
    try {
      final String response = "power (hp)";

      Frame f = parseTestFile("smalldata/junit/cars.csv");
      f.replace(f.find(response), f.vecs()[f.find("cylinders")].toNumericVec()).remove();
      DKV.put(Scope.track(f));

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = response;
      parms._train = f._key;
      parms._ignored_columns = new String[]{"name"};
      parms._seed = 42;

      GBMModel.GBMParameters noConstrParams = (GBMModel.GBMParameters) parms.clone();
      GBMModel noConstrModel = new GBM(noConstrParams).trainModel().get();
      Scope.track_generic(noConstrModel);

      assertTrue(noConstrModel._output._varimp.toMap().get("cylinders") > 0);

      GBMModel.GBMParameters constrParams = (GBMModel.GBMParameters) parms.clone();
      constrParams._monotone_constraints = new KeyValue[] {new KeyValue("cylinders", -1)};
      GBMModel constrModel = new GBM(constrParams).trainModel().get();
      Scope.track_generic(constrModel);

      // we essentially eliminated the effect of the feature by setting an inverted constraint
      assertEquals(constrModel._output._varimp.toMap().get("cylinders"), 0, 0);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testMonotoneConstraintsBernoulli() {
    Scope.enter();
    try {
      final Key<Frame> target = Key.make();
      Frame train = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv"));
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");
      Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(train);                    // Update frame after hacking it
      
      String colName = "SegSumT";

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = DistributionFamily.bernoulli;
      parms._monotone_constraints = new KeyValue[]{new KeyValue(colName, 1)};
      
      GBMModel model = (GBMModel) Scope.track_generic(new GBM(parms).trainModel().get());
      Scope.track_generic(model);

      String[] uniqueValues = Scope.track(train.vec(colName).toCategoricalVec()).domain();
      Vec unchangedPreds = Scope.track(model.score(train)).anyVec();
      Vec lastPreds = null;
      for (String valueStr : uniqueValues) {
        final double value = Double.parseDouble(valueStr);

        new MRTask() {
          @Override
          public void map(Chunk c) {
            for (int i = 0; i < c._len; i++)
              c.set(i, value);
          }
        }.doAll(train.vec(colName));
        assertEquals(value, train.vec(colName).min(), 0);
        assertEquals(value, train.vec(colName).max(), 0);

        Vec currentPreds = Scope.track(model.score(train)).anyVec();
        if (lastPreds != null)
          for (int i = 0; i < lastPreds.length(); i++) {
            assertTrue("value=" + value + ", id=" + i, lastPreds.at(i) <= currentPreds.at(i));
            System.out.println("value=" + value + ", id="+ i +" "+lastPreds.at(i) +" <= "+currentPreds.at(i)+" - "+unchangedPreds.at(i));
          }
        lastPreds = currentPreds;
      }
      
    } finally {
      Scope.exit();
    }
  }

  @Test public void testMonotoneConstraintsBernoulliCheck() {
    Scope.enter();
    try {
      Frame train = Scope.track(parseTestFile("smalldata/gbm_test/ecology_model.csv"));
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");
      Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));   // Convert response 'Angaus' to categorical
      DKV.put(train);                    // Update frame after hacking it

      String colName = "SegSumT";

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = DistributionFamily.bernoulli;
      parms._monotone_constraints = new KeyValue[]{new KeyValue(colName, 1)};
      parms._ntrees = 3;

      System.setProperty("sys.ai.h2o.tree.constraintConsistencyCheck", "true");
      GBMModel model = Scope.track_generic(new GBM(parms).trainModel().get());
      Scope.track_generic(model);

      String[] uniqueValues = Scope.track(train.vec(colName).toCategoricalVec()).domain();
      Vec unchangedPreds = Scope.track(model.score(train)).anyVec();
      Vec lastPreds = null;
      for (String valueStr : uniqueValues) {
        final double value = Double.parseDouble(valueStr);

        new MRTask() {
          @Override
          public void map(Chunk c) {
            for (int i = 0; i < c._len; i++)
              c.set(i, value);
          }
        }.doAll(train.vec(colName));
        assertEquals(value, train.vec(colName).min(), 0);
        assertEquals(value, train.vec(colName).max(), 0);

        Vec currentPreds = Scope.track(model.score(train)).anyVec();
        if (lastPreds != null)
          for (int i = 0; i < lastPreds.length(); i++) {
            assertTrue("value=" + value + ", id=" + i, lastPreds.at(i) <= currentPreds.at(i));
            System.out.println("value=" + value + ", id="+ i +" "+lastPreds.at(i) +" <= "+currentPreds.at(i)+" - "+unchangedPreds.at(i));
          }
        lastPreds = currentPreds;
      }
    } finally {
      Scope.exit();
      System.clearProperty("sys.ai.h2o.tree.constraintConsistencyCheck");
    }
  }

  @Test
  public void testMonotoneConstraintsProstate() {
    checkMonotoneConstraintsProstate(DistributionFamily.gaussian);
  }

  @Test
  public void testMonotoneConstraintsProstateTweedie() {
    checkMonotoneConstraintsProstate(DistributionFamily.tweedie);
  }

  @Test
  public void testMonotoneConstraintsProstateQuantile() {
    checkMonotoneConstraintsProstate(DistributionFamily.quantile);
  }

  private void checkMonotoneConstraintsProstate(DistributionFamily distributionFamily) {
    try {
      Scope.enter();
      Frame f = Scope.track(parseTestFile("smalldata/logreg/prostate.csv"));
      f.replace(f.find("CAPSULE"), f.vec("CAPSULE").toNumericVec());
      DKV.put(f);
  
      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = "CAPSULE";
      parms._train = f._key;
      parms._monotone_constraints = new KeyValue[] {new KeyValue("AGE", 1)};
      parms._ignored_columns = new String[]{"ID"};
      parms._ntrees = 50;
      parms._seed = 42;
      parms._distribution = distributionFamily;
      if (distributionFamily.equals(quantile)) {
        parms._quantile_alpha = 0.1;
      }

      String[] uniqueAges = Scope.track(f.vec("AGE").toCategoricalVec()).domain();

      GBMModel model = new GBM(parms).trainModel().get();
      Scope.track_generic(model);

      Vec lastPreds = null;
      for (String ageStr : uniqueAges) {
        final int age = Integer.parseInt(ageStr);
        
        new MRTask() {
          @Override
          public void map(Chunk c) {
            for (int i = 0; i < c._len; i++)
              c.set(i, age);
          }
        }.doAll(f.vec("AGE"));
        assertEquals(age, f.vec("AGE").min(), 0);
        assertEquals(age, f.vec("AGE").max(), 0);

        Vec currentPreds = Scope.track(model.score(f)).anyVec();
        if (lastPreds != null)
          for (int i = 0; i < lastPreds.length(); i++) {
            assertTrue("age=" + age + ", id=" + f.vec("ID").at8(i), lastPreds.at(i) <= currentPreds.at(i));
          }
        lastPreds = currentPreds;
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMonotoneConstraintsUnsupported() {
    try {
      Scope.enter();
      Frame f = Scope.track(parseTestFile("smalldata/logreg/prostate.csv"));
      f.replace(f.find("CAPSULE"), f.vec("CAPSULE").toNumericVec());
      DKV.put(f);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = "CAPSULE";
      parms._train = f._key;
      parms._monotone_constraints = new KeyValue[]{new KeyValue("AGE", 1)};
      parms._distribution = laplace;

      expectedException.expectMessage("ERRR on field: _monotone_constraints: " +
              "Monotone constraints are only supported for Gaussian, Bernoulli, Tweedie and Quantile distributions, your distribution: laplace.");
      GBMModel model = new GBM(parms).trainModel().get();
      Scope.track_generic(model);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMonotoneConstraintSingleSplit() {
    checkMonotonic(2);
  }

  @Test
  public void testMonotoneConstraintMultiSplit() {
    checkMonotonic(5);
  }

  private void checkMonotonic(int depth) {
    try {
      Scope.enter();
      int len = 10000;
      Frame train = makeSinFrame(len);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = "y";
      parms._train = train._key;
      parms._seed = 42;
      parms._max_depth = depth;
      parms._monotone_constraints = new KeyValue[]{new KeyValue("x", 1)};

      GBMModel gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);

      Frame scored = Scope.track(gbm.score(train));
      double last = -1;
      for (int i = 0; i < len; i++) {
        double pred = scored.vec(0).at(i);
        assertTrue("pred = " + pred + " > " + last, pred >= last);
        last = pred;
      }
    } finally {
      Scope.exit();
    }
  }

  private static Frame makeSinFrame(final int len) {
    Vec blueprint = Scope.track(Vec.makeZero(len));
    Frame train = new MRTask() {
      @Override
      public void map(Chunk c, NewChunk ncX, NewChunk ncY) {
        for (int i = 0; i < c._len; i++) {
          Random r = RandomUtils.getRNG(c.start() + i);
          double x = (c.start() + i) / (double) len * Math.PI / 2;
          double noise = Math.abs(r.nextDouble()) * 0.1;
          double y = Math.sin(x) + noise;
          ncX.addNum(x);
          ncY.addNum(y);
        }
      }
    }.doAll(new byte[]{Vec.T_NUM, Vec.T_NUM}, blueprint)
            .outputFrame(Key.<Frame>make(), new String[]{"x", "y"}, null);
    Scope.track(train);
    return train;
  }

  @Test
  public void testPubDev6356() {
    try {
      Scope.enter();
      final int N = 1000;
      double[] columnData = new double[N];
      double[] responseData = new double[N];
      Arrays.fill(responseData, Double.NaN);
      int cnt = 100;
      for (int i = 0; i < cnt; i++) {
        columnData[i] = 1;
        responseData[i] = 1;
        columnData[i + cnt] = 2;
        responseData[i + cnt] = 2;
      }

      // 1. Create a frame with no NAs and train a shallow GBM model
      TestFrameBuilder builder_noNA = new TestFrameBuilder()
              .withName("testFrame_noNA")
              .withColNames("x", "y")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, Arrays.copyOf(columnData, cnt*2))
              .withDataForCol(1, Arrays.copyOf(responseData, cnt*2));

      Frame train_noNA = Scope.track(builder_noNA.build());

      GBMModel.GBMParameters parms_noNA = new GBMModel.GBMParameters();
      parms_noNA._response_column = "y";
      parms_noNA._train = train_noNA._key;
      parms_noNA._seed = 42;
      parms_noNA._max_depth = 2;
      parms_noNA._ntrees = 1;
      parms_noNA._learn_rate = 1;

      GBMModel gbm_noNA = new GBM(parms_noNA).trainModel().get();
      Scope.track_generic(gbm_noNA);

      assertEquals(1.0, gbm_noNA.score(ard(1)), 0);
      assertEquals(2.0, gbm_noNA.score(ard(2)), 0);

      assertEquals(1.0, gbm_noNA.score(ard(0)), 0); // No values x=0 were observed in training -> we don't know anything about them

      // 1. Create a frame with NAs and train a GBM model using the same parameters 
      TestFrameBuilder builder_NA = new TestFrameBuilder()
              .withName("testFrame_NA")
              .withColNames("x", "y")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, columnData)
              .withDataForCol(1, responseData);

      Frame train_NA = Scope.track(builder_NA.build());

      GBMModel.GBMParameters parms_NA = new GBMModel.GBMParameters();
      parms_NA._response_column = "y";
      parms_NA._train = train_NA._key;
      parms_NA._seed = 42;
      parms_NA._max_depth = 2;
      parms_NA._ntrees = 1;
      parms_NA._learn_rate = 1;

      GBMModel gbm_NA = new GBM(parms_NA).trainModel().get();
      Scope.track_generic(gbm_NA);

      assertEquals(1.0, gbm_NA.score(ard(1)), 0);
      assertEquals(2.0, gbm_NA.score(ard(2)), 0);

      assertEquals(1.0, gbm_NA.score(ard(0)), 0);  // Shows that values x=0 were not seen in training 
                                                                      // (they only correspond to NA response)
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGetFeatureNames() throws Exception {
    GBMModel gbm = null;
    try {
      Scope.enter();
      final Frame frame = TestFrameCatalog.specialColumns();

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = frame._key;
      parms._response_column = "Response";
      parms._fold_column = "Fold";
      parms._weights_column = "Weight";
      parms._offset_column = "Offset";
      parms._ntrees = 1;
      parms._min_rows = 0.1;

      gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);

      frame.remove("Fold").remove();
      gbm.score(frame).remove();
      assertArrayEquals(new String[0], gbm._warningsP); // predict warning
      assertArrayEquals(new String[0], gbm._warnings);
      String[] expectedFeatures = new String[]{"ColA", "ColC"}; // Note: ColB is dropped becuase it is a String column
      
      // check model
      assertArrayEquals(expectedFeatures, gbm._output.features());
      assertArrayEquals(expectedFeatures, gbm.modelDescriptor().features());
      
      // check mojo
      MojoModel mojo = gbm.toMojo();
      assertArrayEquals(expectedFeatures, mojo.features());
      
    } finally {
      if (gbm != null) {
        gbm.deleteCrossValidationModels();
        gbm.deleteCrossValidationPreds();
      }
      Scope.exit();
    }
  }

  @Test
  public void testIsFeatureUsedInPredict() {
    isFeatureUsedInPredictHelper(false, false);
    isFeatureUsedInPredictHelper(true, false);
    isFeatureUsedInPredictHelper(false, true);
    isFeatureUsedInPredictHelper(true, true);
  }

  private void isFeatureUsedInPredictHelper(boolean ignoreConstCols, boolean multinomial) {
    Scope.enter();
    Vec target = Vec.makeRepSeq(100, 3);
    if (multinomial) target = target.toCategoricalVec();
    Vec zeros = Vec.makeCon(0d, 100);
    Vec nonzeros = Vec.makeCon(1e10d, 100);
    Frame dummyFrame = new Frame(
            new String[]{"a", "b", "c", "d", "e", "target"},
            new Vec[]{zeros, zeros, zeros, zeros, target, target}
    );
    dummyFrame._key = Key.make("DummyFrame_testIsFeatureUsedInPredict");

    Frame otherFrame = new Frame(
            new String[]{"a", "b", "c", "d", "e", "target"},
            new Vec[]{nonzeros, nonzeros, nonzeros, nonzeros, target, target}
    );

    Frame reference = null;
    Frame prediction = null;
    GBMModel model = null;
    try {
      DKV.put(dummyFrame);
      GBMModel.GBMParameters gbm = new GBMModel.GBMParameters();
      gbm._train = dummyFrame._key;
      gbm._response_column = "target";
      gbm._ntrees = 5;
      gbm._max_depth = 3;
      gbm._min_rows = 1;
      gbm._nbins = 3;
      gbm._nbins_cats = 3;
      gbm._seed = 1;
      gbm._ignore_const_cols = ignoreConstCols;

      GBM job = new GBM(gbm);
      model = job.trainModel().get();

      String lastUsedFeature = "";
      int usedFeatures = 0;
      for(String feature : model._output._names) {
        if (model.isFeatureUsedInPredict(feature)) {
          usedFeatures ++;
          lastUsedFeature = feature;
        }
      }
      assertEquals(1, usedFeatures);
      assertEquals("e", lastUsedFeature);

      reference = model.score(dummyFrame);
      prediction = model.score(otherFrame);
      for (int i = 0; i < reference.numRows(); i++) {
        assertEquals(reference.vec(0).at(i), prediction.vec(0).at(i), 1e-10);
      }
    } finally {
      dummyFrame.delete();
      if (model != null) model.delete();
      if (reference != null) reference.delete();
      if (prediction != null) prediction.delete();
      target.remove();
      zeros.remove();
      nonzeros.remove();
      Scope.exit();
    }
  }

  @Test
  public void testPrintMojoWithFloatToDouble() throws Exception {
    try {
      Scope.enter();
      double splitPoint = 2841.083;
      String splitPointStr = splitPoint + "f";
      Frame frame = new TestFrameBuilder()
              .withName("data")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(splitPoint*2, 0, splitPoint*2, splitPoint*2, splitPoint*2, 0, splitPoint*2))
              .withDataForCol(1, ard(1, 0, 1, 1, 1, 0, 1))
              .build();

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = frame._key;
      parms._response_column = "Response";
      parms._ntrees = 1;
      parms._min_rows = 1;

      GBMModel gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);

      String pojo = gbm.toJava(false, false);
      assertTrue(pojo.contains(splitPointStr));

      File mojoFile = temporaryFolder.newFile("gbm_mojo");
      gbm.exportMojo(mojoFile.getAbsolutePath(), true);

      String[] pmArgs = new String[]{"-i", mojoFile.getAbsolutePath(), "--format", "json"};

      File outputOrig = temporaryFolder.newFile("output_orig");
      PrintMojo pmOrig = new PrintMojo();
      pmOrig.parseArgs(ArrayUtils.append(pmArgs, new String[]{"-o", outputOrig.getAbsolutePath()}));
      pmOrig.run();

      File outputAsDouble = temporaryFolder.newFile("output_as_double");
      PrintMojo pmAsDouble = new PrintMojo();
      pmAsDouble.parseArgs(ArrayUtils.append(pmArgs, new String[]{"-o", outputAsDouble.getAbsolutePath(), "--floattodouble"}));
      pmAsDouble.run();

      List<String> jsonLinesOrig = Files.readLines(outputOrig, Charset.defaultCharset());
      List<String> jsonLinesAsDouble = Files.readLines(outputAsDouble, Charset.defaultCharset());
      assertEquals(jsonLinesOrig.size(), jsonLinesAsDouble.size());

      for (int i = 0; i < jsonLinesOrig.size(); i++) {
        String jsonLineOrig = jsonLinesOrig.get(i);
        String jsonLineAsDouble = jsonLinesAsDouble.get(i);
        if (jsonLineOrig.contains("predValue") || jsonLineOrig.contains("splitValue")) {
          // interpret the predValue/splitValue as FLOAT from the original output, then cast to DOUBLE
          // (this is what would POJO do)
          double fVal = Float.parseFloat(jsonLineOrig.split(":")[1].replaceAll(",", ""));
          // interpret the modified output as DOUBLE directly with no casting
          double dVal = Double.parseDouble(jsonLineAsDouble.split(":")[1].replaceAll(",", ""));
          // values need to match perfectly
          assertEquals(fVal, dVal, 0);
        } else {
          assertEquals(jsonLineOrig, jsonLineAsDouble);
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testResetThreshold() throws Exception {
    GBMModel gbm = null;
    try {
      Scope.enter();
      Frame frame = new TestFrameBuilder()
              .withName("data")
              .withColNames("ColA", "ColB", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(0, 1, 0, 1, 0, 1, 0))
              .withDataForCol(1, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
              .withDataForCol(2, ard(1, 0, 1, 1, 1, 0, 1))
              .build();

      frame = frame.toCategoricalCol(2);

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = frame._key;
      parms._response_column = "Response";
      parms._ntrees = 1;
      parms._min_rows = 0.1;
      parms._distribution = bernoulli;

      gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);
      double oldTh = gbm._output.defaultThreshold();

      double newTh = 0.6379068421037659;
      gbm.resetThreshold(newTh);
      double resetTh = gbm._output.defaultThreshold();
      assertEquals("The new model (" +newTh+ ") is not the same as reset model ("+resetTh+").", newTh, resetTh, 0);
      assertNotEquals("The old threshold is the same as reset threshold.", oldTh, resetTh);

      // check mojo
      MojoModel mojo = gbm.toMojo();
      double mojoTh = mojo._defaultThreshold;
      assertEquals("The new model is not same in MOJO after reset.", newTh, mojoTh, 0);

    } finally {
      if (gbm != null) {
        gbm.deleteCrossValidationModels();
        gbm.deleteCrossValidationPreds();
      }
      Scope.exit();
    }
  }

  @Test
  public void testMojoMetrics() throws Exception {
    GBMModel gbm = null;
    try {
      Scope.enter();
      Frame frame = new TestFrameBuilder()
              .withName("data")
              .withColNames("ColA", "ColB", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(0, 1, 0, 1, 0, 1, 0))
              .withDataForCol(1, ard(Double.NaN, 1, 2, 3, 4, 5.6, 7))
              .withDataForCol(2, ard(1, 0, 1, 1, 1, 0, 1))
              .build();

      frame = frame.toCategoricalCol(2);

      Frame frameVal = new TestFrameBuilder()
              .withName("dataVal")
              .withColNames("ColA", "ColB", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(0, 1, 1, 1, 0, 0, 1))
              .withDataForCol(1, ard(Double.NaN, 1, 3, 2, 4, 8, 7))
              .withDataForCol(2, ard(1, 1, 1, 0, 0, 1, 1))
              .build();

      frameVal = frameVal.toCategoricalCol(2);

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = frame._key;
      parms._valid = frameVal._key;
      parms._response_column = "Response";
      parms._ntrees = 1;
      parms._min_rows = 0.1;
      parms._distribution = bernoulli;

      gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);
      Frame train_score = gbm.score(frame);
      Scope.track(train_score);
      
      assertTrue(gbm.testJavaScoring(frame, train_score, 1e-15));

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGBMFeatureInteractions() {
    Scope.enter();
    try {
      Frame f = Scope.track(parseTestFile("smalldata/logreg/prostate.csv"));
      f.replace(f.find("CAPSULE"), f.vec("CAPSULE").toNumericVec());
      DKV.put(f);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = "CAPSULE";
      parms._train = f._key;

      GBMModel model = new GBM(parms).trainModel().get();
      Scope.track_generic(model);

      FeatureInteractions featureInteractions = model.getFeatureInteractions(2,100,-1);
      assertEquals(featureInteractions.size(), 113);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGBMFeatureInteractionsGainTest() {
    Scope.enter();
    try {
      Frame f = Scope.track(parseTestFile("smalldata/logreg/prostate.csv"));
      f.replace(f.find("CAPSULE"), f.vec("CAPSULE").toNumericVec());
      DKV.put(f);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = "CAPSULE";
      parms._train = f._key;
      parms._ntrees = 1;
      parms._ignored_columns = new String[] {"ID"};

      GBMModel model = new GBM(parms).trainModel().get();
      Scope.track_generic(model);
      FeatureInteractions featureInteractions = model.getFeatureInteractions(2,100,-1);
      SharedTreeSubgraph treeSubgraph = model.getSharedTreeSubgraph(0, 0);

      String[] keysToCheck = new String[]{"DPROS", "PSA", "GLEASON", "VOL"};
      for (String feature : keysToCheck) {
        if (!feature.equals(parms._response_column)) {
          List<SharedTreeNode> featureSplits = treeSubgraph.nodesArray.stream()
                  .filter(node -> feature.equals(node.getColName()))
                  .collect(Collectors.toList());
          double featureGain = 0.0;
          for (int i = 0; i < featureSplits.size(); i++) {
            SharedTreeNode currSplitNode = featureSplits.get(i);
            featureGain += currSplitNode.getSquaredError() - currSplitNode.getLeftChild().getSquaredError() - currSplitNode.getRightChild().getSquaredError();
          }
          assertEquals(featureGain, featureInteractions.get(feature).gain, 0.0001);
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGBMFeatureInteractionsCheckRanksVsVarimp() {
    Scope.enter();
    try {
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      
      GBMModel.GBMParameters gbmParms = new GBMModel.GBMParameters();
      gbmParms._train = tfr._key;
      gbmParms._response_column = "AGE";
      gbmParms._ignored_columns = new String[]{"ID"};
      gbmParms._seed = 0xDECAF;
      gbmParms._build_tree_one_node = true;

      GBMModel gbmModel = new GBM(gbmParms).trainModel().get();
      Scope.track_generic(gbmModel);
      
      FeatureInteractions featureInteractions = gbmModel.getFeatureInteractions(0, 100, -1);
      VarImp gbmVarimp = gbmModel._output._varimp;
      
      List<KeyValue> varimpList = new ArrayList<>();
      for (int i = 0; i < gbmVarimp._varimp.length; i++) {
        varimpList.add(new KeyValue(gbmVarimp._names[i], gbmVarimp._varimp[i]));  
      }
      varimpList.sort((a,b) -> a.getValue() < b.getValue() ? -1 : a.getValue() == b.getValue() ? 0 : 1);

      List<KeyValue> featureList = new ArrayList<>();
      for (Map.Entry<String, FeatureInteraction> featureInteraction : featureInteractions.entrySet()) {
        featureList.add(new KeyValue(featureInteraction.getKey(), featureInteraction.getValue().gain));
      }
      featureList.sort((a,b) -> a.getValue() < b.getValue() ? -1 : a.getValue() == b.getValue() ? 0 : 1);

      for (int i= 0; i < featureList.size(); i++) {
        assertEquals(featureList.get(i).getKey(), varimpList.get(i).getKey());
      }
    } finally {
      Scope.exit();
    }
  }
  
  @Test
  public void testMultinomialAucEarlyStopping(){
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = makeGBMParameters();
    Frame train = null, valid = null;
    try {
      Scope.enter();
      train = parseTestFile("smalldata/junit/mixcat_train.csv");
      valid = parseTestFile("smalldata/junit/mixcat_test.csv");
      parms._train = train._key;
      parms._valid = valid._key;
      parms._response_column = "Response"; 
      parms._ntrees = 5;
      parms._learn_rate = 1;
      parms._min_rows = 1;
      parms._distribution = DistributionFamily.multinomial;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC;
      parms._stopping_rounds = 1;
      parms._auc_type = MultinomialAucType.MACRO_OVO;
      parms._seed = 42;
      parms._score_tree_interval = 1;

      gbm = new GBM(parms).trainModel().get();

      // with learn_rate = 1 and min_rows = 1 we are forcing the trees to overfit on the training dataset
      // we should see the training stop almost immediately (after 2 trees)
      assertThat(gbm._output._ntrees, OrderingComparison.lessThan(5));

      ScoreKeeper[] history = gbm._output._scored_train;
      double previous = Double.MIN_VALUE;
      for (ScoreKeeper sk : history) {
        assertThat(sk._AUC, OrderingComparison.greaterThanOrEqualTo(previous)); 
        previous = sk._AUC;
      }
    } finally {
      if(train != null){ train.remove();}
      if(valid != null) {valid.remove();}
      if( gbm != null ) gbm.delete();
      Scope.exit();
    }
  }

  @Test
  public void testEarlyStoppingDoesNotNeedFinalScoring() {
    GBMModel.GBMParameters parms = makeGBMParameters();
    try {
      Scope.enter();
      Frame train = parseTestFile("smalldata/testng/cars_train.csv");
      Scope.track(train);
      Frame valid = parseTestFile("smalldata/testng/cars_test.csv");
      Scope.track(valid);
      parms._train = train._key;
      parms._valid = valid._key;
      parms._response_column = "cylinders";
      parms._ntrees = 1000; // will only build few
      parms._learn_rate = 1;
      parms._min_rows = 1;
      parms._stopping_metric = ScoreKeeper.StoppingMetric.MSE;
      parms._stopping_rounds = 1;
      parms._score_each_iteration = true;
      parms._ignored_columns = new String[]{"name"};

      GBMModel gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);

      // +1 is for null model
      assertEquals(gbm._output._ntrees + 1, gbm._output._scored_train.length);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMissingFoldColumnIsNotReportedInScoring() {
    try {
      Scope.enter();
      final Frame frame = TestFrameCatalog.specialColumns();

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = frame._key;
      parms._response_column = "Response";
      parms._fold_column = "Fold";
      parms._weights_column = "Weight";
      parms._offset_column = "Offset";
      parms._ntrees = 1;
      parms._min_rows = 0.1;
      parms._keep_cross_validation_models = false;
      parms._keep_cross_validation_predictions = false;
      parms._keep_cross_validation_fold_assignment = false;

      GBMModel gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);

      assertArrayEquals(new String[0], gbm._warnings);
      assertArrayEquals(null, gbm._warningsP); // no predict warning to begin with

      final Frame test = TestFrameCatalog.specialColumns();
      test.remove("Fold").remove();
      DKV.put(test);

      gbm.score(test).remove();

      assertArrayEquals(new String[0], gbm._warningsP); // no predict warnings
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testHStatistic() {
    Frame fr = null;
    GBMModel model = null;
    Scope.enter();
    try {
      Frame f = Scope.track(parse_test_file("smalldata/logreg/prostate.csv"));
      f.replace(f.find("CAPSULE"), f.vec("CAPSULE").toNumericVec());
      Scope.track(f);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = "CAPSULE";
      parms._train = f._key;
      parms._ntrees = 3;
      parms._seed = 1234L;

      model = new GBM(parms).trainModel().get();
      double h = model.getFriedmanPopescusH(f, new String[] {"GLEASON","VOL"});
      assertTrue(Double.isNaN(h) || (h >= 0.0 && h <= 1.0));
    } finally {
      if (model != null) model.delete();
      if (fr  != null) fr.remove();
      Scope.exit();
    }
  }

  @Test
  public void testMonotoneConstraintsTriggerStrictlyReproducibleHistograms() {
    GBMModel.GBMParameters p = makeGBMParameters();
    p._distribution = gaussian;
    if (test_type.equals("EmulateConstraints")) {
      assertTrue(p.forceStrictlyReproducibleHistograms());
    } else {
      assertFalse(p.forceStrictlyReproducibleHistograms());
    }
  }

  @Test
  public void testReproducibilityWithNAs() {
    Assume.assumeTrue(H2O.CLOUD.size() == 1); // don't run on multinode (too long)
    checkReproducibility(0.5, Double.NaN);
  }

  @Test
  public void testReproducibilityWithNAsSubstituted() { // NAs are substituted for an outlier value 
    Assume.assumeTrue(H2O.CLOUD.size() == 1); // don't run on multinode (too long)
    checkReproducibility(0.5, 10.0);
  }

  @Test
  public void testReproducibilityWithoutNAs() {
    Assume.assumeTrue(H2O.CLOUD.size() == 1); // don't run on multinode (too long)
    checkReproducibility(1.0 + Double.MIN_VALUE, Double.NaN);
  }

  @Test
  public void testStrictReproducibility() {
    Assume.assumeTrue(H2O.CLOUD.size() == 1); // don't run on multinode (too long)

    SharedTree.SharedTreeDebugParams debugParms = new SharedTree.SharedTreeDebugParams();
    debugParms._reproducible_histos = true; // use fully reproducible (deterministic) histograms
    debugParms._keep_orig_histo_precision = true; // do not reduce precision of histograms in the final step
    debugParms._histo_monitor_class = HistogramCollectingMonitor.class.getName();
    
    try {
      checkReproducibility(0.9, Double.NaN, debugParms);
      assertFalse(HistogramCollectingMonitor._histos.isEmpty());
      List<HistogramCollectingMonitor> initialHistos = HistogramCollectingMonitor._histos
              .stream()
              .filter(HistogramCollectingMonitor::isForEmptyModel)
              .collect(Collectors.toList());
      assertEquals(10, initialHistos.size());
      int histosPerModel = HistogramCollectingMonitor._histos.size() / initialHistos.size();
      compareModelHistos(HistogramCollectingMonitor._histos, histosPerModel);
    } finally {
      HistogramCollectingMonitor._histos.clear();
    }
  }

  static void compareModelHistos(List<HistogramCollectingMonitor> histos, int histosPerModel) {
    assertTrue(histosPerModel > 0);
    for (int i = 0; i < histos.size() - histosPerModel; i++) {
      HistogramCollectingMonitor histo = histos.get(i);
      HistogramCollectingMonitor histoComp = histos.get(i + histosPerModel);
      for (int h = 0; h < histo._hcs.length; h++) {
        for (int c = 0; c < histo._hcs[h].length; c++) {
          assertEquals(histo._tree, histoComp._tree);
          assertEquals(histo._k, histoComp._k);
          assertEquals(histo._leaf, histoComp._leaf);
          assertArrayEquals(
                  "Vals for histogram " + histo._hcs[h][c]._name + " should match exactly, h=" + h + ",c" + c + ",i" + i,
                  histo._hcs[h][c].getRawVals(),
                  histoComp._hcs[h][c].getRawVals(),
                  0
          );
        }
      }
    }
  }

  public static class HistogramCollectingMonitor
          implements Consumer<DHistogram[][]> {

    int _tree;
    int _k;
    int _leaf;
    DHistogram[][] _hcs;

    static final List<HistogramCollectingMonitor> _histos = new ArrayList<>();

    @SuppressWarnings("unused")
    public HistogramCollectingMonitor(int tree, int k, int leaf) {
      _tree = tree;
      _k = k;
      _leaf = leaf;
    }

    @SuppressWarnings("unused")
    public HistogramCollectingMonitor() {
    }

    boolean isForEmptyModel() {
      return _tree == 0 && _leaf == 0;
    }
    
    @Override
    public void accept(DHistogram[][] hcs) {
      _hcs = hcs;
      synchronized (_histos) {
        _histos.add(this);
      }
    }
  }

  private void checkReproducibility(double thresholdNA, double NA) {
    checkReproducibility(thresholdNA, NA, null);
  }
  
  private void checkReproducibility(double thresholdNA, double NA, SharedTree.SharedTreeDebugParams debugParms) {
    GBMModel model = null;
    Scope.enter();
    try {
      Vec randomCol = makeRandomVec(1000000, 256, thresholdNA, NA);
      Frame f = new Frame(Key.make("random_frame"));
      f.add("C1", randomCol);
      f.add("C2", randomCol);
      f.add("response", makeRandomVec(randomCol.length(), randomCol.nChunks(), 1.0 + Double.MIN_VALUE, NA));
      Scope.track(f);
      DKV.put(f);

      GBMModel.GBMParameters parms = makeGBMParameters();
      parms._response_column = "response";
      parms._train = f._key;
      parms._ntrees = 1;
      parms._seed = 1234L;

      String pojo = null;
      for (int i = 0; i < 10; i++) {
        GBM gbm = new GBM(parms);
        if (debugParms != null)
          gbm.setDebugParams(debugParms);
        model = gbm.trainModel().get();

        String modelId = model._key.toString();
        String newPojo = model
                .toJava(false, false)
                .replaceAll("AUTOGENERATED BY H2O at.*", "") // get rid of timestamp
                .replaceAll(modelId, "");
        model.delete();

        if (i > 0)
          assertEquals("Different in pojo found after in attempt #" + i, pojo, newPojo);
        pojo = newPojo;
      }
    } finally {
      if (model != null) model.delete();
      Scope.exit();
    }
  }
  
  private static Vec makeRandomVec(long len, final  int nchunks, double thresholdNA, double NA) {
    Vec random = Scope.track(Vec.makeConN(len, nchunks));
    Scope.track(random);
    new MRTask() {
      @Override
      public void map(Chunk c) {
        for (int i = 0; i < c._len; i++) {
          Random r = RandomUtils.getRNG(c.start() + i);
          double d = r.nextDouble();
          if (d < thresholdNA)
            c.set(i, r.nextDouble());
          else if (Double.isNaN(NA))
            c.setNA(i);
          else
            c.set(i, NA);
        }
      }
    }.doAll(random);
    return random;
  }

  @Test
  public void testMonotoneNAvsRestSplit() {
    Assume.assumeTrue(test_type.equals("Default")); // no need to run 2x

    Scope.enter();
    try {
      Frame f = new TestFrameBuilder()
              .withColNames("C1", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .withDataForCol(0, ard(0, 1, 2, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN))
              .withDataForCol(1, new String[]{"a", "a", "a", "b", "b", "b", "b", "b", "b"})
              .build();

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._response_column = "Response";
      parms._min_rows = 1;
      parms._learn_rate = 1;
      parms._train = f._key;
      parms._ntrees = 3;
      parms._seed = 1234L;
      parms._distribution = bernoulli;
      parms._score_each_iteration = true;

      GBMModel.GBMParameters parmsMonotone = (GBMModel.GBMParameters) parms.clone();
      parmsMonotone._monotone_constraints = new KeyValue[] {
              new KeyValue("C1", 1)
      };

      GBMModel model = new GBM(parms).trainModel().get();
      Scope.track_generic(model);

      GBMModel modelMonotone = new GBM(parmsMonotone).trainModel().get();
      Scope.track_generic(modelMonotone);

      Assert.assertArrayEquals(model._output._scored_train, modelMonotone._output._scored_train);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUpdateWeightsWarning() {
    Scope.enter();
    try {
      Frame train = new TestFrameBuilder()
              .withColNames("C1", "C2")
              .withDataForCol(0, new double[]{0, 1})
              .withDataForCol(1, new double[]{0, 1})
              .build();

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._response_column = train.lastVecName();
      parms._train = train._key;
      parms._ntrees = 2;
      parms._learn_rate = 1.0;
      parms._min_rows = 1.0;

      GBMModel model = new GBM(parms).trainModel().get();
      Scope.track_generic(model);

      Vec weights = train.anyVec().makeCon(1.0);
      train.add("ws", weights);
      DKV.put(train);

      assertFalse(model.updateAuxTreeWeights(train, "ws").hasWarnings());

      // now use a subset of the dataset to leave some nodes empty 
      weights.set(0, 0);
      Model.UpdateAuxTreeWeights.UpdateAuxTreeWeightsReport report = model.updateAuxTreeWeights(train, "ws");
      assertTrue(report.hasWarnings());
      assertArrayEquals(new int[1], report._warn_trees);
      assertArrayEquals(new int[1], report._warn_classes);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMulticlassStopping() {
    try {
      Scope.enter();
      Frame f = new TestFrameBuilder()
              .withDataForCol(0, ar(0, 1, 2))
              .withDataForCol(1, new String[]{"a", "b", "c"})
              .withVecTypes(Vec.T_NUM, Vec.T_CAT)
              .build();

      GBMModel.GBMParameters p = makeGBMParameters();
      p._response_column = f.lastVecName();
      p._ntrees = 1000;
      p._max_depth = 1;
      p._train = f._key;
      p._min_rows = 1;
      p._distribution = multinomial;
      p._learn_rate = 1;

      GBMModel model = new GBM(p).trainModel().get();
      Scope.track_generic(model);

      assertTrue(model._output._ntrees < 1000);
      
      int lastTreeIndex = model._output._ntrees - 1;
      for (int i = 0; i < f.lastVec().domain().length; i++) {
        SharedTreeSubgraph lastTree = model.getSharedTreeSubgraph(lastTreeIndex, i);
        assertTrue(lastTree.rootNode.isLeaf());
        assertEquals(lastTree.rootNode.getPredValue(), 0f, 0f);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPrintPojoWithDoubles()  {
    final String outputDoublesPropName = H2O.OptArgs.SYSTEM_PROP_PREFIX + "java.output.doubles";
    try {
      Scope.enter();
      double splitPoint = 2841.083;
      String splitPointStr = splitPoint + "f";
      Frame frame = new TestFrameBuilder()
              .withName("data")
              .withColNames("ColA", "Response")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, ard(splitPoint * 2, 0, splitPoint * 2, splitPoint * 2, splitPoint * 2, 0, splitPoint * 2))
              .withDataForCol(1, ard(1, 0, 1, 1, 1, 0, 1))
              .build();

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = frame._key;
      parms._response_column = "Response";
      parms._ntrees = 1;
      parms._min_rows = 1;

      GBMModel gbm = new GBM(parms).trainModel().get();
      Scope.track_generic(gbm);
      Frame scored = Scope.track(gbm.score(frame));
      
      // 1. export POJO as usual - floating values will be represented as floats
      String pojo_floats = gbm.toJava(false, false);
      assertTrue(pojo_floats.contains(splitPointStr));

      // 2. force export of floating values as doubles
      System.setProperty(outputDoublesPropName, "true");

      // 3. export POJO and check it contains doubles instead of floats
      String pojo_doubles = gbm.toJava(false, false);
      String splitPointDoubleStr = Double.toString(Float.parseFloat(splitPointStr));
      assertTrue(pojo_doubles.contains(splitPointDoubleStr));
      assertFalse(pojo_doubles.contains(splitPointStr));

      // 4. validate scoring with "double" POJO
      assertTrue(gbm.testJavaScoring(frame, scored, 0));
    } finally {
      System.clearProperty(outputDoublesPropName);
      Scope.exit();
    }
  }

  /**
   * Creates a frame with 
   *  - completely random column
   *  - predictor column that perfectly predicts response
   *  - response with values [0, 1]
   * 
   * @return frame instance (tracked in Scope)
   */
  private Frame makeFrameForUniformRobustTesting(double outlierScale) {
    Random r = RandomUtils.getRNG(42);
    final int N = 100;

    double[] randomCol = new double[N];
    double[] perfectPredictor = new double[N];
    double[] response = new double[N];

    for (int i = 0; i < N; i++) {
      randomCol[i] = r.nextDouble();
      perfectPredictor[i]  = i / (double) N;
      response[i] = perfectPredictor[i];
    }

    // inject outliers
    perfectPredictor[0  ] = -outlierScale;
    perfectPredictor[N-1] =  outlierScale;

    return new TestFrameBuilder()
            .withName("data")
            .withColNames("RandomCol", "PerfectPredictor", "Response")
            .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
            .withDataForCol(0, randomCol)
            .withDataForCol(1, perfectPredictor)
            .withDataForCol(2, response)
            .build();
  }
  
  @Test
  public void testUniformRobust()  {
    try {
      Scope.enter();
      Frame frame = makeFrameForUniformRobustTesting(1e3);

      GBMModel.GBMParameters parmsDefault = new GBMModel.GBMParameters();
      parmsDefault._train = frame._key;
      parmsDefault._response_column = "Response";
      parmsDefault._ntrees = 1;
      parmsDefault._max_depth = 3;
      parmsDefault._min_rows = 1;
      parmsDefault._learn_rate = 1;
      parmsDefault._distribution = gaussian;

      GBMModel.GBMParameters parmsRobust = (GBMModel.GBMParameters) parmsDefault.clone();
      parmsRobust._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.UniformRobust;

      GBMModel gbmDefault = new GBM(parmsDefault).trainModel().get();
      assertNotNull(gbmDefault);
      Scope.track_generic(gbmDefault);

      SharedTreeSubgraph treeDefault = gbmDefault.getSharedTreeSubgraph(0, 0);
      // UniformAdaptive finds signal in the RandomColumn
      assertEquals(Arrays.asList(
              "RandomCol",
              "RandomCol", "RandomCol",
              "RandomCol", "RandomCol", "RandomCol", "RandomCol"
      ), getSplitCols(treeDefault));

      GBMModel gbmRobust = new GBM(parmsRobust).trainModel().get();
      assertNotNull(gbmRobust);
      Scope.track_generic(gbmRobust);

      SharedTreeSubgraph treeRobust = gbmRobust.getSharedTreeSubgraph(0, 0);
      // UniformRobust still splits on the RandomColumn in the root but refined split-points kick on the next level
      assertEquals(Arrays.asList(
              "RandomCol",
              "PerfectPredictor", "PerfectPredictor",
              "PerfectPredictor", "PerfectPredictor", "PerfectPredictor", "PerfectPredictor"
      ), getSplitCols(treeRobust));

      // Compare MAE
      assertEquals(0.2, ((ModelMetricsRegression) gbmDefault._output._training_metrics).mae(), 0.05);
      assertEquals(0.05, ((ModelMetricsRegression) gbmRobust._output._training_metrics).mae(), 0.01);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testUniformRobustNeedsMultipleRefinementsOfBinsForLargeOutliers()  {
    try {
      Scope.enter();
      Frame frame = makeFrameForUniformRobustTesting(
              1e6
      );

      GBMModel.GBMParameters p = new GBMModel.GBMParameters();
      p._train = frame._key;
      p._response_column = "Response";
      p._ntrees = 1;
      p._max_depth = 4;
      p._min_rows = 1;
      p._learn_rate = 1;
      p._distribution = gaussian;
      p._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.UniformRobust;

      GBMModel gbm = new GBM(p).trainModel().get();
      assertNotNull(gbm);
      Scope.track_generic(gbm);

      SharedTreeSubgraph treeRobust = gbm.getSharedTreeSubgraph(0, 0);

      // It takes 2 iterations of split-points refinement to get useful split-points
      String splits = printTree(treeRobust);
      assertEquals(String.join("\n",
              "-> RandomCol",
              "    -> RandomCol",
              "        -> PerfectPredictor",
              "            -> PerfectPredictor",
              "            -> PerfectPredictor",
              "        -> RandomCol",
              "    -> RandomCol",
              "        -> PerfectPredictor",
              "            -> PerfectPredictor",
              "            -> PerfectPredictor",
              "        -> PerfectPredictor",
              "            -> PerfectPredictor",
              "            -> PerfectPredictor",
              ""), splits);
    } finally {
      Scope.exit();
    }
  }

  private List<String> getSplitCols(SharedTreeSubgraph t) {
    return t.nodesArray.stream()
            .filter(n -> !n.isLeaf())
            .map(SharedTreeNode::getColName)
            .collect(Collectors.toList());
  }

  private String printTree(SharedTreeSubgraph t) {
    StringBuilder sb = new StringBuilder();
    printNode(t.rootNode, 0, sb);
    return sb.toString();
  }

  private void printNode(SharedTreeNode n, int depth, StringBuilder sb) {
    if (n.isLeaf())
      return;
    for (int i = 0; i < depth; i++)
      sb.append("    ");
    sb.append("-> ").append(n.getColName()).append('\n');
    printNode(n.getLeftChild(), depth + 1, sb);
    printNode(n.getRightChild(), depth + 1, sb);
  }
  
}
