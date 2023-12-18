package hex.deeplearning;


import hex.*;
import hex.genmodel.GenModel;
import hex.genmodel.MojoModel;
import hex.genmodel.tools.PredictCsv;
import hex.genmodel.utils.DistributionFamily;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.*;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static hex.genmodel.utils.DistributionFamily.*;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;
import static water.TestUtil.*;


@RunWith(H2ORunner.class)
@CloudSize(1)
public class DeepLearningTest {

  @Rule
  public transient TemporaryFolder tmp = new TemporaryFolder();

  abstract static class PrepData { abstract int prep(Frame fr); }

  static String[] s(String...arr)  { return arr; }
  static long[]   a(long ...arr)   { return arr; }
  static long[][] a(long[] ...arr) { return arr; }

  @Test public void testClassIris1() throws Throwable {

    // iris ntree=1
    basicDLTest_Classification(
        "./smalldata/iris/iris.csv", "iris.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            return fr.numCols() - 1;
          }
        },
        1,
        ard(ard(27, 16, 7),
            ard(0, 4, 46),
            ard(0, 3, 47)),
        s("Iris-setosa", "Iris-versicolor", "Iris-virginica"),
        DeepLearningParameters.Activation.Rectifier);

  }

  @Test public void testClassIris5() throws Throwable {
    // iris ntree=50
    basicDLTest_Classification(
        "./smalldata/iris/iris.csv", "iris5.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            return fr.numCols() - 1;
          }
        },
        5,
        ard(ard(50, 0, 0),
            ard(0, 39, 11),
            ard(0, 8, 42)),
        s("Iris-setosa", "Iris-versicolor", "Iris-virginica"),
        DeepLearningParameters.Activation.Rectifier);
  }

  @Test public void testClassCars1() throws Throwable {
    // cars ntree=1
    basicDLTest_Classification(
        "./smalldata/junit/cars.csv", "cars.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("name").remove();
            return fr.find("cylinders");
          }
        },
        1,
        ard(ard(0, 4, 0, 0, 0),
            ard(0, 193, 5, 9, 0),
            ard(0, 2, 1, 0, 0),
            ard(0, 65, 3, 16, 0),
            ard(0, 11, 0, 7, 90)),
        s("3", "4", "5", "6", "8"),
        DeepLearningParameters.Activation.Rectifier);
  }

  @Test public void testClassCars5() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/junit/cars.csv", "cars5.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("name").remove();
            return fr.find("cylinders");
          }
        },
        5,
        ard(ard(0, 4, 0, 0, 0),
            ard(0, 206, 0, 1, 0),
            ard(0, 2, 0, 1, 0),
            ard(0, 14, 0, 69, 1),
            ard(0, 0, 0, 6, 102)),
        s("3", "4", "5", "6", "8"),
        DeepLearningParameters.Activation.Rectifier);
  }

  @Test public void testConstantCols() throws Throwable {
    try {
      basicDLTest_Classification(
          "./smalldata/poker/poker100", "poker.hex",
          new PrepData() {
            @Override
            int prep(Frame fr) {
              for (int i = 0; i < 7; i++) {
                Vec v = fr.remove(3);
                if (v!=null) v.remove();
              }
              return 3;
            }
          },
          1,
          null,
          null,
          DeepLearningParameters.Activation.Rectifier);
      Assert.fail();
    } catch( H2OModelBuilderIllegalArgumentException iae ) {
    /*pass*/
    }
  }

  @Test public void testBadData() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/junit/drf_infinities.csv", "infinitys.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            return fr.find("DateofBirth");
          }
        },
        1,
        ard(ard(0, 17),
            ard(0, 17)),
        s("0", "1"),
        DeepLearningParameters.Activation.Rectifier);
  }

  @Test public void testCreditProstate1() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/logreg/prostate.csv", "prostate.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("CAPSULE");
          }
        },
        1,
        ard(ard(97, 130),
            ard(28, 125)),
        s("0", "1"),
        DeepLearningParameters.Activation.Rectifier);
  }

  @Test public void testCreditProstateReLUDropout() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/logreg/prostate.csv", "prostateReLUDropout.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("CAPSULE");
          }
        },
        1,
        ard(ard(4, 223),
            ard(0, 153)),
        s("0", "1"),
        DeepLearningParameters.Activation.RectifierWithDropout);

  }

  @Test public void testCreditProstateTanh() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/logreg/prostate.csv", "prostateTanh.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("CAPSULE");
          }
        },
        1,
        ard(ard(141, 86),
            ard(25, 128)),
        s("0", "1"),
        DeepLearningParameters.Activation.Tanh);
  }

  @Test public void testCreditProstateTanhDropout() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/logreg/prostate.csv", "prostateTanhDropout.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("CAPSULE");
          }
        },
        1,
        ard(ard(110, 117),
            ard(23, 130)),
        s("0", "1"),
        DeepLearningParameters.Activation.TanhWithDropout);
  }

  @Test public void testCreditProstateMaxout() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/logreg/prostate.csv", "prostateMaxout.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("CAPSULE");
          }
        },
        100,
        ard(ard(189, 38),
            ard(30, 123)),
        s("0", "1"),
        DeepLearningParameters.Activation.Maxout);
  }

  @Test public void testCreditProstateMaxoutDropout() throws Throwable {
    basicDLTest_Classification(
            "./smalldata/logreg/prostate.csv", "prostateMaxoutDropout.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("CAPSULE");
              }
            },
            100,
            ard(ard(183, 44),
                    ard(40, 113)),
            s("0", "1"),
            DeepLearningParameters.Activation.MaxoutWithDropout);
  }

  @Test public void testCreditProstateRegression1() throws Throwable {
    basicDLTest_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            1,
            46.26952683659,
            DeepLearningParameters.Activation.Rectifier);

  }

  @Test public void testCreditProstateRegressionTanh() throws Throwable {
    basicDLTest_Regression(
        "./smalldata/logreg/prostate.csv", "prostateRegressionTanh.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("AGE");
          }
        },
        1,
        43.457087913127,
        DeepLearningParameters.Activation.Tanh);

  }

  @Test public void testCreditProstateRegressionMaxout() throws Throwable {
    basicDLTest_Regression(
        "./smalldata/logreg/prostate.csv", "prostateRegressionMaxout.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("AGE");
          }
        },
        100,
        32.81408434266,
        DeepLearningParameters.Activation.Maxout);

  }

  @Test public void testCreditProstateRegression5() throws Throwable {
    basicDLTest_Regression(
        "./smalldata/logreg/prostate.csv", "prostateRegression5.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            fr.remove("ID").remove();
            return fr.find("AGE");
          }
        },
        5,
        41.8498354737908,
        DeepLearningParameters.Activation.Rectifier);

  }

  @Test public void testCreditProstateRegression50() throws Throwable {
    basicDLTest_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression50.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            50,
            37.93380250522667,
            DeepLearningParameters.Activation.Rectifier);

  }
  @Test public void testAlphabet() throws Throwable {
    basicDLTest_Classification(
            "./smalldata/gbm_test/alphabet_cattest.csv", "alphabetClassification.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("y");
              }
            },
            10,
            ard(ard(2080, 0),
                    ard(0, 2080)),
            s("0", "1"),
            DeepLearningParameters.Activation.Rectifier);
  }
  @Test public void testAlphabetRegression() throws Throwable {
    basicDLTest_Regression(
            "./smalldata/gbm_test/alphabet_cattest.csv", "alphabetRegression.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("y");
              }
            },
            10,
            4.975570190016591E-6,
            DeepLearningParameters.Activation.Rectifier);
  }

  @Ignore  //1-vs-5 node discrepancy (parsing into different number of chunks?)
  @Test public void testAirlines() throws Throwable {
    basicDLTest_Classification(
            "./smalldata/airlines/allyears2k_headers.zip", "airlines.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                for (String s : new String[]{
                        "DepTime", "ArrTime", "ActualElapsedTime",
                        "AirTime", "ArrDelay", "DepDelay", "Cancelled",
                        "CancellationCode", "CarrierDelay", "WeatherDelay",
                        "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed", "TailNum"
                }) {
                  fr.remove(s).remove();
                }
                return fr.find("IsDepDelayed");
              }
            },
            7,
            ard(ard(9251, 11636),
                    ard(3053, 200038)),
            s("NO", "YES"),
            DeepLearningParameters.Activation.Rectifier);
  }

  @Ignore //PUBDEV-1001
  @Test public void testCzechboard() throws Throwable {
    basicDLTest_Classification(
        "./smalldata/gbm_test/czechboard_300x300.csv", "czechboard_300x300.hex",
        new PrepData() {
          @Override
          int prep(Frame fr) {
            Vec resp = fr.remove("C2");
            fr.add("C2", resp.toCategoricalVec());
            resp.remove();
            return fr.find("C3");
          }
        },
        1,
        ard(ard(7, 44993),
            ard(2, 44998)),
        s("0", "1"),
        DeepLearningParameters.Activation.Tanh);
  }


  // Put response as the last vector in the frame and return possible frames to clean up later
  static Vec unifyFrame(DeepLearningParameters drf, Frame fr, PrepData prep, boolean classification) {
    int idx = prep.prep(fr);
    if( idx < 0 ) { idx = ~idx; }
    String rname = fr._names[idx];
    drf._response_column = fr.names()[idx];

    Vec resp = fr.vecs()[idx];
    Vec ret = null;
    if (classification) {
      ret = fr.remove(idx);
      fr.add(rname,resp.toCategoricalVec());
    } else {
      fr.remove(idx);
      fr.add(rname,resp);
    }
    return ret;
  }

  public void basicDLTest_Classification(String fnametrain, String hexnametrain, PrepData prep, int epochs, double[][] expCM, String[] expRespDom, DeepLearningParameters.Activation activation) throws Throwable { basicDL(fnametrain, hexnametrain, null, prep, epochs, expCM, expRespDom, -1, new int[]{10, 10}, 1e-5, true, activation); }
  public void basicDLTest_Regression(String fnametrain, String hexnametrain, PrepData prep, int epochs, double expMSE, DeepLearningParameters.Activation activation) throws Throwable { basicDL(fnametrain, hexnametrain, null, prep, epochs, null, null, expMSE, new int[]{10, 10}, 1e-5, false, activation); }

  public void basicDL(String fnametrain, String hexnametrain, String fnametest, PrepData prep, int epochs, double[][] expCM, String[] expRespDom, double expMSE, int[] hidden, double l1, boolean classification, DeepLearningParameters.Activation activation) throws Throwable {
    Scope.enter();
    DeepLearningParameters dl = new DeepLearningParameters();
    Frame frTest = null, pred = null;
    Frame frTrain = null;
    Frame test = null, res = null;
    DeepLearningModel model = null;
    try {
      frTrain = parseTestFile(fnametrain);
      Vec removeme = unifyFrame(dl, frTrain, prep, classification);
      if (removeme != null) Scope.track(removeme);
      DKV.put(frTrain._key, frTrain);
      // Configure DL
      dl._train = frTrain._key;
      dl._response_column = ((Frame)DKV.getGet(dl._train)).lastVecName();
      dl._seed = (1L<<32)|2;
      dl._reproducible = true;
      dl._epochs = epochs;
      dl._stopping_rounds = 0;
      dl._activation = activation;
      dl._export_weights_and_biases = RandomUtils.getRNG(fnametrain.hashCode()).nextBoolean();
      dl._hidden = hidden;
      dl._l1 = l1;
      dl._elastic_averaging = false;

      // Invoke DL and block till the end
      DeepLearning job = new DeepLearning(dl,Key.<DeepLearningModel>make("DL_model_" + hexnametrain));
      // Get the model
      model = job.trainModel().get();
      Log.info(model._output);
      assertTrue(job.isStopped()); //HEX-1817

      hex.ModelMetrics mm;
      if (fnametest != null) {
        frTest = parseTestFile(fnametest);
        pred = model.score(frTest);
        mm = hex.ModelMetrics.getFromDKV(model, frTest);
        // Check test set CM
      } else {
        pred = model.score(frTrain);
        mm = hex.ModelMetrics.getFromDKV(model, frTrain);
      }

      test = parseTestFile(fnametrain);
      res = model.score(test);

      if (classification) {
        assertTrue("Expected: " + Arrays.deepToString(expCM) + ", Got: " + Arrays.deepToString(mm.cm()._cm),
            Arrays.deepEquals(mm.cm()._cm, expCM));

        String[] cmDom = model._output._domains[model._output._domains.length - 1];
        Assert.assertArrayEquals("CM domain differs!", expRespDom, cmDom);
        Log.info("\nTraining CM:\n" + mm.cm().toASCII());
        Log.info("\nTraining CM:\n" + hex.ModelMetrics.getFromDKV(model, test).cm().toASCII());
      } else {
        assertTrue("Expected: " + expMSE + ", Got: " + mm.mse(), MathUtils.compare(expMSE, mm.mse(), 1e-8, 1e-8));
        Log.info("\nOOB Training MSE: " + mm.mse());
        Log.info("\nTraining MSE: " + hex.ModelMetrics.getFromDKV(model, test).mse());
      }

      hex.ModelMetrics.getFromDKV(model, test);

      // Build a POJO, validate same results
      assertTrue(model.testJavaScoring(test, res, 1e-5));

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

  @Test public void elasticAveragingTrivial() {
    DeepLearningParameters dl;
    Frame frTrain;
    int N = 2;
    DeepLearningModel [] models = new DeepLearningModel[N];
    dl = new DeepLearningParameters();
    Scope.enter();
    try {
      for (int i = 0; i < N; ++i) {
        frTrain = parseTestFile("./smalldata/covtype/covtype.20k.data");
        Vec resp = frTrain.lastVec().toCategoricalVec();
        frTrain.remove(frTrain.vecs().length - 1).remove();
        frTrain.add("Response", resp);
        DKV.put(frTrain);
        dl._train = frTrain._key;
        dl._response_column = ((Frame) DKV.getGet(dl._train)).lastVecName();
        dl._export_weights_and_biases = true;
        dl._hidden = new int[]{17, 11};
        dl._quiet_mode = false;

        // make it reproducible
        dl._seed = 1234;
        dl._reproducible = true;

        // only do one M/R iteration, and there's no elastic average yet - so the two paths below should be identical
        dl._epochs = 1;
        dl._train_samples_per_iteration = -1;

        if (i == 0) {
          // no elastic averaging
          dl._elastic_averaging = false;
          dl._elastic_averaging_moving_rate = 0.5; //ignored
          dl._elastic_averaging_regularization = 0.9; //ignored
        } else {
          // no-op elastic averaging
          dl._elastic_averaging = true; //go different path, but don't really do anything because of epochs=1 and train_samples_per_iteration=-1
          dl._elastic_averaging_moving_rate = 0.5; //doesn't matter, it's not used since we only do one M/R iteration and there's no time average
          dl._elastic_averaging_regularization = 0.1; //doesn't matter, since elastic average isn't yet available in first iteration
        }

        // Invoke DL and block till the end
        DeepLearning job = new DeepLearning(dl);
        // Get the model
        models[i] = job.trainModel().get();
        frTrain.remove();
      }
      for (int i = 0; i < N; ++i) {
        Log.info(models[i]._output._training_metrics.cm().table().toString());
        Assert.assertEquals(models[i]._output._training_metrics._MSE, models[0]._output._training_metrics._MSE, 1e-6);
      }

    }finally{
      for (int i=0; i<N; ++i)
        if (models[i] != null)
          models[i].delete();
      Scope.exit();
    }
  }

  @Ignore
  @Test public void elasticAveraging() {
    DeepLearningParameters dl;
    Frame frTrain;
    int N = 2;
    DeepLearningModel [] models = new DeepLearningModel[N];
    dl = new DeepLearningParameters();
    Scope.enter();
    boolean covtype = true; //new Random().nextBoolean();
    if (covtype) {
      frTrain = parseTestFile("./smalldata/covtype/covtype.20k.data");
      Vec resp = frTrain.lastVec().toCategoricalVec();
      frTrain.remove(frTrain.vecs().length - 1).remove();
      frTrain.add("Response", resp);
    } else {
      frTrain = parseTestFile("./bigdata/server/HIGGS.csv");
      Vec resp = frTrain.vecs()[0].toCategoricalVec();
      frTrain.remove(0).remove();
      frTrain.prepend("Response", resp);
    }
    DKV.put(frTrain);
    try {
      for (int i = 0; i < N; ++i) {
        dl._train = frTrain._key;
        String[] n = ((Frame) DKV.getGet(dl._train)).names();
        if (covtype) {
          dl._response_column = n[n.length-1];
          dl._ignored_columns = null;
        } else {
          dl._response_column = n[0];
          dl._ignored_columns = new String[]{n[22], n[23], n[24], n[25], n[26], n[27], n[28]};
        }
        dl._export_weights_and_biases = true;
        dl._hidden = new int[]{64, 64};
        dl._quiet_mode = false;
        dl._max_w2 = 10;
        dl._l1 = 1e-5;
        dl._reproducible = false;
        dl._replicate_training_data = false; //every node only has a piece of the data
        dl._force_load_balance = true; //use multi-node

        dl._epochs = 10;
        dl._train_samples_per_iteration = frTrain.numRows()/100; //100 M/R steps per epoch

        dl._elastic_averaging = i==1;
        dl._elastic_averaging_moving_rate = 0.999;
        dl._elastic_averaging_regularization = 1e-4;

        // Invoke DL and block till the end
        DeepLearning job = new DeepLearning(dl);
        // Get the model
        models[i] = job.trainModel().get();
      }
      for (int i = 0; i < N; ++i) {
        if (models[i] != null)
          Log.info(models[i]._output._training_metrics.cm().table().toString());
      }
      if (models[0] != null)
        Log.info("Without elastic averaging: error=" + models[0]._output._training_metrics.cm().err());
      if (models[1] != null)
        Log.info("With elastic averaging:    error=" + models[1]._output._training_metrics.cm().err());
//      if (models[0] != null && models[1] != null)
//        Assert.assertTrue(models[1]._output._training_metrics.cm().err() < models[0]._output._training_metrics.cm().err());

    }finally{
      frTrain.remove();
      for (int i=0; i<N; ++i)
        if (models[i] != null)
          models[i].delete();
      Scope.exit();
    }
  }

  @Test
  public void testNoRowWeights() {
    Frame tfr = null, vfr = null, pred = null, fr2 = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/no_weights.csv");
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._reproducible = true;
      parms._seed = 0xdecaf;
      parms._l1 = 0.1;
      parms._epochs = 1;
      parms._hidden = new int[]{1};
      parms._classification_stop = -1;

      // Build a first model; all remaining models should be equal
      DeepLearningModel dl = new DeepLearning(parms).trainModel().get();

      pred = dl.score(parms.train());
      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(dl, parms.train());
      assertEquals(0.7592592592592592, mm.auc_obj()._auc, 1e-8);

      double mse = dl._output._training_metrics.mse();
      assertEquals(0.314813341867078, mse, 1e-8);

      assertTrue(dl.testJavaScoring(tfr, fr2 = dl.score(tfr), 1e-5));
      dl.delete();
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (pred != null) pred.remove();
      if (fr2 != null) fr2.remove();
    }
    Scope.exit();
  }

  @Test
  public void testRowWeightsOne() {
    Frame tfr = null, vfr = null, pred = null, fr2 = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights_all_ones.csv");
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._reproducible = true;
      parms._seed = 0xdecaf;
      parms._classification_stop = -1;
      parms._l1 = 0.1;
      parms._hidden = new int[]{1};
      parms._epochs = 1;

      // Build a first model; all remaining models should be equal
      DeepLearningModel dl = new DeepLearning(parms).trainModel().get();

      pred = dl.score(parms.train());
      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(dl, parms.train());
      assertEquals(0.7592592592592592, mm.auc_obj()._auc, 1e-8);

      double mse = dl._output._training_metrics.mse();
      assertEquals(0.3148133418670781, mse, 1e-8); //Note: better results than non-shuffled

//      assertTrue(dl.testJavaScoring(tfr, fr2=dl.score(tfr, 1e-5)); //PUBDEV-1900
      dl.delete();
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (pred != null) pred.remove();
      if (fr2 != null) fr2.remove();
    }
    Scope.exit();
  }

  @Test
  public void testNoRowWeightsShuffled() {
    Frame tfr = null, vfr = null, pred = null, fr2 = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/no_weights_shuffled.csv");
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._reproducible = true;
      parms._seed = 0xdecaf;
      parms._l1 = 0.1;
      parms._epochs = 1;
      parms._hidden = new int[]{1};
      parms._classification_stop = -1;

      // Build a first model; all remaining models should be equal
      DeepLearningModel dl = new DeepLearning(parms).trainModel().get();

      pred = dl.score(parms.train());
      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(dl, parms.train());
      assertEquals(0.7222222222222222, mm.auc_obj()._auc, 1e-8);

      double mse = dl._output._training_metrics.mse();
      assertEquals(0.31643071339946, mse, 1e-8);

      assertTrue(dl.testJavaScoring(tfr, fr2 = dl.score(tfr), 1e-5));
      dl.delete();
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (pred != null) pred.remove();
      if (fr2 != null) fr2.remove();
    }
    Scope.exit();
  }

  @Test
  public void testRowWeights() {
    Frame tfr = null, pred = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/junit/weights.csv");
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = "response";
      parms._weights_column = "weight";
      parms._reproducible = true;
      parms._seed = 0xdecaf;
      parms._classification_stop = -1;
      parms._l1 = 0.1;
      parms._hidden = new int[]{1};
      parms._epochs = 10;

      // Build a first model; all remaining models should be equal
      DeepLearningModel dl = new DeepLearning(parms).trainModel().get();

      pred = dl.score(parms.train());
      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(dl, parms.train());
      assertEquals(0.7592592592592592, mm.auc_obj()._auc, 1e-8);

      double mse = dl._output._training_metrics.mse();
      assertEquals(0.3116490253190556, mse, 1e-8);

//      Assert.assertTrue(dl.testJavaScoring(tfr,fr2=dl.score(tfr),1e-5)); //PUBDEV-1900
      dl.delete();
    } finally {
      if (tfr != null) tfr.remove();
      if (pred != null) pred.remove();
    }
    Scope.exit();
  }

  static class PrintEntries extends MRTask<PrintEntries> {
    @Override
    public void map(Chunk[] cs) {
      StringBuilder sb = new StringBuilder();
      for (int r=0; r<cs[0].len(); ++r) {
        sb.append("Row " + (cs[0].start() + r) + ": ");
        for (int i=0; i<cs.length; ++i) {
          if (i==0) //response
            sb.append("response: " + _fr.vec(i).domain()[(int)cs[i].at8(r)] + " ");
          if (cs[i].atd(r) != 0) {
            sb.append(i + ":" + cs[i].atd(r) + " ");
          }
        }
        sb.append("\n");
      }
      Log.info(sb);
    }
  }

  @Ignore
  @Test public void testWhatever() {
    DeepLearningParameters dl;
    Frame first1kSVM = null;
    Frame second1kSVM = null;
    Frame third1kSVM = null;

    Frame first1kCSV = null;
    Frame second1kCSV = null;
    Frame third1kCSV = null;

    DeepLearningModel model = null;
    dl = new DeepLearningParameters();
    Scope.enter();
    try {
//      first1kSVM = parseTestFile("/users/arno/first1k.svm");
//      Scope.track(first1kSVM.replace(0, first1kSVM.vec(0).toCategoricalVec())._key);
//      DKV.put(first1kSVM);
//
//      second1kSVM = parseTestFile("/users/arno/second1k.svm");
//      Scope.track(second1kSVM.replace(0, second1kSVM.vec(0).toCategoricalVec())._key);
//      DKV.put(second1kSVM);
//
//      third1kSVM = parseTestFile("/users/arno/third1k.svm");
//      Scope.track(third1kSVM.replace(third1kSVM.find("C1"), third1kSVM.vec("C1")).toCategoricalVec()._key);
//      DKV.put(third1kSVM);

      first1kCSV = parseTestFile("/users/arno/first1k.csv");
//      first1kCSV.remove(first1kCSV.find("C1")).remove(); //remove id
      Vec response = first1kCSV.remove(first1kCSV.find("C2")); //remove response, but keep it around outside the frame
      Vec responseFactor = response.toCategoricalVec(); //turn response into a categorical
      response.remove();
//      first1kCSV.prepend("C2", first1kCSV.anyVec().makeCon(0)); //add a dummy column (will be the first predictor)
      first1kCSV.prepend("C2", responseFactor); //add back response as first column
      DKV.put(first1kCSV);

//      second1kCSV = parseTestFile("/users/arno/second1k.csv");
//      second1kCSV.remove(second1kCSV.find("C1")).remove(); //remove id
//      response = second1kCSV.remove(second1kCSV.find("C2")); //remove response, but keep it around outside the frame
//      responseFactor = response.toCategoricalVec(); //turn response into a categorical
//      response.remove();
//      second1kCSV.prepend("C2", second1kCSV.anyVec().makeCon(0)); //add a dummy column (will be the first predictor)
//      second1kCSV.prepend("C1", responseFactor); //add back response as first column
//      DKV.put(second1kCSV);
//
//      third1kCSV = parseTestFile("/users/arno/third1k.csv");
//      third1kCSV.remove(third1kCSV.find("C1")).remove(); //remove id
//      response = third1kCSV.remove(third1kCSV.find("C2")); //remove response, but keep it around outside the frame
//      responseFactor = response.toCategoricalVec(); //turn response into a categorical
//      response.remove();
//      third1kCSV.prepend("C2", third1kCSV.anyVec().makeCon(0)); //add a dummy column (will be the first predictor)
//      third1kCSV.prepend("C1", responseFactor); //add back response as first column
//      DKV.put(third1kCSV);

      //print non-zeros for each frame
//      Log.info("SVMLight First 1k non-trivial rows");
//      new PrintEntries().doAll(trainSVM);
//      Log.info("DenseCSV First 1k non-trivial rows");
//      new PrintEntries().doAll(trainCSV);

//      Log.info("SVMLight Second 1k non-trivial rows");
//      new PrintEntries().doAll(trainSVM);
//      Log.info("DenseCSV Second 1k non-trivial rows");
//      new PrintEntries().doAll(testCSV);

      // Configure DL
      dl._train = first1kCSV._key;
//      dl._valid = second1kSVM._key;
      dl._ignored_columns = new String[]{"C1"};
      dl._response_column = "C2";
      dl._epochs = 10; //default
      dl._reproducible = true; //default
      dl._seed = 1234;
      dl._ignore_const_cols = false; //default
      dl._sparse = true; //non-default, much faster here for sparse data
      dl._hidden = new int[]{10, 10};

      // Invoke DL and block till the end
      DeepLearning job = new DeepLearning(dl);
      // Get the model
      model = job.trainModel().get();
      Log.info(model._output);

//        Log.info("Holdout CSV");
//        model.score(third1kCSV).delete();
//
//        Log.info("Holdout SVM");
//        model.score(third1kSVM).delete();
//
//        Log.info("POJO SVM Train Check");
      Frame pred = null;
//        assertTrue(model.testJavaScoring(first1kSVM, pred = model.score(first1kSVM), 1e-5));
//        pred.remove();
//
//        Log.info("POJO SVM Validation Check");
//        DKV.remove(model._key); model._key = Key.make(); DKV.put(model);
//        assertTrue(model.testJavaScoring(second1kSVM,  pred = model.score(second1kSVM), 1e-5));
//        pred.remove();
//
//        Log.info("POJO SVM Test Check");
//        DKV.remove(model._key); model._key = Key.make(); DKV.put(model);
//        assertTrue(model.testJavaScoring(third1kSVM,  pred = model.score(third1kSVM), 1e-5));
//        pred.remove();

      Log.info("POJO CSV Train Check");
      DKV.remove(model._key); model._key = Key.make(); DKV.put(model);
      assertTrue(model.testJavaScoring(first1kCSV,  pred = model.score(first1kCSV), 1e-5));
      pred.remove();

//        Log.info("POJO CSV Validation Check");
//        DKV.remove(model._key); model._key = Key.make(); DKV.put(model);
//        assertTrue(model.testJavaScoring(second1kCSV,  pred = model.score(second1kCSV), 1e-5));
//        pred.remove();
//
//        Log.info("POJO CSV Test Check");
//        DKV.remove(model._key); model._key = Key.make(); DKV.put(model);
//        assertTrue(model.testJavaScoring(third1kCSV, pred = model.score(third1kSVM), 1e-5));
//        pred.remove();
//
//        Log.info("POJO SVM vs H2O CSV Test Check");
//        DKV.remove(model._key); model._key = Key.make(); DKV.put(model);
//        assertTrue(model.testJavaScoring(third1kSVM, pred = model.score(third1kCSV), 1e-5));
//        pred.remove();
//
//        Log.info("POJO CSV vs H2O SVM Test Check");
//        DKV.remove(model._key); model._key = Key.make(); DKV.put(model);
//        assertTrue(model.testJavaScoring(third1kSVM, pred = model.score(third1kCSV), 1e-5));
//        pred.remove();
      assertTrue(job.isStopped()); //HEX-1817
    } finally {
      if (first1kSVM != null) first1kSVM.remove();
      if (second1kSVM != null) second1kSVM.remove();
      if (third1kSVM != null) third1kSVM.remove();
      if (first1kCSV != null) first1kCSV.remove();
      if (second1kCSV != null) second1kCSV.remove();
      if (third1kCSV != null) third1kCSV.remove();
      if (model != null) model.delete();
      Scope.exit();
    }
  }

  // just a simple sanity check - not a golden test
  @Test
  public void testLossFunctions() {
    Frame tfr = null, vfr = null, fr2 = null;
    DeepLearningModel dl = null;

    for (DeepLearningParameters.Loss loss: new DeepLearningParameters.Loss[]{
        DeepLearningParameters.Loss.Automatic,
        DeepLearningParameters.Loss.Quadratic,
        DeepLearningParameters.Loss.Huber,
        DeepLearningParameters.Loss.Absolute,
        DeepLearningParameters.Loss.Quantile,
    }) {
      Scope.enter();
      try {
        tfr = parseTestFile("smalldata/glm_test/cancar_logIn.csv");
        for (String s : new String[]{
            "Merit", "Class"
        }) {
          Scope.track(tfr.replace(tfr.find(s), tfr.vec(s).toCategoricalVec()));
        }
        DKV.put(tfr);
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._epochs = 1;
        parms._reproducible = true;
        parms._hidden = new int[]{50,50};
        parms._response_column = "Cost";
        parms._seed = 0xdecaf;
        parms._loss = loss;

        // Build a first model; all remaining models should be equal
        DeepLearning job = new DeepLearning(parms);
        dl = job.trainModel().get();

        ModelMetricsRegression mm = (ModelMetricsRegression)dl._output._training_metrics;

        if (loss == DeepLearningParameters.Loss.Automatic || loss == DeepLearningParameters.Loss.Quadratic)
          Assert.assertEquals(mm._mean_residual_deviance, mm._MSE, 1e-6);
        else
          assertTrue(mm._mean_residual_deviance != mm._MSE);

        assertTrue(dl.testJavaScoring(tfr, fr2=dl.score(tfr), 1e-5));

      } finally {
        if (tfr != null) tfr.remove();
        if (dl != null) dl.delete();
        if (fr2 != null) fr2.remove();
        Scope.exit();
      }
    }
  }

  @Test
  public void testDistributions() {
    Frame tfr = null, vfr = null, fr2 = null;
    DeepLearningModel dl = null;

    for (DistributionFamily dist : new DistributionFamily[] {
        AUTO,
        gaussian,
        poisson,
        gamma,
        tweedie,
    }) {
      Scope.enter();
      try {
        tfr = parseTestFile("smalldata/glm_test/cancar_logIn.csv");
        for (String s : new String[]{
            "Merit", "Class"
        }) {
          Scope.track(tfr.replace(tfr.find(s), tfr.vec(s).toCategoricalVec()));
        }
        DKV.put(tfr);
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._epochs = 1;
        parms._reproducible = true;
        parms._hidden = new int[]{50,50};
        parms._response_column = "Cost";
        parms._seed = 0xdecaf;
        parms._distribution = dist;

        // Build a first model; all remaining models should be equal
        DeepLearning job = new DeepLearning(parms);
        dl = job.trainModel().get();

        ModelMetricsRegression mm = (ModelMetricsRegression)dl._output._training_metrics;

        if (dist == gaussian || dist == AUTO)
          Assert.assertEquals(mm._mean_residual_deviance, mm._MSE, 1e-6);
        else
          assertTrue(mm._mean_residual_deviance != mm._MSE);

        assertTrue(dl.testJavaScoring(tfr, fr2=dl.score(tfr), 1e-5));

      } finally {
        if (tfr != null) tfr.remove();
        if (dl != null) dl.delete();
        if (fr2 != null) fr2.delete();
        Scope.exit();
      }
    }
  }

  @Test
  public void testAutoEncoder() throws Exception {
    Frame tfr = null, vfr = null, fr2 = null;
    DeepLearningModel dl = null;

    Scope.enter();
    try {
      tfr = parseTestFile("smalldata/glm_test/cancar_logIn.csv");
      for (String s : new String[]{
          "Merit", "Class"
      }) {
        Scope.track(tfr.replace(tfr.find(s), tfr.vec(s).toCategoricalVec()));
      }
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 10;
      parms._reproducible = true;
      parms._hidden = new int[]{5,5,5};
      parms._response_column = "Cost";
      parms._seed = 0xdecaf;
      parms._autoencoder = true;
      parms._input_dropout_ratio = 0.1;
      parms._activation = DeepLearningParameters.Activation.Tanh;

      // Build a first model; all remaining models should be equal
      dl = new DeepLearning(parms).trainModel().get();

      URI uri = dl.exportMojo("prcak", true);
      
      MojoModel aeMojo = MojoModel.load(uri.getPath(), true);
      

      assertTrue(dl.testJavaScoring(tfr, fr2=dl.score(tfr), 1e-5));

    } finally {
      if (tfr != null) tfr.remove();
      if (dl != null) dl.delete();
      if (fr2 != null) fr2.delete();
      Scope.exit();
    }
  }

  @Test
  public void testNumericalExplosion() {
    for (boolean ae : new boolean[]{
        true,
        false
    }) {
      Frame tfr = null;
      DeepLearningModel dl = null;
      Frame pred = null;

      try {
        tfr = parseTestFile("./smalldata/junit/two_spiral.csv");
        for (String s : new String[]{
            "Class"
        }) {
          Vec resp = tfr.vec(s).toCategoricalVec();
          tfr.remove(s).remove();
          tfr.add(s, resp);
          DKV.put(tfr);
        }
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._epochs = 100;
        parms._response_column = "Class";
        parms._autoencoder = ae;
        parms._reproducible = true;
        parms._train_samples_per_iteration = 10;
        parms._hidden = new int[]{10,10,10,10,10,10,10,10,10,10,10,10,10,10,10,10};
        parms._initial_weight_distribution = DeepLearningParameters.InitialWeightDistribution.Uniform;
        parms._initial_weight_scale = 1e20;
        parms._seed = 0xdecaf;
        parms._max_w2 = 1e20f;

        // Build a first model; all remaining models should be equal
        DeepLearning job = new DeepLearning(parms);
        try {
          dl = job.trainModel().get();
          Assert.fail("Should toss exception instead of reaching here");
        } catch( RuntimeException de ) {
          // catch anything - might be a NPE during cleanup
//          assertTrue(de.getMessage().contains("Trying to predict with an unstable model."));
        }
        dl = DKV.getGet(job.dest());
        try {
          pred = dl.score(tfr);
          Assert.fail("Should toss exception instead of reaching here");
        } catch ( RuntimeException ex) {
          // OK
        }
        assertTrue(dl.model_info().isUnstable());
        assertTrue(dl._output._job.isCrashed());
      } finally {
        if (tfr != null) tfr.delete();
        if (dl != null) dl.delete();
        if (pred != null) pred.delete();
      }
    }
  }

  @Test
  public void testEarlyStopping() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/junit/two_spiral.csv");
      for (String s : new String[]{
              "Class"
      }) {
        Vec resp = tfr.vec(s).toCategoricalVec();
        tfr.remove(s).remove();
        tfr.add(s, resp);
        DKV.put(tfr);
      }
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 100;
      parms._response_column = "Class";
      parms._reproducible = true;
      parms._classification_stop = 0.7;
      parms._score_duty_cycle = 1;
      parms._score_interval = 0;
      parms._hidden = new int[]{100,100};
      parms._seed = 0xdecaf;

      // Build a first model; all remaining models should be equal
      dl = new DeepLearning(parms).trainModel().get();
      assertTrue(dl.stopped_early);
      assertTrue(dl.epoch_counter < 100);
    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testVarimp() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 100;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._classification_stop = 0.7;
      parms._score_duty_cycle = 1;
      parms._score_interval = 0;
      parms._hidden = new int[]{100,100};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      // Build a first model; all remaining models should be equal
      dl = new DeepLearning(parms).trainModel().get();
      Assert.assertTrue(dl.varImp()._varimp != null);
      Log.info(dl.model_info().toStringAll());//for code coverage only
      Assert.assertTrue(ArrayUtils.minValue(dl.varImp()._varimp) > 0.5); //all features matter
      Assert.assertTrue(ArrayUtils.maxValue(dl.varImp()._varimp) <= 1); //all features matter
    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testCheckpointSameEpochs() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 10;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._hidden = new int[]{2,2};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      dl = new DeepLearning(parms).trainModel().get();

      DeepLearningParameters parms2 = (DeepLearningParameters)parms.clone();
      parms2._epochs = 10;
      parms2._checkpoint = dl._key;
      try {
        dl2 = new DeepLearning(parms2).trainModel().get();
        Assert.fail("Should toss exception instead of reaching here");
      } catch (H2OIllegalArgumentException ex) {
      }

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }

  @Test
  public void testCheckpointBackwards() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 10;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._hidden = new int[]{2,2};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      dl = new DeepLearning(parms).trainModel().get();

      DeepLearningParameters parms2 = (DeepLearningParameters)parms.clone();
      parms2._epochs = 9;
      parms2._checkpoint = dl._key;
      try {
        dl2 = new DeepLearning(parms2).trainModel().get();
        Assert.fail("Should toss exception instead of reaching here");
      } catch (H2OIllegalArgumentException ex) {
      }

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }

  @Test
  public void testConvergenceLogloss() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 1000000;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._hidden = new int[]{2,2};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      parms._score_duty_cycle = 0.1;
      parms._score_interval = 0;
      parms._classification_stop = -1; //don't stop based on absolute classification error
      parms._stopping_rounds = 5; //don't stop based on absolute classification error
      parms._stopping_metric = ScoreKeeper.StoppingMetric.logloss; //don't stop based on absolute classification error
      parms._stopping_tolerance = 0.03;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertTrue(dl.epoch_counter < parms._epochs);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }
  @Test
  public void testConvergenceMisclassification() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 1000000;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._hidden = new int[]{2,2};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      parms._score_duty_cycle = 1.0;
      parms._score_interval = 0;
      parms._classification_stop = -1; //don't stop based on absolute classification error
      parms._stopping_rounds = 2; //don't stop based on absolute classification error
      parms._stopping_metric = ScoreKeeper.StoppingMetric.misclassification; //don't stop based on absolute classification error
      parms._stopping_tolerance = 0.0;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertTrue(dl.epoch_counter < parms._epochs);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }
  @Test
  public void testConvergenceDeviance() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/logreg/prostate.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 1000000;
      parms._response_column = "AGE";
      parms._reproducible = true;
      parms._hidden = new int[]{2,2};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      parms._score_duty_cycle = 1.0;
      parms._score_interval = 0;
      parms._classification_stop = -1; //don't stop based on absolute classification error
      parms._stopping_rounds = 2; //don't stop based on absolute classification error
      parms._stopping_metric = ScoreKeeper.StoppingMetric.deviance; //don't stop based on absolute classification error
      parms._stopping_tolerance = 0.0;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertTrue(dl.epoch_counter < parms._epochs);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }
  @Test
  public void testConvergenceAUC() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/logreg/prostate.csv");
      for (String s : new String[]{
              "CAPSULE"
      }) {
        Vec resp = tfr.vec(s).toCategoricalVec();
        tfr.remove(s).remove();
        tfr.add(s, resp);
        DKV.put(tfr);
      }
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 1000000;
      parms._response_column = "CAPSULE";
      parms._reproducible = true;
      parms._hidden = new int[]{2,2};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;

      parms._score_duty_cycle = 1.0;
      parms._score_interval = 0;
      parms._classification_stop = -1; //don't stop based on absolute classification error
      parms._stopping_rounds = 2; //don't stop based on absolute classification error
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC; //don't stop based on absolute classification error
      parms._stopping_tolerance = 0.0;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertTrue(dl.epoch_counter < parms._epochs);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }

  @Test
  public void testNoHiddenLayerRegression() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/logreg/prostate.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 1000;
      parms._response_column = "AGE";
      parms._hidden = new int[]{};
      dl = new DeepLearning(parms).trainModel().get();
      Frame res = dl.score(tfr);
      assertTrue(dl.testJavaScoring(tfr, res, 1e-5));
      res.remove();
    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }

  @Test
  public void testNoHiddenLayerClassification() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/logreg/prostate.csv");
      for (String s : new String[]{
          "CAPSULE"
      }) {
        Vec resp = tfr.vec(s).toCategoricalVec();
        tfr.remove(s).remove();
        tfr.add(s, resp);
        DKV.put(tfr);
      }
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 1000;
      parms._response_column = "CAPSULE";
      parms._hidden = null; // that works too, not just empty array
      dl = new DeepLearning(parms).trainModel().get();
      Frame res = dl.score(tfr);
      assertTrue(dl.testJavaScoring(tfr, res, 1e-5));
      res.remove();
    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }

  @Ignore
  public void testConvergenceAUC_ModifiedHuber() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/logreg/prostate.csv");
      for (String s : new String[]{
              "CAPSULE"
      }) {
        Vec resp = tfr.vec(s).toCategoricalVec();
        tfr.remove(s).remove();
        tfr.add(s, resp);
        DKV.put(tfr);
      }
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 1000000;
      parms._response_column = "CAPSULE";
      parms._reproducible = true;
      parms._hidden = new int[]{2,2};
      parms._seed = 0xdecaf;
      parms._variable_importances = true;
      parms._distribution = modified_huber;

      parms._score_duty_cycle = 1.0;
      parms._score_interval = 0;
      parms._classification_stop = -1; //don't stop based on absolute classification error
      parms._stopping_rounds = 2; //don't stop based on absolute classification error
      parms._stopping_metric = ScoreKeeper.StoppingMetric.AUC; //don't stop based on absolute classification error
      parms._stopping_tolerance = 0.0;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertTrue(dl.epoch_counter < parms._epochs);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
    }
  }
  @Test
  public void testCrossValidation() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._nfolds = 4;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(12.959355363801334,dl._output._training_metrics._MSE,1e-6);
      Assert.assertEquals(17.296871012606317,dl._output._cross_validation_metrics._MSE,1e-6);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testMiniBatch1() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._mini_batch_size = 1;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(12.938076268040659,dl._output._training_metrics._MSE,1e-6);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testMiniBatch50() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._mini_batch_size = 50;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(12.938076268040659,dl._output._training_metrics._MSE,1e-6);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }


  @Test
  public void testPretrainedAE() {
    Frame tfr = null;
    DeepLearningModel dl1 = null;
    DeepLearningModel dl2 = null;
    DeepLearningModel ae = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      Vec r = tfr.remove("chas");
      tfr.add("chas",r.toCategoricalVec());
      DKV.put(tfr);
      r.remove();

      // train unsupervised AE
      Key<DeepLearningModel> key = Key.make("ae_model");
      {
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._ignored_columns = new String[]{"chas"};
        parms._activation = DeepLearningParameters.Activation.TanhWithDropout;
        parms._reproducible = true;
        parms._hidden = new int[]{20, 20};
        parms._input_dropout_ratio = 0.1;
        parms._hidden_dropout_ratios = new double[]{0.2, 0.1};
        parms._autoencoder = true;
        parms._seed = 0xdecaf;
        ae = new DeepLearning(parms, key).trainModel().get();
        // test POJO
        Frame res = ae.score(tfr);
        assertTrue(ae.testJavaScoring(tfr, res, 1e-5));
        res.remove();
      }

      // train supervised DL model
      {
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._response_column = "chas";
        parms._activation = DeepLearningParameters.Activation.TanhWithDropout;
        parms._reproducible = true;
        parms._hidden = new int[]{20, 20};
        parms._input_dropout_ratio = 0.1;
        parms._hidden_dropout_ratios = new double[]{0.2, 0.1};
        parms._seed = 0xdecad;
        parms._pretrained_autoencoder = key;
        parms._rate_decay=1.0;
        parms._adaptive_rate=false;
        parms._rate_annealing=1e-3;
        parms._loss= DeepLearningParameters.Loss.CrossEntropy;
        dl1 = new DeepLearning(parms).trainModel().get();
        // test POJO
        Frame res = dl1.score(tfr);
        assertTrue(dl1.testJavaScoring(tfr, res, 1e-5));
        res.remove();
      }

      // train DL model from scratch
      {
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._response_column = "chas";
        parms._activation = DeepLearningParameters.Activation.TanhWithDropout;
        parms._reproducible = true;
        parms._hidden = new int[]{20, 20};
        parms._input_dropout_ratio = 0.1;
        parms._hidden_dropout_ratios = new double[]{0.2, 0.1};
        parms._seed = 0xdecad;
        parms._rate_decay=1.0;
        parms._adaptive_rate=false;
        parms._rate_annealing=1e-3;
        dl2 = new DeepLearning(parms).trainModel().get();
        // test POJO
        Frame res = dl2.score(tfr);
        assertTrue(dl2.testJavaScoring(tfr, res, 1e-5));
        res.remove();
      }

      Log.info("pretrained  : MSE=" + dl1._output._training_metrics.mse());
      Log.info("from scratch: MSE=" + dl2._output._training_metrics.mse());
//      Assert.assertTrue(dl1._output._training_metrics.mse() < dl2._output._training_metrics.mse());


    } finally {
      if (tfr != null) tfr.delete();
      if (ae != null) ae.delete();
      if (dl1 != null) dl1.delete();
      if (dl2 != null) dl2.delete();
    }
  }

  @Test
  public void testInitialWeightsAndBiases() {
    Frame tfr = null;
    DeepLearningModel dl1 = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");

      // train DL model from scratch
      {
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._response_column = tfr.lastVecName();
        parms._activation = DeepLearningParameters.Activation.Tanh;
        parms._reproducible = true;
        parms._hidden = new int[]{20, 20};
        parms._seed = 0xdecad;
        parms._export_weights_and_biases = true;
        dl1 = new DeepLearning(parms).trainModel().get();
      }

      // train DL model starting from weights/biases from first model
      {
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._response_column = tfr.lastVecName();
        parms._activation = DeepLearningParameters.Activation.Tanh;
        parms._reproducible = true;
        parms._hidden = new int[]{20, 20};
        parms._seed = 0xdecad;
        parms._initial_weights = dl1._output.weights;
        parms._initial_biases = dl1._output.biases;
        parms._epochs = 0;
        dl2 = new DeepLearning(parms).trainModel().get();
      }

      Log.info("dl1  : MSE=" + dl1._output._training_metrics.mse());
      Log.info("dl2  : MSE=" + dl2._output._training_metrics.mse());
      Assert.assertTrue(Math.abs(dl1._output._training_metrics.mse() - dl2._output._training_metrics.mse()) < 1e-6);


    } finally {
      if (tfr != null) tfr.delete();
      if (dl1 != null) dl1.delete();
      if (dl2 != null) dl2.delete();
      for (Key f : dl1._output.weights) f.remove();
      for (Key f : dl1._output.biases) f.remove();
    }
  }

  @Test
  public void testInitialWeightsAndBiasesPartial() {
    Frame tfr = null;
    DeepLearningModel dl1 = null;
    DeepLearningModel dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");

      // train DL model from scratch
      {
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._response_column = tfr.lastVecName();
        parms._activation = DeepLearningParameters.Activation.Tanh;
        parms._reproducible = true;
        parms._hidden = new int[]{20, 20};
        parms._seed = 0xdecad;
        parms._export_weights_and_biases = true;
        dl1 = new DeepLearning(parms).trainModel().get();
      }

      // train DL model starting from weights/biases from first model
      {
        DeepLearningParameters parms = new DeepLearningParameters();
        parms._train = tfr._key;
        parms._response_column = tfr.lastVecName();
        parms._activation = DeepLearningParameters.Activation.Tanh;
        parms._reproducible = true;
        parms._hidden = new int[]{20, 20};
        parms._seed = 0xdecad;
        parms._initial_weights = dl1._output.weights;
        parms._initial_biases = dl1._output.biases;
        parms._initial_weights[1].remove();
        parms._initial_weights[1] = null;
        parms._initial_biases[0].remove();
        parms._initial_biases[0] = null;
        parms._epochs = 10;
        dl2 = new DeepLearning(parms).trainModel().get();
      }

      Log.info("dl1  : MSE=" + dl1._output._training_metrics.mse());
      Log.info("dl2  : MSE=" + dl2._output._training_metrics.mse());

      // the second model is better since it got warm-started at least partially
      Assert.assertTrue(dl1._output._training_metrics.mse() > dl2._output._training_metrics.mse());


    } finally {
      if (tfr != null) tfr.delete();
      if (dl1 != null) dl1.delete();
      if (dl2 != null) dl2.delete();
      for (Key f : dl1._output.weights) if (f!=null) f.remove();
      for (Key f : dl1._output.biases) if (f!=null) f.remove();
    }
  }

  @Test
  public void testLaplace() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._distribution = laplace;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(2.31398/*MAE*/,((ModelMetricsRegression)dl._output._training_metrics)._mean_residual_deviance,1e-5);
      Assert.assertEquals(14.889,((ModelMetricsRegression)dl._output._training_metrics)._MSE,1e-3);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testGaussian() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._distribution = gaussian;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(12.93808 /*MSE*/,((ModelMetricsRegression)dl._output._training_metrics)._mean_residual_deviance,1e-5);
      Assert.assertEquals(12.93808 /*MSE*/,((ModelMetricsRegression)dl._output._training_metrics)._MSE,1e-5);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testHuberDeltaLarge() {
    Frame tfr = null;
    DeepLearningModel dl = null, dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._distribution = huber;
      parms._huber_alpha = 1; //just like gaussian

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(12.93808 /*MSE*/,((ModelMetricsRegression)dl._output._training_metrics)._mean_residual_deviance,0.7);
      Assert.assertEquals(12.93808 /*MSE*/,((ModelMetricsRegression)dl._output._training_metrics)._MSE,0.7);

      // the same for distribution = AUTO representing Huber:
      DeepLearningParameters parms2 = new DeepLearningParameters();
      parms2._train = tfr._key;
      parms2._response_column = tfr.lastVecName();
      parms2._reproducible = true;
      parms2._hidden = new int[]{20,20};
      parms2._seed = 0xdecaf;
      parms2._distribution = AUTO;
      parms2._loss = DeepLearningParameters.Loss.Huber;
      parms2._huber_alpha = 1; //just like gaussian

      dl2 = new DeepLearning(parms2).trainModel().get();
      
      Assert.assertEquals(12.93808 /*MSE*/,((ModelMetricsRegression)dl2._output._training_metrics)._mean_residual_deviance,0.7);
      Assert.assertEquals(12.93808 /*MSE*/,((ModelMetricsRegression)dl2._output._training_metrics)._MSE,0.7);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.deleteCrossValidationModels();
      if (dl2 != null) dl2.delete();  
    }
  }

  @Test
  public void testHuberDeltaTiny() {
    Frame tfr = null;
    DeepLearningModel dl = null, dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._distribution = huber;
      parms._huber_alpha = 1e-2;
      // more like Laplace, but different slope and different prefactor -> so can't compare deviance 1:1

      dl = new DeepLearning(parms).trainModel().get();

      double delta = 0.011996;
      // can compute huber loss from MAE since no obs weights
      Assert.assertEquals((2*2.31398/*MAE*/-delta)*delta,((ModelMetricsRegression)dl._output._training_metrics)._mean_residual_deviance,2e-2);
      Assert.assertEquals(19.856,((ModelMetricsRegression)dl._output._training_metrics)._MSE,1e-3);

      // the same for distribution = AUTO representing Huber:
      DeepLearningParameters parms2 = new DeepLearningParameters();
      parms2._train = tfr._key;
      parms2._response_column = tfr.lastVecName();
      parms2._reproducible = true;
      parms2._hidden = new int[]{20,20};
      parms2._seed = 0xdecaf;
      parms2._distribution = AUTO;
      parms2._loss = DeepLearningParameters.Loss.Huber;
      parms2._huber_alpha = 1e-2;

      dl2 = new DeepLearning(parms2).trainModel().get();
      
      Assert.assertEquals((2*2.31398/*MAE*/-delta)*delta,((ModelMetricsRegression)dl2._output._training_metrics)._mean_residual_deviance,2e-2);
      Assert.assertEquals(19.856,((ModelMetricsRegression)dl2._output._training_metrics)._MSE,1e-3);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.deleteCrossValidationModels();
      if (dl2 != null) dl2.delete();
    }
  }

  @Test
  public void testHuber() {
    Frame tfr = null;
    DeepLearningModel dl = null, dl2 = null;

    try {
      tfr = parseTestFile("./smalldata/gbm_test/BostonHousing.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._response_column = tfr.lastVecName();
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._distribution = huber;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(8.542106186915872,((ModelMetricsRegression)dl._output._training_metrics)._mean_residual_deviance,1e-5);

      // the same for distribution = AUTO representing Huber:
      DeepLearningParameters parms2 = new DeepLearningParameters();
      parms2._train = tfr._key;
      parms2._response_column = tfr.lastVecName();
      parms2._reproducible = true;
      parms2._hidden = new int[]{20,20};
      parms2._seed = 0xdecaf;
      parms2._distribution = AUTO;
      parms2._loss = DeepLearningParameters.Loss.Huber;

      dl2 = new DeepLearning(parms2).trainModel().get();

      Assert.assertEquals(8.542106186915872,((ModelMetricsRegression)dl2._output._training_metrics)._mean_residual_deviance,1e-5);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.deleteCrossValidationModels();
      if (dl2 != null) dl2.delete();
    }
  }

  @Test
  public void testCategoricalEncodingAUTO() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      tfr = parseTestFile("./smalldata/junit/titanic_alt.csv");
      Vec v = tfr.remove("survived");
      tfr.add("survived", v.toCategoricalVec());
      v.remove();
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._valid = tfr._key;
      parms._response_column = "survived";
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._nfolds = 3;
      parms._distribution = bernoulli;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(0.97329, ((ModelMetricsBinomial)dl._output._training_metrics)._auc._auc,1e-3);
      Assert.assertEquals(0.97329, ((ModelMetricsBinomial)dl._output._validation_metrics)._auc._auc,1e-3);
      Assert.assertEquals(0.93152, ((ModelMetricsBinomial)dl._output._cross_validation_metrics)._auc._auc,1e-3);

    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testCategoricalEncodingBinary() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      String response = "survived";
      tfr = parseTestFile("./smalldata/junit/titanic_alt.csv");
      if (tfr.vec(response).isBinary()) {
        Vec v = tfr.remove(response);
        tfr.add(response, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._valid = tfr._key;
      parms._response_column = response;
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._nfolds = 3;
      parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.Binary;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(0.94696  , ((ModelMetricsBinomial)dl._output._training_metrics)._auc._auc,1e-4);
      Assert.assertEquals(0.94696  , ((ModelMetricsBinomial)dl._output._validation_metrics)._auc._auc,1e-4);
      Assert.assertEquals(0.86556613, ((ModelMetricsBinomial)dl._output._cross_validation_metrics)._auc._auc,1e-4);

      int auc_row = Arrays.binarySearch(dl._output._cross_validation_metrics_summary.getRowHeaders(), "auc");
      Assert.assertEquals(0.86556613, (Float)dl._output._cross_validation_metrics_summary.get(auc_row,0), 1e-2);

    } finally {
      if (tfr != null) tfr.remove();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testCategoricalEncodingEigen() {
    Frame tfr = null;
    Frame vfr = null;
    DeepLearningModel dl = null;

    try {
      String response = "survived";
      tfr = parseTestFile("./smalldata/junit/titanic_alt.csv");
      vfr = parseTestFile("./smalldata/junit/titanic_alt.csv");
      if (tfr.vec(response).isBinary()) {
        Vec v = tfr.remove(response);
        tfr.add(response, v.toCategoricalVec());
        v.remove();
      }
      if (vfr.vec(response).isBinary()) {
        Vec v = vfr.remove(response);
        vfr.add(response, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(tfr);
      DKV.put(vfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._valid = vfr._key;
      parms._response_column = response;
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.Eigen;
      parms._score_training_samples = 0;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(
              ((ModelMetricsBinomial)dl._output._training_metrics)._logloss,
              ((ModelMetricsBinomial)dl._output._validation_metrics)._logloss,
              1e-8);
    } finally {
      if (tfr != null) tfr.remove();
      if (vfr != null) vfr.remove();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testCategoricalEncodingEigenCV() {
    Frame tfr = null;
    DeepLearningModel dl = null;

    try {
      String response = "survived";
      tfr = parseTestFile("./smalldata/junit/titanic_alt.csv");
      if (tfr.vec(response).isBinary()) {
        Vec v = tfr.remove(response);
        tfr.add(response, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._valid = tfr._key;
      parms._response_column = response;
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._nfolds = 3;
      parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.Eigen;
      parms._score_training_samples = 0;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(0.9521718170580964, ((ModelMetricsBinomial)dl._output._training_metrics)._auc._auc,1e-4);
      Assert.assertEquals(0.9521656365883807, ((ModelMetricsBinomial)dl._output._validation_metrics)._auc._auc,1e-4);
      Assert.assertEquals(0.9115080346106303, ((ModelMetricsBinomial)dl._output._cross_validation_metrics)._auc._auc,1e-4);

      int auc_row = Arrays.binarySearch(dl._output._cross_validation_metrics_summary.getRowHeaders(), "auc");
      Assert.assertEquals(0.913637, (Float)dl._output._cross_validation_metrics_summary.get(auc_row,0), 1e-4);

    } finally {
      if (tfr != null) tfr.remove();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
    }
  }

  @Test
  public void testCategoricalEncodingRegressionHuber() {
    Frame tfr = null;
    DeepLearningModel dl = null, dl2 = null;

    try {
      String response = "age";
      tfr = parseTestFile("./smalldata/junit/titanic_alt.csv");
      if (tfr.vec(response).isBinary()) {
        Vec v = tfr.remove(response);
        tfr.add(response, v.toCategoricalVec());
        v.remove();
      }
      DKV.put(tfr);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._valid = tfr._key;
      parms._response_column = response;
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      parms._nfolds = 3;
      parms._distribution = huber;
      parms._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.Binary;

      dl = new DeepLearning(parms).trainModel().get();

      Assert.assertEquals(122.15905123948743, ((ModelMetricsRegression)dl._output._training_metrics)._mean_residual_deviance,1e-4);
      Assert.assertEquals(122.15905123948743, ((ModelMetricsRegression)dl._output._validation_metrics)._mean_residual_deviance,1e-4);
      Assert.assertEquals(165.93781670012774, ((ModelMetricsRegression)dl._output._cross_validation_metrics)._mean_residual_deviance,1e-4);

      int mean_residual_deviance_row = Arrays.binarySearch(dl._output._cross_validation_metrics_summary.getRowHeaders(), "mean_residual_deviance");
      Assert.assertEquals(165.93781670012774, (Float)dl._output._cross_validation_metrics_summary.get(mean_residual_deviance_row,0), 1);

      // the same for distribution = AUTO representing Huber:
      DeepLearningParameters parms2 = new DeepLearningParameters();
      parms2._train = tfr._key;
      parms2._valid = tfr._key;
      parms2._response_column = response;
      parms2._reproducible = true;
      parms2._hidden = new int[]{20,20};
      parms2._seed = 0xdecaf;
      parms2._nfolds = 3;
      parms2._distribution = AUTO;
      parms2._loss = DeepLearningParameters.Loss.Huber;
      parms2._categorical_encoding = Model.Parameters.CategoricalEncodingScheme.Binary;

      dl2 = new DeepLearning(parms2).trainModel().get();

      Assert.assertEquals(122.15905123948743, ((ModelMetricsRegression)dl2._output._training_metrics)._mean_residual_deviance,1e-4);
      Assert.assertEquals(122.15905123948743, ((ModelMetricsRegression)dl2._output._validation_metrics)._mean_residual_deviance,1e-4);
      Assert.assertEquals(165.93781670012774, ((ModelMetricsRegression)dl2._output._cross_validation_metrics)._mean_residual_deviance,1e-4);

    } finally {
      if (tfr != null) tfr.remove();
      if (dl != null) dl.deleteCrossValidationModels();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.deleteCrossValidationModels();
      if (dl2 != null) dl2.delete();
    }
  }

  @Ignore
  @Test
  public void testMultinomialMNIST() {
    Frame train = null;
    Frame preds = null;
    Frame small = null, large = null;
    DeepLearningModel model = null;
    Scope.enter();
    try {
      File file = FileUtils.locateFile("bigdata/laptop/mnist/train.csv.gz");
      if (file != null) {
        NFSFileVec trainfv = NFSFileVec.make(file);
        train = ParseDataset.parse(Key.make(), trainfv._key);
        int ci = train.find("C785");
        Scope.track(train.replace(ci, train.vecs()[ci].toCategoricalVec()));
        DKV.put(train);

        DeepLearningParameters p = new DeepLearningParameters();
        p._train = train._key;
        p._response_column = "C785"; // last column is the response
        p._activation = DeepLearningParameters.Activation.RectifierWithDropout;
        p._hidden = new int[]{50,50};
        p._epochs = 1;
        p._adaptive_rate = false;
        p._rate = 0.005;
        p._sparse = true;
        model = new DeepLearning(p).trainModel().get();

        FrameSplitter fs = new FrameSplitter(train, new double[]{0.0001},new Key[]{Key.make("small"),Key.make("large")},null);
        fs.compute2();
        small = fs.getResult()[0];
        large = fs.getResult()[1];
        preds = model.score(small);
        preds.remove(0); //remove label, keep only probs
        Vec labels = small.vec("C785"); //actual
        String[] fullDomain = train.vec("C785").domain(); //actual

        ModelMetricsMultinomial mm = ModelMetricsMultinomial.make(preds, labels, fullDomain, MultinomialAucType.NONE);
        Log.info(mm.toString());
      }
    } catch(Throwable t) {
      t.printStackTrace();
      throw t;
    }
    finally {
      if (model!=null)  model.delete();
      if (preds!=null)  preds.remove();
      if (train!=null)  train.remove();
      if (small!=null)  small.delete();
      if (large!=null)  large.delete();
      Scope.exit();
    }
  }


  @Test
  public void testMultinomial() {
    Frame train = null;
    Frame preds = null;
    DeepLearningModel model = null;
    Scope.enter();
    try {
      train = parseTestFile("./smalldata/junit/titanic_alt.csv");
      Vec v = train.remove("pclass");
      train.add("pclass", v.toCategoricalVec());
      v.remove();
      DKV.put(train);

      DeepLearningParameters p = new DeepLearningParameters();
      p._train = train._key;
      p._response_column = "pclass"; // last column is the response
      p._activation = DeepLearningParameters.Activation.RectifierWithDropout;
      p._hidden = new int[]{50, 50};
      p._epochs = 1;
      p._adaptive_rate = false;
      p._rate = 0.005;
      p._sparse = true;
      model = new DeepLearning(p).trainModel().get();

      preds = model.score(train);
      preds.remove(0).remove(); //remove label, keep only probs
      Vec labels = train.vec("pclass"); //actual
      String[] fullDomain = train.vec("pclass").domain(); //actual

      ModelMetricsMultinomial mm = ModelMetricsMultinomial.make(preds, labels, fullDomain, MultinomialAucType.NONE);
      Log.info(mm.toString());
    } finally {
      if (model!=null)  model.delete();
      if (preds!=null)  preds.remove();
      if (train!=null)  train.remove();
      Scope.exit();
    }
  }

  @Test
  public void testBinomial() {
    Frame train = null;
    Frame preds = null;
    Frame small = null, large = null;
    DeepLearningModel model = null;
    Scope.enter();
    try {
      train = parseTestFile("./smalldata/junit/titanic_alt.csv");
      Vec v = train.remove("survived");
      train.add("survived", v.toCategoricalVec());
      v.remove();
      DKV.put(train);
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = train._key;
      parms._response_column = "survived";
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._seed = 0xdecaf;
      model = new DeepLearning(parms).trainModel().get();

      FrameSplitter fs = new FrameSplitter(train, new double[]{0.002},new Key[]{Key.make("small"),Key.make("large")},null);
      fs.compute2();
      small = fs.getResult()[0];
      large = fs.getResult()[1];
      preds = model.score(small);
      Vec labels = small.vec("survived"); //actual
      String[] fullDomain = train.vec("survived").domain(); //actual

      ModelMetricsBinomial mm = ModelMetricsBinomial.make(preds.vec(2), labels, fullDomain);
      Log.info(mm.toString());

      mm = ModelMetricsBinomial.make(preds.vec(2), labels, new String[]{"0","1"});
      Log.info(mm.toString());

      mm = ModelMetricsBinomial.make(preds.vec(2), labels);
      Log.info(mm.toString());

      try {
        mm = ModelMetricsBinomial.make(preds.vec(2), labels, new String[]{"a", "b"});
        Log.info(mm.toString());
        Assert.assertFalse(true);
      } catch (IllegalArgumentException ex) {
        ex.printStackTrace();
      }

    } catch(Throwable t) {
      t.printStackTrace();
      throw t;
    }
    finally {
      if (model!=null)  model.delete();
      if (preds!=null)  preds.remove();
      if (train!=null)  train.remove();
      if (small!=null)  small.delete();
      if (large!=null)  large.delete();
      Scope.exit();
    }
  }

  @Test
  public void testRegression() {
    Frame train = null;
    Frame preds = null;
    DeepLearningModel model = null;
    Scope.enter();
    try {
      train = parseTestFile("./smalldata/junit/titanic_alt.csv");
      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = train._key;
      parms._response_column = "age";
      parms._reproducible = true;
      parms._hidden = new int[]{20,20};
      parms._distribution = laplace;
      parms._seed = 0xdecaf;
      model = new DeepLearning(parms).trainModel().get();

      preds = model.score(train);
      Vec targets = train.vec("age"); //actual

      ModelMetricsRegression mm = ModelMetricsRegression.make(preds.vec(0), targets, parms._distribution);
      Log.info(mm.toString());

      mm = ModelMetricsRegression.make(preds.vec(0), targets, gaussian);
      Log.info(mm.toString());

      mm = ModelMetricsRegression.make(preds.vec(0), targets, poisson);
      Log.info(mm.toString());

    } catch(Throwable t) {
      t.printStackTrace();
      throw t;
    }
    finally {
      if (model!=null)  model.delete();
      if (preds!=null)  preds.remove();
      if (train!=null)  train.remove();
      Scope.exit();
    }
  }

  // NOTE: This test has nothing to do with Deep Learning, except that it uses the Deep Learning infrastructure to get access to the EigenVec computation logic
  @Test
  public void testEigenEncodingLogic() {
    int numNoncatColumns = 1;
    int[] catSizes       = {16};
    String[] catNames = {"sixteen"};
    Assert.assertEquals(catSizes.length, catNames.length);
    int totalExpectedColumns = numNoncatColumns + catSizes.length;
    double[] expectedMean = {0.0453}; //to test reproducibility

    Key<Frame> frameKey = Key.make();
    CreateFrame cf = new CreateFrame(frameKey);
    cf.rows = 100000;
    cf.cols = numNoncatColumns;
    cf.categorical_fraction = 0.0;
    cf.seed = 1234;
    cf.integer_fraction = 0.3;
    cf.binary_fraction = 0.1;
    cf.time_fraction = 0.2;
    cf.string_fraction = 0.1;
    Frame mainFrame = cf.execImpl().get();
    assert mainFrame != null : "Unable to create a frame";
    Frame[] auxFrames = new Frame[catSizes.length];
    Frame transformedFrame = null;
    try {
      for (int i = 0; i < catSizes.length; ++i) {
        CreateFrame ccf = new CreateFrame();
        ccf.rows = 100000;
        ccf.cols = 1;
        ccf.categorical_fraction = 1;
        ccf.integer_fraction = 0;
        ccf.binary_fraction = 0;
        ccf.seed = 1234;
        ccf.time_fraction = 0;
        ccf.string_fraction = 0;
        ccf.factors = catSizes[i];
        auxFrames[i] = ccf.execImpl().get();
        auxFrames[i]._names[0] = catNames[i];
        mainFrame.add(auxFrames[i]);
      }
      Log.info(mainFrame, 0, 100);

      FrameUtils.CategoricalEigenEncoder cbed =
              new FrameUtils.CategoricalEigenEncoder(new DeepLearning(new DeepLearningParameters()).getToEigenVec(), mainFrame, null);
      transformedFrame = cbed.exec().get();
      assert transformedFrame != null : "Unable to transform a frame";

      Assert.assertEquals("Wrong number of columns after converting to eigen encoding",
              totalExpectedColumns, transformedFrame.numCols());
      for (int i = 0; i < numNoncatColumns; ++i) {
        Assert.assertEquals(mainFrame.name(i), transformedFrame.name(i));
        Assert.assertEquals(mainFrame.types()[i], transformedFrame.types()[i]);
      }
      for (int i = numNoncatColumns; i < transformedFrame.numCols(); i++) {
          Assert.assertTrue("A categorical column should be transformed into one numeric one (col "+i+")",
                  transformedFrame.vec(i).isNumeric());
          Assert.assertEquals("Transformed categorical column should carry the name of the original column",
                  transformedFrame.name(i), mainFrame.name(i) + ".Eigen");
        Assert.assertEquals("Transformed categorical column should have the correct mean value",
                expectedMean[i-numNoncatColumns], transformedFrame.vec(i).mean(), 5e-4);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      throw e;
    } finally {
      mainFrame.delete();
      if (transformedFrame != null) transformedFrame.delete();
      for (Frame f : auxFrames)
        if (f != null)
          f.delete();
    }
  }

  // Check that the restarted model honors overwrite_with_best_model
  @Test
  public void testCheckpointOverwriteWithBestModel() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    Frame train = null, valid = null;
    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");

      FrameSplitter fs = new FrameSplitter(tfr, new double[]{0.8},new Key[]{Key.make("train"),Key.make("valid")},null);
      fs.compute2();
      train = fs.getResult()[0];
      valid = fs.getResult()[1];

      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = train._key;
      parms._valid = valid._key;
      parms._epochs = 1;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._hidden = new int[]{50,50};
      parms._seed = 0xdecaf;
      parms._train_samples_per_iteration = 0;
      parms._score_duty_cycle = 1;
      parms._score_interval = 0;
      parms._stopping_rounds = 0;
      parms._overwrite_with_best_model = true;
      DeepLearningParameters parms2 = (DeepLearningParameters)parms.clone();
      parms2._epochs = 10;

      dl = new DeepLearning(parms).trainModel().get();
      double ll1 = ((ModelMetricsMultinomial)dl._output._validation_metrics).logloss();

      parms2._checkpoint = dl._key;

      dl2 = new DeepLearning(parms2).trainModel().get();
      double ll2 = ((ModelMetricsMultinomial)dl2._output._validation_metrics).logloss();

      Assert.assertTrue(ll2 <= ll1);
    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
      if (train != null) train.delete();
      if (valid != null) valid.delete();
    }
  }

  // Check that the restarted model honors the previous model as a best model so far
  @Test
  public void testCheckpointOverwriteWithBestModel2() {
    Frame tfr = null;
    DeepLearningModel dl = null;
    DeepLearningModel dl2 = null;

    Frame train = null, valid = null;
    try {
      tfr = parseTestFile("./smalldata/iris/iris.csv");

      FrameSplitter fs = new FrameSplitter(tfr, new double[]{0.8},new Key[]{Key.make("train"),Key.make("valid")},null);
      fs.compute2();
      train = fs.getResult()[0];
      valid = fs.getResult()[1];

      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = train._key;
      parms._valid = valid._key;
      parms._epochs = 10;
      parms._response_column = "C5";
      parms._reproducible = true;
      parms._hidden = new int[]{50,50};
      parms._seed = 0xdecaf;
      parms._train_samples_per_iteration = 0;
      parms._score_duty_cycle = 1;
      parms._score_interval = 0;
      parms._stopping_rounds = 0;
      parms._overwrite_with_best_model = true;
      DeepLearningParameters parms2 = (DeepLearningParameters)parms.clone();
      parms2._epochs = 20;

      dl = new DeepLearning(parms).trainModel().get();
      double ll1 = ((ModelMetricsMultinomial)dl._output._validation_metrics).logloss();

      parms2._checkpoint = dl._key;

      dl2 = new DeepLearning(parms2).trainModel().get();
      double ll2 = ((ModelMetricsMultinomial)dl2._output._validation_metrics).logloss();

      Assert.assertTrue(ll2 <= ll1);
    } finally {
      if (tfr != null) tfr.delete();
      if (dl != null) dl.delete();
      if (dl2 != null) dl2.delete();
      if (train != null) train.delete();
      if (valid != null) valid.delete();
    }
  }

  @Test
  public void testMojoConcurrentScoring() throws Exception  { // PUBDEV-6615: DeepLearning MOJOs should be thread-safe
    try {
      Scope.enter();
      Frame tfr = Scope.track(parseTestFile("./smalldata/prostate/prostate.csv"));
      tfr.remove("ID").remove();
      tfr.add("AGE", tfr.remove("AGE")); // make AGE the last column (for convenience)
      DKV.put(tfr);

      DeepLearningParameters parms = new DeepLearningParameters();
      parms._train = tfr._key;
      parms._epochs = 3;
      parms._response_column = "AGE";
      parms._reproducible = true;
      parms._hidden = new int[]{50,50};
      parms._seed = 0xdecaf;

      DeepLearningModel model = new DeepLearning(parms).trainModel().get();
      Scope.track_generic(model);
      
      Frame preds = Scope.track(model.score(tfr));
      MojoModel m = model.toMojo();

      // 1. find rows that generate max prediction and min prediction
      double minPred = preds.vec(0).min();
      double maxPred = preds.vec(0).max();
      Vec.Reader vr = preds.vec(0).new Reader();
      long minPredIdx = -1;
      long maxPredIdx = -1;
      for (long i = 0; i < vr.length(); i++) {
        if (minPred == vr.at(i)) {
          minPredIdx = i;
        }
        if (maxPred == vr.at(i)) {
          maxPredIdx = i;
        }
      }
      double[] minRow = new double[tfr.numCols() - 1];
      double[] maxRow = new double[tfr.numCols() - 1];
      for (int i = 0; i < tfr.numCols() - 1; i++) {
        minRow[i] = tfr.vec(i).at(minPredIdx);
        maxRow[i] = tfr.vec(i).at(maxPredIdx);
      }
      
      // 2. sanity check - make sure MOJO scores on these rows correct
      assertEquals(minPred, m.score0(minRow.clone(), new double[1])[0], 0);
      assertEquals(maxPred, m.score0(maxRow.clone(), new double[1])[0], 0);

      // 3. Run 2 threads to predict on min-row and 2 threads to predict on max-row in parallel
      ExecutorService executor = Executors.newFixedThreadPool(4);
      List<Callable<Long>> runnables = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        runnables.add(new MojoRowScorer(m, minPred, minRow));
        runnables.add(new MojoRowScorer(m, maxPred, maxRow));
      }
      for (Future<Long> future : executor.invokeAll(runnables)) {
        assertNotEquals(0L, (long) future.get()); // we ran at least once
      }

    } finally {
      Scope.exit();
    }
  }

  private static class MojoRowScorer implements Callable<Long> {

    private final MojoModel _mojo;
    private final double _expected;
    private final double[] _input;

    private MojoRowScorer(MojoModel mojo, double expected, double[] input) {
      _mojo = mojo;
      _expected = expected;
      _input = input;
    }

    public Long call() {
      long cnt = 0;
      long start = System.currentTimeMillis();
      while (System.currentTimeMillis() < start + 1e4) {
        for (int i = 0; i < 100; i++) {
          double actual = _mojo.score0(_input.clone(), new double[1])[0];
          assertEquals(_expected, actual, 0);
          cnt++;
        }
      }
      return cnt;
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
    Frame dummyFrame = new Frame(
            new String[]{"a", "b", "c", "d", "e", "target"},
            new Vec[]{zeros, zeros, zeros, zeros, target, target}
    );
    dummyFrame._key = Key.make("DummyFrame_testIsFeatureUsedInPredict");

    Frame reference = null;
    Frame prediction = null;
    DeepLearningModel model = null;
    try {
      DKV.put(dummyFrame);
      DeepLearningModel.DeepLearningParameters dl = new DeepLearningModel.DeepLearningParameters();
      dl._train = dummyFrame._key;
      dl._response_column = "target";
      dl._seed = 1;
      dl._ignore_const_cols = ignoreConstCols;

      DeepLearning job = new DeepLearning(dl);
      model = job.trainModel().get();

      int usedFeatures = 0;
      for(String feature : model._output._names) {
        if (model.isFeatureUsedInPredict(feature)) {
          usedFeatures ++;
        }
      }
      // Unfortunately DeepLearning seems to use even the non-informative columns so this test just that
      // we didn't use more features than there are (e.g., wrong handling of categorical features, etc.)
      assertTrue(usedFeatures <= 5);
    } finally {
      dummyFrame.delete();
      if (model != null) model.delete();
      if (reference != null) reference.delete();
      if (prediction != null) prediction.delete();
      target.remove();
      zeros.remove();
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
                  Model.Parameters.CategoricalEncodingScheme.OneHotInternal,
                  Model.Parameters.CategoricalEncodingScheme.SortByResponse,
                  Model.Parameters.CategoricalEncodingScheme.Binary,
                  Model.Parameters.CategoricalEncodingScheme.LabelEncoder,
                  Model.Parameters.CategoricalEncodingScheme.Eigen
          };
    
          for (Model.Parameters.CategoricalEncodingScheme scheme : supportedSchemes) {
    
              DeepLearningModel.DeepLearningParameters parms = new DeepLearningModel.DeepLearningParameters();
              parms._train = fr._key;
              parms._response_column = response;
              parms._categorical_encoding = scheme;
    
              DeepLearning job = new DeepLearning(parms);
              DeepLearningModel dl = job.trainModel().get();
              Scope.track_generic(dl);
    
              // Done building model; produce a score column with predictions
              Frame scored = Scope.track(dl.score(fr));
    
              // Build a POJO & MOJO, validate same results
              Assert.assertTrue(dl.testJavaScoring(fr, scored, 1e-15));
    
              File pojoScoringOutput = tmp.newFile(dl._key + "_scored.csv");
    
              String modelName = JCodeGen.toJavaId(dl._key.toString());
              String pojoSource = dl.toJava(false, true);
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
              assertFrameEquals(scored, scoredWithPojo, 1e-7);

              File mojoScoringOutput = tmp.newFile(dl._key + "_scored2.csv");
              MojoModel mojoModel = dl.toMojo();

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
}

