package hex.tree.drf;


import hex.Model;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsRegression;
import hex.SplitFrame;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.Triple;
import water.util.VecUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class DRFTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  abstract static class PrepData { abstract int prep(Frame fr); }

  static String[] s(String...arr)  { return arr; }
  static long[]   a(long ...arr)   { return arr; }
  static long[][] a(long[] ...arr) { return arr; }

  @Test public void testClassIris1() throws Throwable {

    // iris ntree=1
    // the DRF should  use only subset of rows since it is using oob validation
    basicDRFTestOOBE_Classification(
            "./smalldata/iris/iris.csv", "iris.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.numCols() - 1;
              }
            },
            1,
            20,
            1,
            20,
            ard(ard(15, 0, 0),
                    ard(0, 18, 0),
                    ard(0, 1, 17)),
            s("Iris-setosa", "Iris-versicolor", "Iris-virginica"));

  }

  @Test public void testClassIris5() throws Throwable {
    // iris ntree=50
    basicDRFTestOOBE_Classification(
            "./smalldata/iris/iris.csv", "iris5.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.numCols() - 1;
              }
            },
            5,
            20,
            1,
            20,
            ard(ard(43, 0, 0),
                    ard(0, 37, 4),
                    ard(0, 4, 39)),
            s("Iris-setosa", "Iris-versicolor", "Iris-virginica"));
  }

  @Test public void testClassCars1() throws Throwable {
    // cars ntree=1
    basicDRFTestOOBE_Classification(
            "./smalldata/junit/cars.csv", "cars.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("name").remove();
                return fr.find("cylinders");
              }
            },
            1,
            20,
            1,
            20,
            ard(ard(0, 1, 1, 0, 0),
                    ard(0, 62, 6, 0, 0),
                    ard(0, 0, 1, 0, 0),
                    ard(1, 2, 2, 28, 1),
                    ard(0, 0, 2, 2, 35)),
            s("3", "4", "5", "6", "8"));
  }

  @Test public void testClassCars5() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/junit/cars.csv", "cars5.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("name").remove();
                return fr.find("cylinders");
              }
            },
            5,
            20,
            1,
            20,
            ard(ard(1, 2, 0, 0, 0),
                    ard(0, 174, 6,  3, 0),
                    ard(0, 2, 0, 0, 0),
                    ard(2, 4, 1, 67, 1),
                    ard(0, 0, 1, 2, 83)),
            s("3", "4", "5", "6", "8"));
  }

  @Test public void testConstantCols() throws Throwable {
    try {
      basicDRFTestOOBE_Classification(
              "./smalldata/poker/poker100", "poker.hex",
              new PrepData() {
                @Override
                int prep(Frame fr) {
                  for (int i = 0; i < 7; i++) {
                    fr.remove(3).remove();
                  }
                  return 3;
                }
              },
              1,
              20,
              1,
              20,
              null,
              null);
      Assert.fail();
    } catch( H2OModelBuilderIllegalArgumentException iae ) {
    /*pass*/
    }
  }

  @Ignore @Test public void testBadData() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/junit/drf_infinities.csv", "infinitys.hex",
            new PrepData() { @Override int prep(Frame fr) { return fr.find("DateofBirth"); } },
            1,
            20,
            1,
            20,
            ard(ard(6, 0),
                    ard(9, 1)),
            s("0", "1"));
  }

  //@Test
  public void testCreditSample1() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/kaggle/creditsample-training.csv.gz", "credit.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("MonthlyIncome").remove();
                return fr.find("SeriousDlqin2yrs");
              }
            },
            1,
            20,
            1,
            20,
            ard(ard(46294, 202),
                    ard(3187, 107)),
            s("0", "1"));

  }

  @Test public void testCreditProstate1() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/logreg/prostate.csv", "prostate.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("CAPSULE");
              }
            },
            1,
            20,
            1,
            20,
            ard(ard(0, 70),
                    ard(0, 59)),
            s("0", "1"));

  }

  @Test public void testCreditProstateRegression1() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            1,
            20,
            1,
            10,
            59.87077260106929
    );

  }

  @Test public void testCreditProstateRegression5() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression5.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            5,
            20,
            1,
            10,
            58.857160962841164
    );

  }

  @Test public void testCreditProstateRegression50() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression50.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            50,
            20,
            1,
            10,
            49.42453594627541
    );

  }
  @Test public void testCzechboard() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/gbm_test/czechboard_300x300.csv", "czechboard_300x300.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                Vec resp = fr.remove("C2");
                fr.add("C2", VecUtils.toCategoricalVec(resp));
                resp.remove();
                return fr.find("C3");
              }
            },
            50,
            20,
            1,
            20,
            ard(ard(0, 45000),
                    ard(0, 45000)),
            s("0", "1"));
  }

  @Test public void testProstate() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/prostate/prostate.csv.zip", "prostate2.zip.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                String[] names = fr.names().clone();
                Vec[] en = fr.remove(new int[]{1,4,5,8});
                fr.add(names[1], VecUtils.toCategoricalVec(en[0])); //CAPSULE
                fr.add(names[4], VecUtils.toCategoricalVec(en[1])); //DPROS
                fr.add(names[5], VecUtils.toCategoricalVec(en[2])); //DCAPS
                fr.add(names[8], VecUtils.toCategoricalVec(en[3])); //GLEASON
                for (Vec v : en) v.remove();
                fr.remove(0).remove(); //drop ID
                return 4; //CAPSULE
              }
            },
            4, //ntrees
            2, //bins
            1, //min_rows
            1, //max_depth
            null,
            s("0", "1"));
  }

  @Test public void testAlphabet() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/gbm_test/alphabet_cattest.csv", "alphabetClassification.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("y");
              }
            },
            1,
            20,
            1,
            20,
            ard(ard(670, 0),
                    ard(0, 703)),
            s("0", "1"));
  }
  @Test public void testAlphabetRegression() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/gbm_test/alphabet_cattest.csv", "alphabetRegression.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("y");
              }
            },
            1,
            20,
            1,
            10,
            0.0);
  }

  @Ignore  //1-vs-5 node discrepancy (parsing into different number of chunks?)
  @Test public void testAirlines() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/airlines/allyears2k_headers.zip", "airlines.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                for (String s : new String[]{
                        "DepTime", "ArrTime", "ActualElapsedTime",
                        "AirTime", "ArrDelay", "DepDelay", "Cancelled",
                        "CancellationCode", "CarrierDelay", "WeatherDelay",
                        "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
                }) {
                  fr.remove(s).remove();
                }
                return fr.find("IsDepDelayed");
              }
            },
            7,
            20, 1, 20, ard(ard(7958, 11707), //1-node
                    ard(2709, 19024)),
//          a(a(7841, 11822), //5-node
//            a(2666, 19053)),
            s("NO", "YES"));
  }



  // Put response as the last vector in the frame and return possible frames to clean up later
  // Also fill DRF.
  static Vec unifyFrame(DRFModel.DRFParameters drf, Frame fr, PrepData prep, boolean classification) {
    int idx = prep.prep(fr);
    if( idx < 0 ) { idx = ~idx; }
    String rname = fr._names[idx];
    drf._response_column = fr.names()[idx];

    Vec resp = fr.vecs()[idx];
    Vec ret = null;
    if (classification) {
      ret = fr.remove(idx);
      fr.add(rname, VecUtils.toCategoricalVec(resp));
    } else {
      fr.remove(idx);
      fr.add(rname,resp);
    }
    return ret;
  }

  public void basicDRFTestOOBE_Classification(String fnametrain, String hexnametrain, PrepData prep, int ntree, int nbins, int min_rows, int max_depth, double[][] expCM, String[] expRespDom) throws Throwable {
    basicDRF(fnametrain, hexnametrain, null, prep, ntree, max_depth, nbins, true, min_rows, expCM, -1, expRespDom);
  }
  public void basicDRFTestOOBE_Regression(String fnametrain, String hexnametrain, PrepData prep, int ntree, int nbins, int min_rows, int max_depth, double expMSE) throws Throwable {
    basicDRF(fnametrain, hexnametrain, null, prep, ntree, max_depth, nbins, false, min_rows, null, expMSE, null);
  }

  public void basicDRF(String fnametrain, String hexnametrain, String fnametest, PrepData prep, int ntree, int max_depth, int nbins, boolean classification, int min_rows, double[][] expCM, double expMSE, String[] expRespDom) throws Throwable {
    Scope.enter();
    DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
    Frame frTest = null, pred = null;
    Frame frTrain = null;
    Frame test = null, res = null;
    DRFModel model = null;
    try {
      frTrain = parse_test_file(fnametrain);
      Vec removeme = unifyFrame(drf, frTrain, prep, classification);
      if (removeme != null) Scope.track(removeme);
      DKV.put(frTrain._key, frTrain);
      // Configure DRF
      drf._train = frTrain._key;
      drf._response_column = ((Frame)DKV.getGet(drf._train)).lastVecName();
      drf._ntrees = ntree;
      drf._max_depth = max_depth;
      drf._min_rows = min_rows;
      drf._stopping_rounds = 0; //no early stopping
//      drf._binomial_double_trees = new Random().nextBoolean();
      drf._nbins = nbins;
      drf._nbins_cats = nbins;
      drf._mtries = -1;
      drf._sample_rate = 0.66667f;   // Simulated sampling with replacement
      drf._seed = (1L<<32)|2;

      // Invoke DRF and block till the end
      DRF job = new DRF(drf);
      // Get the model
      model = job.trainModel().get();
      Log.info(model._output);
      Assert.assertTrue(job.isStopped()); //HEX-1817

      hex.ModelMetrics mm;
      if (fnametest != null) {
        frTest = parse_test_file(fnametest);
        pred = model.score(frTest);
        mm = hex.ModelMetrics.getFromDKV(model, frTest);
        // Check test set CM
      } else {
        mm = hex.ModelMetrics.getFromDKV(model, frTrain);
      }
      Assert.assertEquals("Number of trees differs!", ntree, model._output._ntrees);

      test = parse_test_file(fnametrain);
      res = model.score(test);

      // Build a POJO, validate same results
      Assert.assertTrue(model.testJavaScoring(test,res,1e-15));

      if (classification && expCM != null) {
        Assert.assertTrue("Expected: " + Arrays.deepToString(expCM) + ", Got: " + Arrays.deepToString(mm.cm()._cm),
                Arrays.deepEquals(mm.cm()._cm, expCM));

        String[] cmDom = model._output._domains[model._output._domains.length - 1];
        Assert.assertArrayEquals("CM domain differs!", expRespDom, cmDom);
        Log.info("\nOOB Training CM:\n" + mm.cm().toASCII());
        Log.info("\nTraining CM:\n" + hex.ModelMetrics.getFromDKV(model, test).cm().toASCII());
      } else if (!classification) {
        Assert.assertTrue("Expected: " + expMSE + ", Got: " + mm.mse(), expMSE == mm.mse());
        Log.info("\nOOB Training MSE: " + mm.mse());
        Log.info("\nTraining MSE: " + hex.ModelMetrics.getFromDKV(model, test).mse());
      }

      hex.ModelMetrics.getFromDKV(model, test);

    } finally {
      if (frTrain!=null) frTrain.remove();
      if (frTest!=null) frTest.remove();
      if( model != null ) model.delete(); // Remove the model
      if( pred != null ) pred.delete();
      if( test != null ) test.delete();
      if( res != null ) res.delete();
      Scope.exit();
    }
  }
  
  @Ignore
  @Test public void testAutoRebalance() {
    
    //First pass to warm up
    boolean warmUp = true;
    if (warmUp) {
      int[] warmUpChunks = {1, 2, 3, 4, 5};
      for (int chunk : warmUpChunks) {
        Frame tfr = null;

        Scope.enter();
        try {
          // Load data, hack frames
          tfr = parse_test_file("/Users/ludirehak/Downloads/train.csv.zip");

          DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
          parms._train = tfr._key;
          parms._response_column = "Sales";
          parms._nbins = 1000;
          parms._ntrees = 10;
          parms._max_depth = 20;
          parms._mtries = -1;
          parms._min_rows = 10;
          parms._seed = 1234;
//          parms._rebalance_me = true;
//          parms._nchunks = 22;

          // Build a first model; all remaining models should be equal
          DRF job = new DRF(parms);
          DRFModel drf = job.trainModel().get();
          drf.delete();

        } finally {
          if (tfr != null) tfr.remove();
        }
        Scope.exit();
      }
    }
    
    
    int[] max_depths = {2,5,10,15,20};
    int[] chunks = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32};
    boolean[] rebalanceMes = {true};
    int[] ntrees = {10};
    
    int totalLength = chunks.length*max_depths.length*rebalanceMes.length*ntrees.length;
    double[] executionTimes = new double[totalLength];
    int[] outputchunks = new int[totalLength];
    int[] outputdepths = new int[totalLength];
    boolean[] outputrebalanceme = new boolean[totalLength];
    int[] outputntrees = new int[totalLength];
    double[] R2 = new double[totalLength];
    int c = 0;
    for (int max_depth : max_depths) {
      for (int ntree: ntrees) {
        for (boolean rebalanceMe: rebalanceMes) {
          for (int chunk : chunks) {
            long startTime = System.currentTimeMillis();
            Scope.enter();
            // Load data, hack frames
            Frame tfr = parse_test_file("/Users/ludirehak/Downloads/train.csv.zip");

            DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
            parms._train = tfr._key;
            parms._response_column = "Sales";
            parms._nbins = 1000;
            parms._mtries = -1;
            parms._min_rows = 10;
            parms._seed = 1234;
            
            parms._ntrees = ntree;
            parms._max_depth = max_depth;
//            parms._rebalance_me = rebalanceMe;
//            parms._nchunks = chunk;
            
            // Build a first model
            DRF job = new DRF(parms);
            DRFModel drf = job.trainModel().get();
            assertEquals(drf._output._ntrees, parms._ntrees);
            ModelMetricsRegression mm = (ModelMetricsRegression) drf._output._training_metrics;
            R2[c] = (double) Math.round(mm.r2() * 10000d) / 10000d;
            int actualChunk = job.train().anyVec().nChunks();
            drf.delete();

            tfr.remove();

            Scope.exit();
            executionTimes[c] = (System.currentTimeMillis() - startTime) / 1000d;
            if (!rebalanceMe) assert actualChunk == 22;
            outputchunks[c] = actualChunk;
            outputdepths[c] = max_depth;
            outputrebalanceme[c] = rebalanceMe;
            outputntrees[c] = drf._output._ntrees;
            Log.info("Iteration " + (c + 1) + " out of " + executionTimes.length);
            Log.info(" DEPTH: " + outputdepths[c] + " NTREES: "+ outputntrees[c] + " CHUNKS: " + outputchunks[c] + " EXECUTION TIME: " + executionTimes[c] + " R2: " + R2[c] + " Rebalanced: " + rebalanceMe + " WarmedUp: " + warmUp);
            c++;
          }
        }
      }
    }
    String fileName = "/Users/ludirehak/Desktop/DRFTestRebalance3.txt";
    //R code for plotting: plot(chunks,execution_time,t='n',main='Execution Time of DRF on Rebalanced Data');
    // for (i in 1:length(unique(max_depth))) {s = which(max_depth ==unique(max_depth)[i]); 
    // points(chunks[s],execution_time[s],col=i)};
    // legend('topright', legend= c('max_depth',unique(max_depth)),col = 0:length(unique(max_depth)),pch=1);
    try {
      FileWriter fileWriter = new FileWriter(fileName);
      BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
      bufferedWriter.write("max_depth,ntrees,nbins,min_rows,chunks,execution_time,r2,rebalanceMe,warmUp");
      bufferedWriter.newLine();
      for (int i = 0; i < executionTimes.length; i++) {
        bufferedWriter.write(outputdepths[i] +"," + outputntrees[i] + "," + 1000 + ","+ 10 + "," + outputchunks[i] + "," + executionTimes[i] +"," +R2[i] +","+(outputrebalanceme[i]? 1:0)+","+(warmUp?1:0));
        bufferedWriter.newLine();
      }
      bufferedWriter.close();
    } catch (Exception e) {
      Log.info("Fail");
    }

  }
  
  // PUBDEV-2476 Check reproducibility for the same # of chunks (i.e., same # of nodes) and same parameters
  @Test public void testChunks() {
    Frame tfr;
    final int N = 4;
    double[] mses = new double[N];
    int[] chunks = new int[]{1,13,19,39,500};

    for (int i=0; i<N; ++i) {
      Scope.enter();
      // Load data, hack frames
      tfr = parse_test_file("smalldata/covtype/covtype.20k.data");

      // rebalance to 256 chunks
      Key dest = Key.make("df.rebalanced.hex");
      RebalanceDataSet rb = new RebalanceDataSet(tfr, dest, chunks[i]);
      H2O.submitTask(rb);
      rb.join();
      tfr.delete();
      tfr = DKV.get(dest).get();
      Scope.track(tfr.replace(54, tfr.vecs()[54].toCategoricalVec()));
      DKV.put(tfr);

      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "C55";
      parms._ntrees = 10;
      parms._seed = 1234;

      // Build a first model; all remaining models should be equal
      DRF job = new DRF(parms);
      DRFModel drf = job.trainModel().get();
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

  //
  @Test public void testReproducibility() {
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
//      Scope.track(tfr.replace(54, tfr.vecs()[54].toCategoricalVec())._key);
//      DKV.put(tfr);

      for (int i=0; i<N; ++i) {
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = tfr._key;
        parms._response_column = "C55";
        parms._nbins = 1000;
        parms._ntrees = 1;
        parms._max_depth = 8;
        parms._mtries = -1;
        parms._min_rows = 10;
        parms._seed = 1234;

        // Build a first model; all remaining models should be equal
        DRFModel drf = new DRF(parms).trainModel().get();
        assertEquals(drf._output._ntrees, parms._ntrees);

        mses[i] = drf._output._scored_train[drf._output._scored_train.length-1]._mse;
        drf.delete();
      }
    } finally{
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    for (int i=0; i<mses.length; ++i) {
      Log.info("trial: " + i + " -> MSE: " + mses[i]);
    }
    for(double mse : mses)
      assertEquals(mse, mses[0], 1e-15);
  }

  // PUBDEV-557 Test dependency on # nodes (for small number of bins, but fixed number of chunks)
  @Test public void testReproducibilityAirline() {
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
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = tfr._key;
        parms._response_column = "IsDepDelayed";
        parms._nbins = 10;
        parms._nbins_cats = 1024;
        parms._ntrees = 7;
        parms._max_depth = 10;
        parms._binomial_double_trees = false;
        parms._mtries = -1;
        parms._min_rows = 1;
        parms._sample_rate = 0.632f;   // Simulated sampling with replacement
        parms._balance_classes = true;
        parms._seed = (1L<<32)|2;

        // Build a first model; all remaining models should be equal
        DRFModel drf = new DRF(parms).trainModel().get();
        assertEquals(drf._output._ntrees, parms._ntrees);

        mses[i] = drf._output._training_metrics.mse();
        drf.delete();
      }
    } finally{
      if (tfr != null) tfr.remove();
    }
    Scope.exit();
    for (int i=0; i<mses.length; ++i) {
      Log.info("trial: " + i + " -> MSE: " + mses[i]);
    }
    for (int i=0; i<mses.length; ++i) {
      assertEquals(0.21488096730810302, mses[i], 1e-4); //check for the same result on 1 nodes and 5 nodes
    }
  }

  // HEXDEV-319
  @Ignore
  @Test public void testAirline() {
    Frame tfr=null;
    Frame test=null;

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parse_test_file(Key.make("air.hex"), "/users/arno/sz_bench_data/train-1m.csv");
      test = parse_test_file(Key.make("airt.hex"), "/users/arno/sz_bench_data/test.csv");
//      for (int i : new int[]{0,1,2}) {
//        tfr.vecs()[i] = tfr.vecs()[i].toCategoricalVec();
//        test.vecs()[i] = test.vecs()[i].toCategoricalVec();
//      }

      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._valid = test._key;
      parms._ignored_columns = new String[]{"Origin","Dest"};
//      parms._ignored_columns = new String[]{"UniqueCarrier","Origin","Dest"};
//      parms._ignored_columns = new String[]{"UniqueCarrier","Origin"};
//      parms._ignored_columns = new String[]{"Month","DayofMonth","DayOfWeek","DepTime","UniqueCarrier","Origin","Distance"};
      parms._response_column = "dep_delayed_15min";
      parms._nbins = 20;
      parms._nbins_cats = 1024;
      parms._binomial_double_trees = new Random().nextBoolean(); //doesn't matter!
      parms._ntrees = 1;
      parms._max_depth = 3;
      parms._mtries = -1;
      parms._sample_rate = 0.632f;
      parms._min_rows = 10;
      parms._seed = 12;

      // Build a first model; all remaining models should be equal
      DRFModel drf = new DRF(parms).trainModel().get();
      Log.info("Training set AUC:   " + drf._output._training_metrics.auc_obj()._auc);
      Log.info("Validation set AUC: " + drf._output._validation_metrics.auc_obj()._auc);

      // all numerical
      assertEquals(drf._output._training_metrics.auc_obj()._auc, 0.6498819479528417, 1e-8);
      assertEquals(drf._output._validation_metrics.auc_obj()._auc, 0.6479974533672835, 1e-8);

      drf.delete();
    } finally{
      if (tfr != null) tfr.remove();
      if (test != null) test.remove();
    }
    Scope.exit();
  }

  static double _AUC = 1.0;
  static double _MSE = 0.041294642857142856;
  static double _R2 = 0.8313802083333334;
  static double _LogLoss = 0.14472835908293025;

  @Test
  public void testNoRowWeights() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/no_weights.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 234;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._r2_stopping = Double.MAX_VALUE; //don't stop early

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.remove();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeightsOne() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/weights_all_ones.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 234;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._r2_stopping = Double.MAX_VALUE; //don't stop early

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeightsTwo() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/weights_all_twos.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 234;
      parms._min_rows = 2; //in terms of weighted rows
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._r2_stopping = Double.MAX_VALUE; //don't stop early

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.delete();
      Scope.exit();
    }
  }

  @Ignore
  @Test
  public void testRowWeightsTiny() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/weights_all_tiny.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 234;
      parms._min_rows = 0.01242; // in terms of weighted rows
      parms._max_depth = 2;
      parms._ntrees = 3;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_R2, mm.r2(), 1e-6);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.delete();
      Scope.exit();
    }
  }

  @Test
  public void testNoRowWeightsShuffled() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/no_weights_shuffled.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 234;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;
      parms._r2_stopping = Double.MAX_VALUE; //don't stop early

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      // Shuffling changes the row sampling -> results differ
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(1.0, mm.auc_obj()._auc, 1e-8);
      assertEquals(0.0290178571428571443, mm.mse(), 1e-8);
      assertEquals(0.8815104166666666, mm.r2(), 1e-6);
      assertEquals(0.10824081452821664, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeights() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/weights.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 234;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      // Reduced number of rows changes the row sampling -> results differ
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(1.0, mm.auc_obj()._auc, 1e-8);
      assertEquals(0.05823863636363636, mm.mse(), 1e-8);
      assertEquals(0.7651041666666667, mm.r2(), 1e-6);
      assertEquals(0.21035264541934587, mm.logloss(), 1e-6);


      // test set scoring (on the same dataset, but without normalizing the weights)
      Frame pred = drf.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(drf, parms.train());

      // Non-OOB
      assertEquals(1, mm2.auc_obj()._auc, 1e-8);
      assertEquals(0.0154320987654321, mm2.mse(), 1e-8);
      assertEquals(0.93827160493827166, mm2.r2(), 1e-8);
      assertEquals(0.08349430638608361, mm2.logloss(), 1e-8);

      pred.remove();
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.delete();
      Scope.exit();
    }
  }

  @Ignore
  @Test
  public void testNFold() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "IsDepDelayed";
      parms._seed = 234;
      parms._min_rows = 2;
      parms._nfolds = 3;
      parms._max_depth = 5;
      parms._ntrees = 5;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._cross_validation_metrics;
      assertEquals(0.7276154565296726, mm.auc_obj()._auc, 1e-8); // 1 node
      assertEquals(0.21211607823987555, mm.mse(), 1e-8);
      assertEquals(0.14939930970822446, mm.r2(), 1e-6);
      assertEquals(0.6121968624307211, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) {
        drf.deleteCrossValidationModels();
        drf.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNFoldBalanceClasses() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "IsDepDelayed";
      parms._seed = 234;
      parms._min_rows = 2;
      parms._nfolds = 3;
      parms._max_depth = 5;
      parms._balance_classes = true;
      parms._ntrees = 5;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) {
        drf.deleteCrossValidationModels();
        drf.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsOneVsRest() {
    Frame tfr = null;
    DRFModel drf1 = null;
    DRFModel drf2 = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/weights.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 9999;
      parms._min_rows = 2;
      parms._nfolds = (int) tfr.numRows();
      parms._fold_assignment = Model.Parameters.FoldAssignmentScheme.Modulo;
      parms._max_depth = 5;
      parms._ntrees = 5;

      drf1 = new DRF(parms).trainModel().get();

//            parms._nfolds = (int) tfr.numRows() + 1; //this is now an error
      drf2 = new DRF(parms).trainModel().get();

      ModelMetricsBinomial mm1 = (ModelMetricsBinomial)drf1._output._cross_validation_metrics;
      ModelMetricsBinomial mm2 = (ModelMetricsBinomial)drf2._output._cross_validation_metrics;
      assertEquals(mm1.auc_obj()._auc, mm2.auc_obj()._auc, 1e-12);
      assertEquals(mm1.mse(), mm2.mse(), 1e-12);
      assertEquals(mm1.r2(), mm2.r2(), 1e-12);
      assertEquals(mm1.logloss(), mm2.logloss(), 1e-12);

      //TODO: add check: the correct number of individual models were built. PUBDEV-1690

    } finally {
      if (tfr != null) tfr.remove();
      if (drf1 != null) {
        drf1.deleteCrossValidationModels();
        drf1.delete();
      }
      if (drf2 != null) {
        drf2.deleteCrossValidationModels();
        drf2.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsInvalidValues() {
    Frame tfr = null;
    DRFModel drf1 = null;
    DRFModel drf2 = null;
    DRFModel drf3 = null;

    Scope.enter();
    try {
      tfr = parse_test_file("./smalldata/airlines/allyears2k_headers.zip");
      for (String s : new String[]{
              "DepTime", "ArrTime", "ActualElapsedTime",
              "AirTime", "ArrDelay", "DepDelay", "Cancelled",
              "CancellationCode", "CarrierDelay", "WeatherDelay",
              "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "IsDepDelayed";
      parms._seed = 234;
      parms._min_rows = 2;
      parms._max_depth = 5;
      parms._ntrees = 5;

      parms._nfolds = 0;
      drf1 = new DRF(parms).trainModel().get();

      parms._nfolds = 1;
      try {
        Log.info("Trying nfolds==1.");
        drf2 = new DRF(parms).trainModel().get();
        Assert.fail("Should toss H2OModelBuilderIllegalArgumentException instead of reaching here");
      } catch(H2OModelBuilderIllegalArgumentException e) {}

      parms._nfolds = -99;
      try {
        Log.info("Trying nfolds==-99.");
        drf3 = new DRF(parms).trainModel().get();
        Assert.fail("Should toss H2OModelBuilderIllegalArgumentException instead of reaching here");
      } catch(H2OModelBuilderIllegalArgumentException e) {}

    } finally {
      if (tfr != null) tfr.remove();
      if (drf1 != null) drf1.delete();
      if (drf2 != null) drf2.delete();
      if (drf3 != null) drf3.delete();
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsCVAndValidation() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/weights.csv");
      vfr = parse_test_file("smalldata/junit/weights.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = "response";
      parms._min_rows = 2;
      parms._max_depth = 2;
      parms._nfolds = 2;
      parms._ntrees = 3;
      parms._seed = 11233;

      try {
        Log.info("Trying N-fold cross-validation AND Validation dataset provided.");
        drf = new DRF(parms).trainModel().get();
      } catch(H2OModelBuilderIllegalArgumentException e) {
        Assert.fail("Should not toss H2OModelBuilderIllegalArgumentException.");
      }

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) {
        drf.deleteCrossValidationModels();
        drf.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testNfoldsConsecutiveModelsSame() {
    Frame tfr = null;
    Vec old = null;
    DRFModel drf1 = null;
    DRFModel drf2 = null;

    Scope.enter();
    try {
      tfr = parse_test_file("smalldata/junit/cars_20mpg.csv");
      tfr.remove("name").remove(); // Remove unique id
      tfr.remove("economy").remove();
      old = tfr.remove("economy_20mpg");
      tfr.add("economy_20mpg", VecUtils.toCategoricalVec(old)); // response to last column
      DKV.put(tfr);

      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "economy_20mpg";
      parms._min_rows = 2;
      parms._max_depth = 2;
      parms._nfolds = 3;
      parms._ntrees = 3;
      parms._seed = 77777;

      drf1 = new DRF(parms).trainModel().get();

      drf2 = new DRF(parms).trainModel().get();

      ModelMetricsBinomial mm1 = (ModelMetricsBinomial)drf1._output._cross_validation_metrics;
      ModelMetricsBinomial mm2 = (ModelMetricsBinomial)drf2._output._cross_validation_metrics;
      assertEquals(mm1.auc_obj()._auc, mm2.auc_obj()._auc, 1e-12);
      assertEquals(mm1.mse(), mm2.mse(), 1e-12);
      assertEquals(mm1.r2(), mm2.r2(), 1e-12);
      assertEquals(mm1.logloss(), mm2.logloss(), 1e-12);

    } finally {
      if (tfr != null) tfr.remove();
      if (old != null) old.remove();
      if (drf1 != null) {
        drf1.deleteCrossValidationModels();
        drf1.delete();
      }
      if (drf2 != null) {
        drf2.deleteCrossValidationModels();
        drf2.delete();
      }
      Scope.exit();
    }
  }

  @Test
  public void testMTrys() {
    Frame tfr = null;
    Vec old = null;
    DRFModel drf1 = null;

    for (int i=1; i<=6; ++i) {
      Scope.enter();
      try {
        tfr = parse_test_file("smalldata/junit/cars_20mpg.csv");
        tfr.remove("name").remove(); // Remove unique id
        tfr.remove("economy").remove();
        old = tfr.remove("economy_20mpg");
        tfr.add("economy_20mpg", VecUtils.toCategoricalVec(old)); // response to last column
        DKV.put(tfr);

        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = tfr._key;
        parms._response_column = "economy_20mpg";
        parms._min_rows = 2;
        parms._ntrees = 5;
        parms._max_depth = 5;
        parms._nfolds = 3;
        parms._mtries = i;

        drf1 = new DRF(parms).trainModel().get();

        ModelMetricsBinomial mm1 = (ModelMetricsBinomial) drf1._output._cross_validation_metrics;
        Assert.assertTrue(mm1._auc != null);

      } finally {
        if (tfr != null) tfr.remove();
        if (old != null) old.remove();
        if (drf1 != null) {
          drf1.deleteCrossValidationModels();
          drf1.delete();
        }
        Scope.exit();
      }
    }
  }

  @Test
  public void testStochasticDRFEquivalent() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parse_test_file("./smalldata/junit/cars.csv");
      for (String s : new String[]{
              "name",
      }) {
        tfr.remove(s).remove();
      }
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "cylinders"; //regression
      parms._seed = 234;
      parms._min_rows = 2;
      parms._max_depth = 5;
      parms._r2_stopping = 2;
      parms._ntrees = 5;
      parms._mtries = 3;
      parms._sample_rate = 0.5f;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      ModelMetricsRegression mm = (ModelMetricsRegression)drf._output._training_metrics;
      assertEquals(0.1238181934227711, mm.mse(), 1e-4);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.delete();
      Scope.exit();
    }
  }

  @Test
  public void testColSamplingPerTree() {
    Frame tfr = null;
    Key[] ksplits = new Key[0];
    try{
      tfr=parse_test_file("./smalldata/gbm_test/ecology_model.csv");
      SplitFrame sf = new SplitFrame(tfr,new double[] { 0.5, 0.5 }, new Key[] { Key.make("train.hex"), Key.make("test.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;

      DRFModel drf = null;
      float[] sample_rates = new float[]{0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
      float[] col_sample_rates = new float[]{0.4f, 0.6f, 0.8f, 1.0f};
      float[] col_sample_rates_per_tree = new float[]{0.4f, 0.6f, 0.8f, 1.0f};

      Map<Double, Triple<Float>> hm = new TreeMap<>();
      for (float sample_rate : sample_rates) {
        for (float col_sample_rate : col_sample_rates) {
          for (float col_sample_rate_per_tree : col_sample_rates_per_tree) {
            Scope.enter();
            try {
              DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
              parms._train = ksplits[0];
              parms._valid = ksplits[1];
              parms._response_column = "Angaus"; //regression
              parms._seed = 12345;
              parms._min_rows = 1;
              parms._max_depth = 15;
              parms._ntrees = 2;
              parms._mtries = Math.max(1,(int)(col_sample_rate*(tfr.numCols()-1)));
              parms._col_sample_rate_per_tree = col_sample_rate_per_tree;
              parms._sample_rate = sample_rate;

              // Build a first model; all remaining models should be equal
              DRF job = new DRF(parms);
              drf = job.trainModel().get();

              // too slow, but passes (now)
//            // Build a POJO, validate same results
//            Frame pred = drf.score(tfr);
//            Assert.assertTrue(drf.testJavaScoring(tfr,pred,1e-15));
//            pred.remove();

              ModelMetricsRegression mm = (ModelMetricsRegression)drf._output._validation_metrics;
              hm.put(mm.mse(), new Triple<>(sample_rate, col_sample_rate, col_sample_rate_per_tree));

            } finally {
              if (drf != null) drf.delete();
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
  @Test public void minSplitImprovement() {
    Frame tfr = null;
    Key[] ksplits = null;
    DRFModel drf = null;
    try {
      Scope.enter();
      tfr = parse_test_file("smalldata/covtype/covtype.20k.data");
      int resp = 54;
//      tfr = parse_test_file("bigdata/laptop/mnist/train.csv.gz");
//      int resp = 784;
      Scope.track(tfr.replace(resp, tfr.vecs()[resp].toCategoricalVec()));
      DKV.put(tfr);
      SplitFrame sf = new SplitFrame(tfr, new double[]{0.5, 0.5}, new Key[]{Key.make("train.hex"), Key.make("valid.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;
      double[] msi = new double[]{0, 1e-10, 1e-8, 1e-6, 1e-4, 1e-2};
      final int N = msi.length;
      double[] loglosses = new double[N];
      for (int i = 0; i < N; ++i) {
        // Load data, hack frames
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = ksplits[0];
        parms._valid = ksplits[1];
        parms._response_column = tfr.names()[resp];
        parms._min_split_improvement = msi[i];
        parms._ntrees = 20;
        parms._score_tree_interval = parms._ntrees;
        parms._max_depth = 15;
        parms._seed = 1234;

        DRF job = new DRF(parms);
        drf = job.trainModel().get();
        loglosses[i] = drf._output._scored_valid[drf._output._scored_valid.length - 1]._logloss;
        if (drf!=null) drf.delete();
      }
      for (int i = 0; i < msi.length; ++i) {
        Log.info("min_split_improvement: " + msi[i] + " -> validation logloss: " + loglosses[i]);
      }
      int idx = ArrayUtils.minIndex(loglosses);
      Log.info("Optimal min_split_improvement: " + msi[idx]);
      Assert.assertTrue(0 != idx);
    } finally {
      if (drf!=null) drf.delete();
      if (tfr!=null) tfr.delete();
      if (ksplits[0]!=null) ksplits[0].remove();
      if (ksplits[1]!=null) ksplits[1].remove();
      Scope.exit();
    }
  }
  @Test public void randomizeSplitPoints() {
    Frame tfr = null;
    Key[] ksplits = null;
    DRFModel drf = null;
    try {
      Scope.enter();
      tfr = parse_test_file("smalldata/covtype/covtype.20k.data");
      int resp = 54;
//      tfr = parse_test_file("bigdata/laptop/mnist/train.csv.gz");
//      int resp = 784;
      Scope.track(tfr.replace(resp, tfr.vecs()[resp].toCategoricalVec()));
      DKV.put(tfr);
      SplitFrame sf = new SplitFrame(tfr, new double[]{0.5, 0.5}, new Key[]{Key.make("train.hex"), Key.make("valid.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;
      boolean[] randomize = new boolean[]{false, true};
      final int N = randomize.length;
      double[] loglosses = new double[N];
      for (int i = 0; i < N; ++i) {
        // Load data, hack frames
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = ksplits[0];
        parms._valid = ksplits[1];
        parms._response_column = tfr.names()[resp];
        parms._random_split_points = randomize[i];
        parms._ntrees = 10;
        parms._score_tree_interval = parms._ntrees;
        parms._max_depth = 10;
        parms._seed = 12345;
        parms._nbins = 10;
        parms._nbins_top_level = 10;

        DRF job = new DRF(parms);
        drf = job.trainModel().get();
        loglosses[i] = drf._output._scored_valid[drf._output._scored_valid.length - 1]._logloss;
        if (drf!=null) drf.delete();
      }
      for (int i = 0; i < randomize.length; ++i) {
        Log.info("randomize: " + randomize[i] + " -> validation logloss: " + loglosses[i]);
      }
      int idx = ArrayUtils.minIndex(loglosses);
      Log.info("Optimal randomization: " + randomize[idx]);
//      Assert.assertTrue(0 == idx); //this is a memorization problem, doesn't suffer from overfitting
    } finally {
      if (drf!=null) drf.delete();
      if (tfr!=null) tfr.delete();
      if (ksplits[0]!=null) ksplits[0].remove();
      if (ksplits[1]!=null) ksplits[1].remove();
      Scope.exit();
    }
  }

  @Test public void sampleRatePerClass() {
    Frame tfr = null;
    Key[] ksplits = null;
    DRFModel drf = null;
    try {
      Scope.enter();
      tfr = parse_test_file("smalldata/covtype/covtype.20k.data");
      int resp = 54;
//      tfr = parse_test_file("bigdata/laptop/mnist/train.csv.gz");
//      int resp = 784;
      Scope.track(tfr.replace(resp, tfr.vecs()[resp].toCategoricalVec()));
      DKV.put(tfr);
      SplitFrame sf = new SplitFrame(tfr, new double[]{0.5, 0.5}, new Key[]{Key.make("train.hex"), Key.make("valid.hex")});
      // Invoke the job
      sf.exec().get();
      ksplits = sf._destination_frames;
      // Load data, hack frames
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = ksplits[0];
      parms._valid = ksplits[1];
      parms._response_column = tfr.names()[resp];
      parms._min_split_improvement = 1e-5;
      parms._ntrees = 20;
      parms._score_tree_interval = parms._ntrees;
      parms._max_depth = 15;
      parms._seed = 1234;
      parms._sample_rate_per_class = new double[]{0.1f,0.1f,0.2f,0.4f,1f,0.3f,0.2f};

      DRF job = new DRF(parms);
      drf = job.trainModel().get();
      if (drf!=null) drf.delete();
    } finally {
      if (drf!=null) drf.delete();
      if (tfr!=null) tfr.delete();
      if (ksplits[0]!=null) ksplits[0].remove();
      if (ksplits[1]!=null) ksplits[1].remove();
      Scope.exit();
    }
  }
}
