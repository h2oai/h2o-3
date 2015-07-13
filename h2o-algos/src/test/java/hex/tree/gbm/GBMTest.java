package hex.tree.gbm;

import hex.AUC2;
import hex.Distributions;
import hex.ModelMetricsBinomial;
import hex.ScoreKeeper;
import org.junit.*;
import water.*;
import water.fvec.*;
import water.fvec.RebalanceDataSet;
import water.util.Log;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

public class GBMTest extends TestUtil {

  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  private abstract class PrepData { abstract int prep(Frame fr); }

  static final String ignored_aircols[] = new String[] { "DepTime", "ArrTime", "AirTime", "ArrDelay", "DepDelay", "TaxiIn", "TaxiOut", "Cancelled", "CancellationCode", "Diverted", "CarrierDelay", "WeatherDelay", "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsDepDelayed"};

  @Test public void testGBMRegressionGaussian() {
    GBMModel gbm = null;
    Frame fr = null, fr2 = null;
    try {
      fr = parse_test_file("./smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = fr._key;
      parms._distribution = Distributions.Family.gaussian;
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

      GBM job = null;
      try {
        job = new GBM(parms);
        gbm = job.trainModel().get();
      } finally {
        if (job != null) job.remove();
      }
      Assert.assertTrue(job._state == water.Job.JobState.DONE); //HEX-1817
      //Assert.assertTrue(gbm._output._state == Job.JobState.DONE); //HEX-1817

      // Done building model; produce a score column with predictions
      fr2 = gbm.score(fr);
      double sq_err = new CompErr().doAll(job.response(),fr2.vecs()[0])._sum;
      double mse = sq_err/fr2.numRows();
      assertEquals(79152.26,mse,0.1);
      assertEquals(79152.26,gbm._output._scored_train[1]._mse,0.1);
    } finally {
      if( fr  != null ) fr .remove();
      if( fr2 != null ) fr2.remove();
      if( gbm != null ) gbm.delete();
    }
  }

  private static class CompErr extends MRTask<CompErr> {
    double _sum;
    @Override public void map( Chunk resp, Chunk pred ) {
      double sum = 0;
      for( int i=0; i<resp._len; i++ ) {
        double err = resp.atd(i)-pred.atd(i);
        sum += err*err;
      }
      _sum = sum;
    }
    @Override public void reduce( CompErr ce ) { _sum += ce._sum; }
  }

  @Test public void testBasicGBM() {
    // Regression tests
    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, Distributions.Family.gaussian);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, Distributions.Family.poisson);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, Distributions.Family.gamma);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr ) {fr.remove("name").remove(); return ~fr.find("economy (mpg)"); }},
            false, Distributions.Family.tweedie);

    // Classification tests
    basicGBM("./smalldata/junit/test_tree.csv",
            new PrepData() { int prep(Frame fr) { return 1; }
            },
            false, Distributions.Family.multinomial);

    basicGBM("./smalldata/junit/test_tree_minmax.csv",
            new PrepData() { int prep(Frame fr) { return fr.find("response"); }
            },
            false, Distributions.Family.bernoulli);

    basicGBM("./smalldata/logreg/prostate.csv",
            new PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
            },
            false, Distributions.Family.bernoulli);

    basicGBM("./smalldata/logreg/prostate.csv",
            new PrepData() { int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); }
            },
            false, Distributions.Family.multinomial);

    basicGBM("./smalldata/junit/cars.csv",
            new PrepData() { int prep(Frame fr) { fr.remove("name").remove(); return fr.find("cylinders"); }
            },
            false, Distributions.Family.multinomial);

    basicGBM("./smalldata/gbm_test/alphabet_cattest.csv",
            new PrepData() { int prep(Frame fr) { return fr.find("y"); }
            },
            false, Distributions.Family.bernoulli);

    basicGBM("./smalldata/airlines/allyears2k_headers.zip",
             new PrepData() { int prep(Frame fr) {
               for( String s : ignored_aircols ) fr.remove(s).remove();
               return fr.find("IsArrDelayed"); }
             },
            false, Distributions.Family.bernoulli);
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
                 Scope.track(fr.replace(ci,fr.vecs()[ci].toEnum())._key);
                 return fr.find("CAPSULE"); // Prostate: predict on CAPSULE
               }
             }, false, Distributions.Family.bernoulli);
    Scope.exit();
  }

  // ==========================================================================
  public GBMModel.GBMOutput basicGBM(String fname, PrepData prep, boolean validation, Distributions.Family family) {
    GBMModel gbm = null;
    Frame fr = null, fr2= null, vfr=null;
    try {
      Scope.enter();
      fr = parse_test_file(fname);
      int idx = prep.prep(fr); // hack frame per-test
      if (family == Distributions.Family.bernoulli || family == Distributions.Family.multinomial) {
        if (!fr.vecs()[idx].isEnum()) {
          Scope.track(fr.replace(idx, fr.vecs()[idx].toEnum())._key);
        }
      }
      DKV.put(fr);             // Update frame after hacking it

      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      if( idx < 0 ) idx = ~idx;
      parms._train = fr._key;
      parms._response_column = fr._names[idx];
      parms._ntrees = 4;
      parms._distribution = family;
      parms._max_depth = 4;
      parms._min_rows = 1;
      parms._nbins = 50;
      parms._learn_rate = .2f;
      parms._score_each_iteration = true;
      if( validation ) {        // Make a validation frame thats a clone of the training data
        vfr = new Frame(fr);
        DKV.put(vfr);
        parms._valid = vfr._key;
      }

      GBM job = null;
      try {
        job = new GBM(parms);
        gbm = job.trainModel().get();
      } finally {
        if( job != null ) job.remove();
      }

      // Done building model; produce a score column with predictions
      fr2 = gbm.score(fr);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(fr,fr2,1e-15));

      Assert.assertTrue(job._state == water.Job.JobState.DONE); //HEX-1817
      //Assert.assertTrue(gbm._output._state == Job.JobState.DONE); //HEX-1817
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
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    try {
      Scope.enter();
      parms._valid = parse_test_file("smalldata/gbm_test/ecology_eval.csv")._key;
      Frame  train = parse_test_file("smalldata/gbm_test/ecology_model.csv");
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");    // Convert response to categorical
      Scope.track(train.replace(ci, train.vecs()[ci].toEnum())._key);
      DKV.put(train);                    // Update frame after hacking it
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._ntrees = 5;
      parms._max_depth = 5;
      parms._min_rows = 10;
      parms._nbins = 100;
      parms._learn_rate = .2f;
      parms._distribution = Distributions.Family.multinomial;

      GBM job = null;
      try {
        job = new GBM(parms);
        gbm = job.trainModel().get();
      } finally {
        if( job != null ) job.remove();
      }

      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(gbm,parms.valid());
      double auc = mm._auc._auc;
      Assert.assertTrue(0.84 <= auc && auc < 0.86); // Sanely good model
      double[][] cm = mm._auc.defaultCM();
      Assert.assertArrayEquals(ard(ard(315, 78), ard(26, 81)), cm);
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
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    Frame pred=null, res=null;
    Scope.enter();
    try {
      Frame train = parse_test_file("smalldata/gbm_test/ecology_model.csv");
      train.remove("Site").remove();     // Remove unique ID
      int ci = train.find("Angaus");
      Scope.track(train.replace(ci, train.vecs()[ci].toEnum())._key);   // Convert response 'Angaus' to categorical
      DKV.put(train);                    // Update frame after hacking it
      parms._train = train._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._distribution = Distributions.Family.multinomial;

      GBM job = new GBM(parms);
      gbm = job.trainModel().get();
      job.remove();

      pred = parse_test_file("smalldata/gbm_test/ecology_eval.csv" );
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

  // Adapt a trained model to a test dataset with different enums
  @Test public void testModelAdaptMultinomial() {
    GBM job = null;
    GBMModel gbm = null;
    GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
    try {
      Scope.enter();
      Frame v;
      parms._train = (  parse_test_file("smalldata/junit/mixcat_train.csv"))._key;
      parms._valid = (v=parse_test_file("smalldata/junit/mixcat_test.csv" ))._key;
      parms._response_column = "Response"; // Train on the outcome
      parms._ntrees = 1; // Build a CART tree - 1 tree, full learn rate, down to 1 row
      parms._learn_rate = 1.0f;
      parms._min_rows = 1;
      parms._distribution = Distributions.Family.multinomial;

      job = new GBM(parms);
      gbm = job.trainModel().get();

      Frame res = gbm.score(v);

      int[] ps = new int[(int)v.numRows()];
      for( int i=0; i<ps.length; i++ ) ps[i] = (int)res.vecs()[0].at8(i);
      // Expected predictions are X,X,Y,Y,X,Y,Z,X,Y
      // Never predicts W, the extra class in the test set.
      // Badly predicts Z because 1 tree does not pick up that feature#2 can also
      // be used to predict Z, and instead relies on factor C which does not appear
      // in the test set.
      Assert.assertArrayEquals("", ps, new int[]{1, 1, 2, 2, 1, 2, 3, 1, 2});

      hex.ModelMetricsMultinomial mm = hex.ModelMetricsMultinomial.getFromDKV(gbm,parms.valid());
      Assert.assertTrue(mm.r2() > 0.5);

      // Build a POJO, validate same results
      Assert.assertTrue(gbm.testJavaScoring(v,res,1e-15));

      res.remove();

    } finally {
      parms._train.remove();
      parms._valid.remove();
      if( gbm != null ) gbm.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }

  // A test of locking the input dataset during model building.
  @Test public void testModelLock() {
    GBM gbm=null;
    Frame fr=null;
    Scope.enter();
    try {
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      fr = parse_test_file("smalldata/gbm_test/ecology_model.csv");
      fr.remove("Site").remove();        // Remove unique ID
      int ci = fr.find("Angaus");
      Scope.track(fr.replace(ci, fr.vecs()[ci].toEnum())._key);   // Convert response 'Angaus' to categorical
      DKV.put(fr);                       // Update after hacking
      parms._train = fr._key;
      parms._response_column = "Angaus"; // Train on the outcome
      parms._ntrees = 10;
      parms._max_depth = 10;
      parms._min_rows = 1;
      parms._nbins = 20;
      parms._learn_rate = .2f;
      parms._distribution = Distributions.Family.multinomial;
      gbm = new GBM(parms);
      gbm.trainModel();
      try { Thread.sleep(50); } catch( Exception ignore ) { }

      try {
        Log.info("Trying illegal frame delete.");
        fr.delete();            // Attempted delete while model-build is active
        Assert.fail("Should toss IAE instead of reaching here");
      } catch( IllegalArgumentException ignore ) {
      } catch( DException.DistributedException de ) {
        assertTrue( de.getMessage().contains("java.lang.IllegalArgumentException") );
      }

      Log.info("Getting model");
      GBMModel model = gbm.get();
      Assert.assertTrue(gbm._state == Job.JobState.DONE); //HEX-1817
      if( model != null ) model.delete();

    } finally {
      if( fr  != null ) fr .remove();
      if( gbm != null ) gbm.remove();             // Remove GBM Job
      Scope.exit();
    }
  }

  //  MSE generated by GBM with/without validation dataset should be same
  @Test public void testModelScoreKeeperEqualityOnProstateBernoulli() {
    final PrepData prostatePrep = new PrepData() { @Override int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("CAPSULE"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, false, Distributions.Family.bernoulli)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, true , Distributions.Family.bernoulli)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnProstateGaussian() {
    final PrepData prostatePrep = new PrepData() { @Override int prep(Frame fr) { fr.remove("ID").remove(); return ~fr.find("CAPSULE"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, false, Distributions.Family.gaussian)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, true , Distributions.Family.gaussian)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnTitanicBernoulli() {
    final PrepData titanicPrep = new PrepData() { @Override int prep(Frame fr) { return fr.find("survived"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, false, Distributions.Family.bernoulli)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, true , Distributions.Family.bernoulli)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnTitanicMultinomial() {
    final PrepData titanicPrep = new PrepData() { @Override int prep(Frame fr) { return fr.find("survived"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, false, Distributions.Family.multinomial)._scored_train;
    ScoreKeeper[] scoredWithVal = basicGBM("./smalldata/junit/titanic_alt.csv", titanicPrep, true , Distributions.Family.multinomial)._scored_valid;
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testModelScoreKeeperEqualityOnProstateMultinomial() {
    final PrepData prostatePrep = new PrepData() { @Override int prep(Frame fr) { fr.remove("ID").remove(); return fr.find("RACE"); } };
    ScoreKeeper[] scoredWithoutVal = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, false, Distributions.Family.multinomial)._scored_train;
    ScoreKeeper[] scoredWithVal    = basicGBM("./smalldata/logreg/prostate.csv", prostatePrep, true , Distributions.Family.multinomial)._scored_valid;
    // FIXME: 0-tree scores don't match between WithoutVal and WithVal for multinomial
    scoredWithoutVal = Arrays.copyOfRange(scoredWithoutVal, 1, scoredWithoutVal.length);
    scoredWithVal = Arrays.copyOfRange(scoredWithVal, 1, scoredWithVal.length);
    Assert.assertArrayEquals("GBM has to report same list of MSEs for run without/with validation dataset (which is equal to training data)", scoredWithoutVal, scoredWithVal);
  }

  @Test public void testBigCat() {
    final PrepData prep = new PrepData() { @Override int prep(Frame fr) { return fr.find("y"); } };
    basicGBM("./smalldata/gbm_test/50_cattest_test.csv" , prep, false, Distributions.Family.bernoulli);
    basicGBM("./smalldata/gbm_test/50_cattest_train.csv", prep, false, Distributions.Family.bernoulli);
    basicGBM("./smalldata/gbm_test/swpreds_1000x3.csv", prep, false, Distributions.Family.bernoulli);
  }

  // Test uses big data and is too slow for a pre-push
  @Test @Ignore public void testKDDTrees() {
    Frame tfr=null, vfr=null;
    String[] cols = new String[] {"DOB", "LASTGIFT", "TARGET_D"};
    try {
      // Load data, hack frames
      Frame inF1 = parse_test_file("bigdata/laptop/usecases/cup98LRN_z.csv");
      Frame inF2 = parse_test_file("bigdata/laptop/usecases/cup98VAL_z.csv");
      tfr = inF1.subframe(cols); // Just the columns to train on
      vfr = inF2.subframe(cols);
      inF1.remove(cols).remove(); // Toss all the rest away
      inF2.remove(cols).remove();
      tfr.replace(0, tfr.vec("DOB").toEnum());     // Convert 'DOB' to enum
      vfr.replace(0, vfr.vec("DOB").toEnum());
      DKV.put(tfr);
      DKV.put(vfr);

      // Same parms for all
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "TARGET_D";
      parms._ntrees = 3;
      parms._distribution = Distributions.Family.gaussian;
      // Build a first model; all remaining models should be equal
      GBM job1 = new GBM(parms);
      GBMModel gbm1 = job1.trainModel().get();
      job1.remove();
      // Validation MSE should be equal
      ScoreKeeper[] firstScored = gbm1._output._scored_valid;

      // Build 10 more models, checking for equality
      for( int i=0; i<10; i++ ) {
        GBM job2 = new GBM(parms);
        GBMModel gbm2 = job2.trainModel().get();
        job2.remove();
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
      tfr = parse_test_file("bigdata/laptop/mnist/train.csv.gz");
      Scope.track(tfr.replace(784, tfr.vecs()[784].toEnum())._key);   // Convert response 'C785' to categorical
      DKV.put(tfr);

      vfr = parse_test_file("bigdata/laptop/mnist/test.csv.gz");
      Scope.track(vfr.replace(784, vfr.vecs()[784].toEnum())._key);   // Convert response 'C785' to categorical
      DKV.put(vfr);

      // Same parms for all
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "C785";
      parms._ntrees = 2;
      parms._max_depth = 4;
      parms._distribution = Distributions.Family.multinomial;
      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      GBMModel gbm = job.trainModel().get();

      Frame pred = gbm.score(vfr);
      double sq_err = new CompErr().doAll(vfr.lastVec(),pred.vecs()[0])._sum;
      double mse = sq_err/pred.numRows();
      assertEquals(3.0199, mse, 1e-15); //same results
      job.remove();
      gbm.delete();
    } finally {
      if (tfr  != null) tfr.remove();
      if (vfr  != null) vfr.remove();
      Scope.exit();
    }
  }

  // HEXDEV-194: Check reproducibility for the same # of chunks (i.e., same # of nodes) and same parameters
  @Test public void testReprodubility() {
    Frame tfr=null;
    final int N = 5;
    double[] mses = new double[N];

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parse_test_file("smalldata/covtype/covtype.20k.data");

      // rebalance to 256 chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, 256);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toEnum())._key);
//      DKV.put(tfr);

      for (int i=0; i<N; ++i) {
        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = tfr._key;
        parms._response_column = "C55";
        parms._nbins = 1000;
        parms._ntrees = 1;
        parms._max_depth = 8;
        parms._learn_rate = 0.1f;
        parms._min_rows = 10;
//        parms._distribution = Family.multinomial;
        parms._distribution = Distributions.Family.gaussian;

        // Build a first model; all remaining models should be equal
        GBM job = new GBM(parms);
        GBMModel gbm = job.trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        job.remove();
        gbm.delete();
      }
    } finally{
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    for( double mse : mses ) assertEquals(mse, mses[0], 1e-15);
  }

  // PUBDEV-557: Test dependency on # nodes (for small number of bins, but fixed number of chunks)
  @Test public void testReprodubilityAirline() {
    Frame tfr=null;
    final int N = 1;
    double[] mses = new double[N];

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");

      // rebalance to fixed number of chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, 256);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toEnum())._key);
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
        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = tfr._key;
        parms._response_column = "IsDepDelayed";
        parms._nbins = 10;
        parms._nbins_cats = 500;
        parms._ntrees = 7;
        parms._max_depth = 5;
        parms._min_rows = 10;
        parms._distribution = Distributions.Family.bernoulli;
        parms._balance_classes = true;
        parms._seed = 0;

        // Build a first model; all remaining models should be equal
        GBM job = new GBM(parms);
        GBMModel gbm = job.trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        job.remove();
        gbm.delete();
      }
    } finally {
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    for( double mse : mses )
      assertEquals(0.21925349482557605, mse, 1e-8); //check for the same result on 1 nodes and 5 nodes (will only work with enough chunks)
  }

  @Test public void testReprodubilityAirlineSingleNode() {
    Frame tfr=null;
    final int N = 1;
    double[] mses = new double[N];

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");

      // rebalance to fixed number of chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, 256);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toEnum())._key);
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
        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = tfr._key;
        parms._response_column = "IsDepDelayed";
        parms._nbins = 10;
        parms._nbins_cats = 500;
        parms._ntrees = 7;
        parms._max_depth = 5;
        parms._min_rows = 10;
        parms._distribution = Distributions.Family.bernoulli;
        parms._balance_classes = true;
        parms._seed = 0;
        parms._build_tree_one_node = true;

        // Build a first model; all remaining models should be equal
        GBM job = new GBM(parms);
        GBMModel gbm = job.trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        job.remove();
        gbm.delete();
      }
    } finally {
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    for( double mse : mses )
      assertEquals(0.21925349482557605, mse, 1e-8); //check for the same result on 1 nodes and 5 nodes (will only work with enough chunks)
  }

  // HEXDEV-223
  @Test public void testCategorical() {
    Frame tfr=null;
    final int N = 1;
    double[] mses = new double[N];

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/gbm_test/alphabet_cattest.csv");
      Scope.track(tfr.replace(1, tfr.vecs()[1].toEnum())._key);
      DKV.put(tfr);
      for (int i=0; i<N; ++i) {
        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = tfr._key;
        parms._response_column = "y";
        parms._ntrees = 1;
        parms._max_depth = 1;
        parms._learn_rate = 1;
        parms._distribution = Distributions.Family.bernoulli;

        // Build a first model; all remaining models should be equal
        GBM job = new GBM(parms);
        GBMModel gbm = job.trainModel().get();
        assertEquals(gbm._output._ntrees, parms._ntrees);

        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(gbm,parms.train());
        double auc = mm._auc._auc;
        Assert.assertTrue(1 == auc);

        mses[i] = gbm._output._scored_train[gbm._output._scored_train.length-1]._mse;
        job.remove();
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
    Frame tfr=null, vfr=null;
    GBMModel gbm=null;
    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parse_test_file("./bigdata/covktr.csv");
      vfr = parse_test_file("./bigdata/covkts.csv");
      int idx = tfr.find("V55");
      Scope.track(tfr.replace(idx, tfr.vecs()[idx].toEnum())._key);
      Scope.track(vfr.replace(idx, vfr.vecs()[idx].toEnum())._key);
      DKV.put(tfr);
      DKV.put(vfr);

      // Build model
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "V55";
      parms._ntrees = 10;
      parms._max_depth = 1;
      parms._nbins = 20;
      parms._min_rows = 10;
      parms._learn_rate = 0.01f;
      parms._distribution = Distributions.Family.multinomial;
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();
      job.remove();

      // Report AUC from training
      hex.ModelMetricsBinomial tmm = hex.ModelMetricsBinomial.getFromDKV(gbm,tfr);
      hex.ModelMetricsBinomial vmm = hex.ModelMetricsBinomial.getFromDKV(gbm,vfr);
      double t_auc = tmm._auc._auc;
      double v_auc = vmm._auc._auc;
      System.out.println("train_AUC= "+t_auc+" , validation_AUC= "+v_auc);

      // Report AUC from scoring
      Frame t_pred = gbm.score(tfr);
      Frame v_pred = gbm.score(vfr);
      hex.ModelMetricsBinomial tmm2 = hex.ModelMetricsBinomial.getFromDKV(gbm,tfr);
      hex.ModelMetricsBinomial vmm2 = hex.ModelMetricsBinomial.getFromDKV(gbm,vfr);
      assert tmm != tmm2;
      assert vmm != vmm2;
      double t_auc2 = tmm._auc._auc;
      double v_auc2 = vmm._auc._auc;
      System.out.println("train_AUC2= "+t_auc2+" , validation_AUC2= "+v_auc2);

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
      if (gbm  != null) gbm.delete();
      Scope.exit();
    }
  }
  static double _AUC = 1;
  static double _MSE = 0.24850374695598948;
  static double _R2 = 0.005985012176042082;
  static double _LogLoss = 0.690155;

  @Test
  public void testNoRowWeights() {
    Frame tfr = null, vfr = null;
    GBMModel gbm = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/no_weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_R2, mm2.r2(), 1e-6);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);

      job.remove();
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
      tfr = parse_test_file("smalldata/junit/weights_all_ones.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_R2, mm2.r2(), 1e-6);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);

      job.remove();
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
      tfr = parse_test_file("smalldata/junit/weights_all_twos.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 2; //Must be adapted to the weights
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_R2, mm2.r2(), 1e-6);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);

      job.remove();
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
      tfr = parse_test_file("smalldata/junit/weights_all_tiny.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 0.01242; //Must be adapted to the weights
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_R2, mm2.r2(), 1e-6);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);

      job.remove();
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
      tfr = parse_test_file("smalldata/junit/no_weights_shuffled.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_R2, mm2.r2(), 1e-6);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);

      job.remove();
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
      tfr = parse_test_file("smalldata/junit/weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._training_metrics;
      assertEquals(_AUC, mm.auc()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

      gbm.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(gbm, parms.train());
      assertEquals(_AUC, mm2.auc()._auc, 1e-8);
      assertEquals(_MSE, mm2.mse(), 1e-8);
      assertEquals(_R2, mm2.r2(), 1e-6);
      assertEquals(_LogLoss, mm2.logloss(), 1e-6);

      job.remove();
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
      tfr = parse_test_file("smalldata/junit/weights.csv");
      DKV.put(tfr);
      GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 0xdecaf;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._nfolds = 2;
      parms._ntrees = 3;
      parms._learn_rate = 1e-3f;

      // Build a first model; all remaining models should be equal
      GBM job = new GBM(parms);
      gbm = job.trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)gbm._output._validation_metrics;
      assertEquals(0.55555555555, mm.auc()._auc, 1e-8);
      assertEquals(0.3313375036935877, mm.mse(), 1e-8);
      assertEquals(-0.3253500147743509, mm.r2(), 1e-6);
      assertEquals(0.8630781835948022, mm.logloss(), 1e-6);

      job.remove();
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (gbm != null) gbm.delete();
      Scope.exit();
    }
  }
}
