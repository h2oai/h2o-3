package hex.tree.drf;


import hex.Model;
import hex.ModelMetricsBinomial;
import hex.ModelMetricsRegression;
import hex.SplitFrame;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.algos.tree.SharedTreeNode;
import hex.genmodel.algos.tree.SharedTreeSubgraph;
import hex.genmodel.tools.PredictCsv;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.DHistogram;
import hex.tree.SharedTreeModel;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.RebalanceDataSet;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.ParseSetup;
import water.util.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class DRFTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  @Rule
  public transient TemporaryFolder temporaryFolder = new TemporaryFolder();

  abstract static class PrepData { abstract int prep(Frame fr); }

  static String[] s(String...arr)  { return arr; }

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
            ard(ard(0, 2, 0, 0, 0),
                    ard(0, 58, 6, 4, 0),
                    ard(0, 1, 0, 0, 0),
                    ard(1, 3, 4, 25, 1),
                    ard(0, 0, 0, 2, 37)),
            s("3", "4", "5", "6", "8"));
  }

    @Test public void testClassCars1UnlimitedDepth() throws Throwable {
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
                0,
                ard(ard(0, 2, 0, 0, 0),
                        ard(0, 58, 6, 4, 0),
                        ard(0, 1, 0, 0, 0),
                        ard(1, 3, 4, 25, 1),
                        ard(0, 0, 0, 2, 37)),
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
                    ard(0, 177, 1,  5, 0),
                    ard(0, 2, 0, 0, 0),
                    ard(0, 6, 1, 67, 1),
                    ard(0, 0, 0, 2, 84)),
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
            63.13182273942728
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
            59.713095855920244
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
            46.90342217100751
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

  @Test public void test30kUnseenLevels() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/gbm_test/30k_cattest.csv", "cat30k",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("C3");
              }
            },
            50, //ntrees
            20, //bins
            10, //min_rows
            5, //max_depth
            0.25040513069517);
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
  @Test public void testAlphabetRegression2() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/gbm_test/alphabet_cattest.csv", "alphabetRegression2.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("y");
              }
            },
            1,
            26, // enough bins to resolve the alphabet
            1,
            1, // depth 1 is enough since nbins_cats == nbins == 26 (enough)
            0.0);
  }
  @Test public void testAlphabetRegression3() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/gbm_test/alphabet_cattest.csv", "alphabetRegression3.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("y");
              }
            },
            1,
            25, // not enough bins to resolve the alphabet
            1,
            1, // depth 1 is not enough since nbins_cats == nbins < 26
            0.24007225096411577);
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

  public void basicDRF(String fnametrain, String hexnametrain, String fnametest, PrepData prep, int ntree, int max_depth, int nbins, boolean classification, int min_rows, double[][] expCM, double expMSE, String[] expRespDom) {
    Scope.enter();
    DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
    Frame frTest = null, pred = null;
    Frame frTrain = null;
    Frame test = null, res = null;
    DRFModel model = null;
    try {
      frTrain = parseTestFile(fnametrain);
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

//      StreamingSchema ss = new StreamingSchema(model.getMojo(), "model.zip");
//      FileOutputStream fos = new FileOutputStream("model.zip");
//      ss.getStreamWriter().writeTo(fos);

      Log.info(model._output);
      Assert.assertTrue(job.isStopped()); //HEX-1817

      hex.ModelMetrics mm;
      if (fnametest != null) {
        frTest = parseTestFile(fnametest);
        pred = model.score(frTest);
        mm = hex.ModelMetrics.getFromDKV(model, frTest);
        // Check test set CM
      } else {
        mm = hex.ModelMetrics.getFromDKV(model, frTrain);
      }
      Assert.assertEquals("Number of trees differs!", ntree, model._output._ntrees);

      test = parseTestFile(fnametrain);
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
        Assert.assertTrue("Expected: " + expMSE + ", Got: " + mm.mse(), Math.abs(expMSE-mm.mse()) <= 1e-10*Math.abs(expMSE+mm.mse()));
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
          tfr = parseTestFile("/Users/ludirehak/Downloads/train.csv.zip");

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
    int c = 0;
    for (int max_depth : max_depths) {
      for (int ntree: ntrees) {
        for (boolean rebalanceMe: rebalanceMes) {
          for (int chunk : chunks) {
            long startTime = System.currentTimeMillis();
            Scope.enter();
            // Load data, hack frames
            Frame tfr = parseTestFile("/Users/ludirehak/Downloads/train.csv.zip");

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
            Log.info(" DEPTH: " + outputdepths[c] + " NTREES: "+ outputntrees[c] + " CHUNKS: " + outputchunks[c] + " EXECUTION TIME: " + executionTimes[c] + " Rebalanced: " + rebalanceMe + " WarmedUp: " + warmUp);
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
      bufferedWriter.write("max_depth,ntrees,nbins,min_rows,chunks,execution_time,rebalanceMe,warmUp");
      bufferedWriter.newLine();
      for (int i = 0; i < executionTimes.length; i++) {
        bufferedWriter.write(outputdepths[i] +"," + outputntrees[i] + "," + 1000 + ","+ 10 + "," + outputchunks[i] + "," + executionTimes[i] +"," +","+(outputrebalanceme[i]? 1:0)+","+(warmUp?1:0));
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
      tfr = parseTestFile("smalldata/covtype/covtype.20k.data");

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
      parms._auto_rebalance = false;

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
      assertEquals(0.20377446328850304, mses[i], 1e-4); //check for the same result on 1 nodes and 5 nodes
    }
  }

  // gh issue: https://github.com/h2oai/private-h2o-3/issues/381
  @Ignore
  @Test public void testAirline() {
    Frame tfr=null;
    Frame test=null;

    Scope.enter();
    try {
      // Load data, hack frames
      tfr = parseTestFile(Key.make("air.hex"), "/users/arno/sz_bench_data/train-1m.csv");
      test = parseTestFile(Key.make("airt.hex"), "/users/arno/sz_bench_data/test.csv");
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
  static double _LogLoss = 0.14472835908293025;

  @Test
  public void testNoRowWeights() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/no_weights.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 234;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
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
      tfr = parseTestFile("smalldata/junit/weights_all_ones.csv");
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
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
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
      tfr = parseTestFile("smalldata/junit/weights_all_twos.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._seed = 234;
      parms._min_rows = 2; //in terms of weighted rows
      parms._max_depth = 2;
      parms._ntrees = 3;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(_AUC, mm.auc_obj()._auc, 1e-8);
      assertEquals(_MSE, mm.mse(), 1e-8);
      assertEquals(_LogLoss, mm.logloss(), 1e-6);

    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (drf != null) drf.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRowWeightsTiny() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights_all_tiny.csv");
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
      tfr = parseTestFile("smalldata/junit/no_weights_shuffled.csv");
      DKV.put(tfr);
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._seed = 234;
      parms._min_rows = 1;
      parms._max_depth = 2;
      parms._ntrees = 3;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      // OOB
      // Shuffling changes the row sampling -> results differ
      ModelMetricsBinomial mm = (ModelMetricsBinomial)drf._output._training_metrics;
      assertEquals(1.0, mm.auc_obj()._auc, 1e-8);
      assertEquals(0.0290178571428571443, mm.mse(), 1e-8);
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
      tfr = parseTestFile("smalldata/junit/weights.csv");
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
      assertEquals(0.21035264541934587, mm.logloss(), 1e-6);


      // test set scoring (on the same dataset, but without normalizing the weights)
      Frame pred = drf.score(parms.train());
      hex.ModelMetricsBinomial mm2 = hex.ModelMetricsBinomial.getFromDKV(drf, parms.train());

      // Non-OOB
      assertEquals(1, mm2.auc_obj()._auc, 1e-8);
      assertEquals(0.0154320987654321, mm2.mse(), 1e-8);
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
      tfr = parseTestFile("smalldata/junit/weights.csv");
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
      tfr = parseTestFile("smalldata/junit/weights.csv");
      vfr = parseTestFile("smalldata/junit/weights.csv");
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
      tfr = parseTestFile("smalldata/junit/cars_20mpg.csv");
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
        tfr = parseTestFile("smalldata/junit/cars_20mpg.csv");
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
  public void testMTryNegTwo() {
    Frame tfr = null;
    Vec old = null;
    DRFModel drf1 = null;
    Scope.enter();
    try {
        tfr = parseTestFile("smalldata/junit/cars_20mpg.csv");
        tfr.remove("name").remove(); // Remove unique id
        tfr.remove("economy").remove();
        old = tfr.remove("economy_20mpg");
        tfr.add("economy_20mpg", VecUtils.toCategoricalVec(old)); // response to last column
        tfr.add("constantCol",tfr.anyVec().makeCon(1)); //DRF should not honor constant cols but still use all cols for split when mtries=-2
        DKV.put(tfr);

        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = tfr._key;
        parms._response_column = "economy_20mpg";
        parms._ignored_columns = new String[]{"year"}; //Test to see if ignored column is not passed to DRF
        parms._min_rows = 2;
        parms._ntrees = 5;
        parms._max_depth = 5;
        parms._nfolds = 3;
        parms._mtries = -2;

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

  @Test
  public void testStochasticDRFEquivalent() {
    Frame tfr = null, vfr = null;
    DRFModel drf = null;

    Scope.enter();
    try {
      tfr = parseTestFile("./smalldata/junit/cars.csv");
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
      parms._ntrees = 5;
      parms._mtries = 3;
      parms._sample_rate = 0.5f;

      // Build a first model; all remaining models should be equal
      drf = new DRF(parms).trainModel().get();

      ModelMetricsRegression mm = (ModelMetricsRegression)drf._output._training_metrics;
      assertEquals(0.12358322821934015, mm.mse(), 1e-4);

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
      tfr= parseTestFile("./smalldata/gbm_test/ecology_model.csv");
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
  @Test public void histoTypes() {
    Frame tfr = null;
    Key[] ksplits = null;
    DRFModel drf = null;
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
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = ksplits[0];
        parms._valid = ksplits[1];
        parms._response_column = tfr.names()[resp];
        parms._histogram_type = histoType[i];
        parms._ntrees = 10;
        parms._score_tree_interval = parms._ntrees;
        parms._max_depth = 10;
        parms._seed = 123456;
        parms._nbins = 20;
        parms._nbins_top_level = 20;

        DRF job = new DRF(parms);
        drf = job.trainModel().get();
        loglosses[i] = drf._output._scored_valid[drf._output._scored_valid.length - 1]._logloss;
        if (drf!=null) drf.delete();
      }
      for (int i = 0; i < histoType.length; ++i) {
        Log.info("histoType: " + histoType[i] + " -> validation logloss: " + loglosses[i]);
      }
      int idx = ArrayUtils.minIndex(loglosses);
      Log.info("Optimal randomization: " + histoType[idx]);
      Assert.assertEquals(SharedTreeModel.SharedTreeParameters.HistogramType.QuantilesGlobal, histoType[idx]); //Quantiles are best
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
  
  @Test public void testConstantResponse() { 
    Scope.enter();
    Frame tfr=null;
    DRFModel drf = null;
    try {
        tfr = parseTestFile(Key.make("iris.hex"), "./smalldata/iris/iris.csv");
        tfr.add("constantCol",tfr.anyVec().makeCon(1));
        DKV.put(tfr);
        DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
        parms._train = tfr._key;
        parms._response_column = "constantCol";
        parms._ntrees = 1;
        parms._max_depth = 3;
        parms._seed = 12;
        parms._check_constant_response = false; //Allow constant response column
        // Build model
        drf = new DRF(parms).trainModel().get();
    } finally{
        if (tfr != null) tfr.remove();
        if (drf != null) drf.remove();
    }
    Scope.exit(); 
  }

  @Test public void testScoreFeatureFrequencies() {
    Scope.enter();
    try {
      Frame train = parseTestFile("./smalldata/logreg/prostate.csv");
      train.toCategoricalCol("CAPSULE");
      Scope.track(train);

      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._train = train._key;
      parms._response_column = "CAPSULE";
      parms._ntrees = 5;
      parms._seed = 0xC0B0L;
      parms._ignored_columns = new String[]{"ID"};

      SharedTreeModel<?, ?, ?> model = new DRF(parms).trainModel().get();
      Scope.<SharedTreeModel>track_generic(model);

      Frame ff = model.scoreFeatureFrequencies(train, Key.<Frame>make("ff_prostate"));
      Scope.track(ff);

      assertArrayEquals(model._output.features(), ff.names());
      assertEquals(train.numRows(), ff.numRows());

      // Check on a single row
      Frame testRow = Scope.track(train.deepSlice(new long[]{0}, null));

      Frame ffTestRow = model.scoreFeatureFrequencies(testRow, Key.<Frame>make("ff_prostate"));
      Scope.track(ffTestRow);
      
      long ffTotal = 0;
      for (int i = 0; i < ffTestRow.numCols(); i++) {
        ffTotal += ffTestRow.vec(i).at8(0);
      }
      
      Frame lnAssignment = model.scoreLeafNodeAssignment(
              testRow, Model.LeafNodeAssignment.LeafNodeAssignmentType.Path, Key.<Frame>make("lna_prostate"));
      Scope.track(lnAssignment);
      long totalPathLength = 0;
      for (String path : ArrayUtils.flat(lnAssignment.domains())) {
        totalPathLength += path.length();
      }

      assertEquals(totalPathLength, ffTotal);
    } finally {
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
    DRFModel model = null;
    try {
      DKV.put(dummyFrame);
      DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
      drf._train = dummyFrame._key;
      drf._response_column = "target";
      drf._ntrees = 5;
      drf._max_depth = 3;
      drf._min_rows = 1;
      drf._nbins = 3;
      drf._nbins_cats = 3;
      drf._seed = 1;
      drf._ignore_const_cols = ignoreConstCols;

      DRF job = new DRF(drf);
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
  public void checkDeepLeafNodeAssignmentConsistency() {
    checkDeepLeafNodeAssignmentConsistency(31, 7);  // old supported maximum
    checkDeepLeafNodeAssignmentConsistency(48, 10); // random point between 32 and 63 
    checkDeepLeafNodeAssignmentConsistency(63, 12); // current supported maximum
    checkDeepLeafNodeAssignmentConsistency(64, 17); // breaking point
    checkDeepLeafNodeAssignmentConsistency(73, 42); // way past breaking point 
  }

  /**
   * Grow and check properties of a very deep tree
   * 
   * @param depth desired tree depth 
   * @param extraObservations how many observations should be classified in the deepest leaf node
   */
  private void checkDeepLeafNodeAssignmentConsistency(int depth, int extraObservations) {
    try {
      Scope.enter();
      String[] x = new String[depth + extraObservations];
      double[] w = new double[x.length];
      double[] y = new double[x.length];
      for (int i = 0; i < x.length; i++) {
        x[i] = String.valueOf(i + 100);
        w[i] = i > 0 ? (w[i-1] * 2) + 1 : 0;
        y[i] = i;
      }
      Frame tfr = new TestFrameBuilder()
              .withColNames("w", "x", "y")
              .withDataForCol(0, w)
              .withDataForCol(1, x)
              .withDataForCol(2, y)
              .withVecTypes(Vec.T_NUM, Vec.T_CAT, Vec.T_NUM)
              .withChunkLayout(x.length)
              .build();
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._nbins_top_level = x.length;
      parms._nbins = x.length;
      parms._train = tfr._key;
      parms._response_column = "y";
      parms._weights_column = "w";
      parms._ntrees = 1;
      parms._max_depth = depth;
      parms._sample_rate = 1;
      parms._mtries = -2;
      parms._seed = 1234;
      parms._min_split_improvement = 0;
      parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.OneHotExplicit;

      DRF job = new DRF(parms);
      DRFModel drf = job.trainModel().get();
      assertNotNull(drf);
      Scope.track_generic(drf);

      Frame paths = drf.scoreLeafNodeAssignment(tfr, Model.LeafNodeAssignment.LeafNodeAssignmentType.Path, Key.make());
      Scope.track(paths);
      Frame nodeIds = drf.scoreLeafNodeAssignment(tfr, Model.LeafNodeAssignment.LeafNodeAssignmentType.Node_ID, Key.make());
      Scope.track(nodeIds);

      SharedTreeSubgraph tree = drf.getSharedTreeSubgraph(0, 0);
      // check assumptions (are we really testing deep trees?)
      int actualDepth = -1;
      for (SharedTreeNode n : tree.nodesArray)
        if (n.getDepth() >= actualDepth) {
          actualDepth = n.getDepth();
        }
      assertEquals(depth, actualDepth);

      Vec.Reader pathReader = paths.vec(0).new Reader();
      Vec.Reader nodeIdReader = nodeIds.vec(0).new Reader();

      for (long i = 0; i < tfr.numRows(); i++) {
        if ((depth > 63) && (depth - 63 + extraObservations > i)) {
          assertTrue(pathReader.isNA(i));
          assertEquals(-1, nodeIdReader.at8(i));
        } else {
          assertFalse(pathReader.isNA(i));
          assertNotEquals(-1, nodeIdReader.at8(i));
          String path = paths.vec(0).domain()[(int) pathReader.at8(i)];
          int nodeId = (int) nodeIdReader.at8(i);
          SharedTreeNode node = tree.walkNodes(path);
          assertNotNull(node);
          assertTrue(node.isLeaf());
          assertEquals(nodeId, node.getNodeNumber());
        }
      }
    } finally {
      Scope.exit();
    }
  }

  @Test // observations at scoring time traverse the tree differently than at training time 
  public void showRoundingErrorsCanMisclassifyObservations() {
    checkRoundingErrorSplits(1);
  }

  @Test // tree has a larger total weight than it theoretically could be
  public void showRoundingErrorsCanMakeTreeBuildingRegisterObservationsTwice() {
    checkRoundingErrorSplits(2);
  }

  private void checkRoundingErrorSplits(int maxDepth) {
    Scope.enter();
    try {
      int nbins = 2;

      // column will have these 3 values
      double min = 0;
      double max = 40 + 1e-6;
      double splitAt = 20 + 1e-7;

      // validate assumptions of this setup
      double maxEx = DHistogram.find_maxEx(max, 0);
      double step = nbins / (maxEx - min);
      // 'splitAt' point will be classified into the first bin
      assertEquals(0, (int) (step * (splitAt - min)));
      // but if we decide to split between the 2 bins, it would actually go RIGHT (direction of bin 2) -> issue
      assertTrue(splitAt >= (float) ((min + max) / 2));

      Frame f = new TestFrameBuilder()
              .withColNames("C0", "response", "weight")
              .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, new double[]{min, splitAt, max})
              .withDataForCol(1, new double[]{1, 0, 0.1})
              .withDataForCol(2, new double[]{1, 1, 1})
              .build();

      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._response_column = "response";
      parms._train = f._key;
      parms._ntrees = 1;
      parms._seed = 1234L;
      parms._max_depth = maxDepth;
      parms._sample_rate = 1.0;
      parms._weights_column = "weight";
      parms._mtries = f.numCols() - 2;
      parms._nbins_top_level = nbins;
      parms._nbins = nbins;

      DRFModel model = new DRF(parms).trainModel().get();
      Scope.track_generic(model);

      TreeWeightInfo twi = calculateNodeWeights(model, f, "weight");

      SharedTreeSubgraph tree0 = model.getSharedTreeSubgraph(0, 0);
      float actualWeight = tree0.nodesArray.stream()
              .filter(SharedTreeNode::isLeaf)
              .map(SharedTreeNode::getWeight)
              .reduce(Float::sum)
              .get();
      assertEquals(twi._treeWeight, actualWeight, 0);

      for (SharedTreeNode n : tree0.nodesArray) {
          if (n.isLeaf()) {
              double expectedNodeWeight = n.getWeight();
              assertEquals("Weight in node #" + n.getNodeNumber() + " should match",
                      expectedNodeWeight, twi._nodeWeights.get(n.getNodeNumber()), 0);
          }
      }
    } finally {
      Scope.exit();
    }
  }

  private static TreeWeightInfo calculateNodeWeights(DRFModel model, Frame f, String weightColumn) {
      Map<Integer, Double> nodeWeights = new HashMap<>();
      Frame nodeIds = model.scoreLeafNodeAssignment(
              f, Model.LeafNodeAssignment.LeafNodeAssignmentType.Node_ID, Key.make());
      nodeIds.add(f);
      Scope.track(nodeIds);
      double treeWeight = 0;
      for (int i = 0; i < nodeIds.numRows(); i++) {
          int nodeId = (int) nodeIds.vec(0).at(i);
          double weight = nodeIds.vec(weightColumn).at(i);
          treeWeight += weight;
          if (!nodeWeights.containsKey(nodeId)) {
              nodeWeights.put(nodeId, 0.0);
          }
          nodeWeights.put(nodeId, nodeWeights.get(nodeId) + weight);
      }
      return new TreeWeightInfo(nodeWeights, treeWeight);
  }

  private static class TreeWeightInfo {
      Map<Integer, Double> _nodeWeights;
      double _treeWeight;

      TreeWeightInfo(Map<Integer, Double> nodeWeights, double treeWeight) {
          _nodeWeights = nodeWeights;
          _treeWeight = treeWeight;
      }
  }
  
  @Test
  public void testCategoricalSplitNAvsREST() {
    Scope.enter();
    try {
      String[] dataCol1 = ArrayUtils.toString(ArrayUtils.seq(0, 64));
      String[] dataCol2 = new String[dataCol1.length];
      double[] response = new double[dataCol1.length];
      double[] weights = new double[response.length];
      for (int i = 0; i < response.length; i++) {
          if (i <= 50) {
              response[i] = i / 100.0;
              weights[i] = 1.0;
              dataCol2[i] = null;
          } else if (i <= 57) {
              response[i] = 10;
              weights[i] = 100;
              dataCol2[i] = null;
          } else {
              response[i] = 11;
              weights[i] = 100;
              dataCol2[i] = i % 2 == 0 ? "A" : "B";
          }
      }
      
      Frame f = new TestFrameBuilder()
              .withColNames("C0", "C1", "response", "weight")
              .withVecTypes(Vec.T_CAT, Vec.T_CAT, Vec.T_NUM, Vec.T_NUM)
              .withDataForCol(0, dataCol2)
              .withDataForCol(1, dataCol1)
              .withDataForCol(2, response)
              .withDataForCol(3, weights)
              .build();
      f.vec(0).setNA(0);

      System.out.println(f.toTwoDimTable(0, (int) f.numRows(), false));
      
      DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
      parms._response_column = "response";
      parms._train = f._key;
      parms._ntrees = 1;
      parms._seed = 1234L;
      parms._max_depth = 4;
      parms._sample_rate = 1.0;
      parms._weights_column = "weight";
      parms._mtries = f.numCols() - 2;
      parms._nbins_cats = 32;

      DRFModel model = new DRF(parms).trainModel().get();
      Scope.track_generic(model);

      Map<Integer, Integer> actualWeights = new HashMap<>();
      
      Frame nodeIds = model.scoreLeafNodeAssignment(
              f, Model.LeafNodeAssignment.LeafNodeAssignmentType.Node_ID, Key.make());
      nodeIds.add(f);
      System.out.println(nodeIds.toTwoDimTable(0, (int) f.numRows(), false));
      Scope.track(nodeIds);
      for (int i = 0; i < nodeIds.numRows(); i++) {
          int nodeId = (int) nodeIds.vec(0).at(i);
          int weight = (int) nodeIds.vec("weight").at(i);
          if (!actualWeights.containsKey(nodeId)) {
              actualWeights.put(nodeId, 0);
          }
          actualWeights.put(nodeId, actualWeights.get(nodeId) + weight);
      }

      SharedTreeSubgraph tree0 = model.getSharedTreeSubgraph(0, 0);
      // check that we reproduced the edge case that triggers the bug:
      // we have a node that has a split "NAvsREST" whose parent is a bitset split
      List<SharedTreeNode> naVsRestNodes = tree0.nodesArray.stream()
              .filter(SharedTreeNode::isNaVsRest).collect(Collectors.toList());
      assertEquals(1, naVsRestNodes.size());
      assertTrue(naVsRestNodes.get(0).getParent().isBitset());
      for (SharedTreeNode n : tree0.nodesArray) {
          if (n.isLeaf()) {
              int expectedWeight = (int) n.getWeight();
              assertEquals("Weight in node #" + n.getNodeNumber() + " should match",
                      expectedWeight, (int) actualWeights.get(n.getNodeNumber()));
          }
      }
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
                    Model.Parameters.CategoricalEncodingScheme.OneHotExplicit,
                    Model.Parameters.CategoricalEncodingScheme.SortByResponse,
                    Model.Parameters.CategoricalEncodingScheme.EnumLimited,
                    Model.Parameters.CategoricalEncodingScheme.Enum,
                    Model.Parameters.CategoricalEncodingScheme.Binary,
                    Model.Parameters.CategoricalEncodingScheme.LabelEncoder,
                    Model.Parameters.CategoricalEncodingScheme.Eigen
            };

            for (Model.Parameters.CategoricalEncodingScheme scheme : supportedSchemes) {

                DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
                parms._train = fr._key;
                parms._response_column = response;
                parms._ntrees = 5;
                parms._categorical_encoding = scheme;
                if (scheme == Model.Parameters.CategoricalEncodingScheme.EnumLimited) {
                    parms._max_categorical_levels = 3;
                }

                DRF job = new DRF(parms);
                DRFModel gbm = job.trainModel().get();
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
    
    @Test 
    public void reproducePUBDEV8298() throws Exception {
      try {
          Scope.enter();
          int[] responseData = new int[] {1, 2, 3};
          int[] c1Data = new int[] {1, 2, 3};
          int[] c2Data = new int[] {2, 3, 4};
          int[] c3Data = new int[] {3, 4, 5};

          Frame train = new TestFrameBuilder()
                  .withColNames("C1", "C2", "C3", "P")
                  .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                  .withDataForCol(0, c1Data)
                  .withDataForCol(1, c2Data)
                  .withDataForCol(2, c3Data)
                  .withDataForCol(3, responseData)
                  .build();

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
              DRFModel.DRFParameters parms = new DRFModel.DRFParameters();
              parms._train = train._key;
              parms._response_column = "P";
              parms._ntrees = 5;
              parms._categorical_encoding = scheme;
              parms._max_categorical_levels = 50;
              parms._seed = 12345;
              parms._distribution = DistributionFamily.gaussian;
              parms._mtries = -2;
              parms._ntrees = 1;
              parms._sample_rate = 1.0;
              parms._check_constant_response = false;
              parms._max_depth = 3;
              parms._nfolds = 0;

              DRF job = new DRF(parms);
              DRFModel drfModel= job.trainModel().get();
              assertNotNull(drfModel);
              Scope.track_generic(drfModel);                     

              // conversion to mojo shouldn't produce exception
              MojoModel mojoModel = drfModel.toMojo();
              assertNotNull(mojoModel);
          }
      } finally {
          Scope.exit();
      }
    }
}
