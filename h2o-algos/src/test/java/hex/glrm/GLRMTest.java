package hex.glrm;

import hex.DataInfo;
import hex.glrm.GLRMModel.GLRMParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.FrameUtils;
import water.util.Log;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

public class GLRMTest extends TestUtil {
  public final double TOLERANCE = 1e-6;
  @BeforeClass public static void setup() { stall_till_cloudsize(1); }

  private static String colFormat(String[] cols, String format) {
    int[] idx = new int[cols.length];
    for(int i = 0; i < idx.length; i++) idx[i] = i;
    return colFormat(cols, format, idx);
  }
  private static String colFormat(String[] cols, String format, int[] idx) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0; i < cols.length; i++) sb.append(String.format(format, cols[idx[i]]));
    sb.append("\n");
    return sb.toString();
  }

  private static String colExpFormat(String[] cols, String[][] domains, String format) {
    int[] idx = new int[cols.length];
    for(int i = 0; i < idx.length; i++) idx[i] = i;
    return colExpFormat(cols, domains, format, idx);
  }

  private static String colExpFormat(String[] cols, String[][] domains, String format, int[] idx) {
    StringBuilder sb = new StringBuilder();
    for(int i = 0; i < domains.length; i++) {
      int c = idx[i];
      if(domains[c] == null)
        sb.append(String.format(format, cols[c]));
      else {
        for(int j = 0; j < domains[c].length; j++)
          sb.append(String.format(format, domains[c][j]));
      }
    }
    sb.append("\n");
    return sb.toString();
  }

  public double errStddev(double[] expected, double[] actual) {
    double err = 0;
    for(int i = 0; i < actual.length; i++) {
      double diff = expected[i] - actual[i];
      err += diff * diff;
    }
    return err;
  }

  public double errEigvec(double[][] expected, double[][] actual) { return errEigvec(expected, actual, TOLERANCE); }
  public double errEigvec(double[][] expected, double[][] actual, double threshold) {
    double err = 0;
    for(int j = 0; j < actual[0].length; j++) {
      boolean flipped = Math.abs(expected[0][j] - actual[0][j]) > threshold;
      for(int i = 0; i < actual.length; i++) {
        double diff = expected[i][j] - (flipped ? -actual[i][j] : actual[i][j]);
        err += diff * diff;
      }
    }
    return err;
  }

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of standardized training frame
    Frame yinit = frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
            ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
            ard(0.07163341, 1.4788032, 0.9989801, 1.042878388)));
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null, score = null;
    long seed = 1234;

    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._gamma_x = parms._gamma_y = 0.5;
      parms._regularization_x = GLRMParameters.Regularizer.L2;
      parms._regularization_y = GLRMParameters.Regularizer.L2;
      parms._k = 3;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.User;
      parms._recover_svd = false;
      parms._user_points = yinit._key;
      parms._seed = seed;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      yinit.delete();
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testBenignSVD() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null, score = null;

    try {
      train = parse_test_file(Key.make("benign.hex"), "smalldata/logreg/benign.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 10;
      parms._gamma_x = parms._gamma_y = 0.25;
      parms._regularization_x = GLRMParameters.Regularizer.L2;
      parms._regularization_y = GLRMParameters.Regularizer.L2;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.SVD;
      parms._min_step_size = 1e-5;
      parms._recover_svd = false;
      parms._max_iterations = 2000;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testArrestsSVD() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of standardized training frame
    Frame yinit = frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
            ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
            ard(0.07163341, 1.4788032, 0.9989801, 1.042878388),
            ard(0.23234938, 0.2308680, -1.0735927, -0.184916602)));
    double[] sval = new double[] {11.024148, 6.964086, 4.179904, 2.915146};
    double[][] eigvec = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
            ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
            ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
            ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    GLRMModel model = null;
    Frame train = null, score = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      // parms._init = GLRM.Initialization.PlusPlus;
      parms._init = GLRM.Initialization.User;
      parms._user_points = yinit._key;
      parms._max_iterations = 1000;
      parms._min_step_size = 1e-8;
      parms._recover_svd = true;

      GLRM job = new GLRM(parms);
      try {
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        // checkStddev(sval, model._output._singular_vals, 1e-4);
        // checkEigvec(eigvec, model._output._eigenvectors, 1e-4);

        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
        Assert.assertEquals(model._output._objective, mm._numerr, TOLERANCE);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      yinit.delete();
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testArrestsPlusPlus() throws InterruptedException, ExecutionException {
    GLRMModel model = null;
    Frame train = null, score = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._loss = GLRMParameters.Loss.Logistic;
      parms._regularization_x = GLRMParameters.Regularizer.NonNegative;
      parms._regularization_y = GLRMParameters.Regularizer.NonNegative;
      parms._gamma_x = parms._gamma_y = 1;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.PlusPlus;
      parms._max_iterations = 1000;
      parms._min_step_size = 1e-8;
      parms._recover_svd = true;

      GLRM job = new GLRM(parms);
      try {
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testArrestsMissing() throws InterruptedException, ExecutionException {
    // Expected eigenvectors and their corresponding singular values with standardized data
    double[] sval = new double[] {11.024148, 6.964086, 4.179904, 2.915146};
    double[][] eigvec = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
            ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
            ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
            ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    long seed = 1234;
    Frame train = null, score = null;
    GLRMModel model = null;
    GLRMParameters parms;

    Map<Double,Double> sd_map = new TreeMap<>();
    Map<Double,Double> ev_map = new TreeMap<>();
    StringBuilder sb = new StringBuilder();

    for (double missing_fraction : new double[]{0, 0.1, 0.25, 0.5, 0.75, 0.9}) {
      try {
        Scope.enter();
        train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");

        // Add missing values to the training data
        if (missing_fraction > 0) {
          Frame frtmp = new Frame(Key.make(), train.names(), train.vecs());
          DKV.put(frtmp._key, frtmp); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
          FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frtmp._key, seed, missing_fraction);
          j.execImpl();
          j.get(); // MissingInserter is non-blocking, must block here explicitly
          DKV.remove(frtmp._key); // Delete the frame header (not the data)
        }

        parms = new GLRMParameters();
        parms._train = train._key;
        parms._k = train.numCols();
        parms._regularization_x = GLRMParameters.Regularizer.L2;
        parms._regularization_y = GLRMParameters.Regularizer.L2;
        parms._transform = DataInfo.TransformType.STANDARDIZE;
        parms._init = GLRM.Initialization.PlusPlus;
        parms._max_iterations = 1000;
        parms._seed = seed;
        parms._recover_svd = true;

        GLRM job = new GLRM(parms);
        try {
          model = job.trainModel().get();
          Log.info(100 * missing_fraction + "% missing values: Objective = " + model._output._objective);
          double sd_err = errStddev(sval, model._output._singular_vals) / parms._k;
          double ev_err = errEigvec(eigvec, model._output._eigenvectors) / parms._k;
          Log.info("Avg SSE in Std Dev = " + sd_err + "\tAvg SSE in Eigenvectors = " + ev_err);
          sd_map.put(missing_fraction, sd_err);
          ev_map.put(missing_fraction, ev_err);

          score = model.score(train);
          ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
          Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          job.remove();
          if (score != null) score.delete();
        }
        Scope.exit();
      } catch(Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (train != null) train.delete();
        if (model != null) {
          model._parms._loading_key.get().delete();
          model.delete();
        }
      }
    }
    sb.append("\nMissing Fraction --> Avg SSE in Std Dev\n");
    for (String s : Arrays.toString(sd_map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
    sb.append("\n");
    sb.append("Missing Fraction --> Avg SSE in Eigenvectors\n");
    for (String s : Arrays.toString(ev_map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
    Log.info(sb.toString());
  }

  @Test public void testRegularizers() throws InterruptedException, ExecutionException {
    // Initialize using first 4 rows of USArrests
    Frame init = frame(ard(ard(13.2, 236, 58, 21.2),
            ard(10.0, 263, 48, 44.5),
            ard( 8.1, 294, 80, 31.0),
            ard( 8.8, 190, 50, 19.5)));

    GLRM job = null;
    GLRMModel model = null;
    Frame train = null, score = null;
    long seed = 1234;

    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._init = GLRM.Initialization.User;
      parms._user_points = init._key;
      parms._transform = DataInfo.TransformType.NONE;
      parms._recover_svd = false;
      parms._max_iterations = 1000;
      parms._seed = seed;

      Log.info("\nNon-negative matrix factorization");
      parms._gamma_x = parms._gamma_y = 1;
      parms._regularization_x = GLRMParameters.Regularizer.NonNegative;
      parms._regularization_y = GLRMParameters.Regularizer.NonNegative;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes (Y'):\n" + ArrayUtils.pprint(model._output._archetypes));
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (score != null) score.delete();
        if (model != null) {
          model._parms._loading_key.get().delete();
          model.delete();
        }
      }

      Log.info("\nOrthogonal non-negative matrix factorization");
      parms._gamma_x = parms._gamma_y = 1;
      parms._regularization_x = GLRMParameters.Regularizer.OneSparse;
      parms._regularization_y = GLRMParameters.Regularizer.NonNegative;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes (Y'):\n" + ArrayUtils.pprint(model._output._archetypes));
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (score != null) score.delete();
        if (model != null) {
          model._parms._loading_key.get().delete();
          model.delete();
        }
      }

      Log.info("\nQuadratic clustering (k-means)");
      parms._gamma_x = 1; parms._gamma_y = 0;
      parms._regularization_x = GLRMParameters.Regularizer.UnitOneSparse;
      parms._regularization_y = GLRMParameters.Regularizer.None;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes (Y'):\n" + ArrayUtils.pprint(model._output._archetypes));
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (score != null) score.delete();
        if (model != null) {
          model._parms._loading_key.get().delete();
          model.delete();
        }
      }

      Log.info("\nQuadratic mixture (soft k-means)");
      parms._gamma_x = 1; parms._gamma_y = 0;
      parms._regularization_x = GLRMParameters.Regularizer.UnitOneSparse;
      parms._regularization_y = GLRMParameters.Regularizer.None;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes (Y'):\n" + ArrayUtils.pprint(model._output._archetypes));
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (score != null) score.delete();
        if (model != null) {
          model._parms._loading_key.get().delete();
          model.delete();
        }
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      init.delete();
      if (train != null) train.delete();
    }
  }

  @Test public void testCategoricalIris() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null, score = null;

    try {
      train = parse_test_file(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._loss = GLRMParameters.Loss.L1;
      parms._init = GLRM.Initialization.SVD;
      parms._transform = DataInfo.TransformType.NONE;
      parms._recover_svd = true;
      parms._max_iterations = 1000;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
    }
  }

  @Test public void testCategoricalProstate() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null, score = null;
    final int[] cats = new int[]{1,3,4,5};    // Categoricals: CAPSULE, RACE, DPROS, DCAPS

    try {
      Scope.enter();
      train = parse_test_file(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
      for(int i = 0; i < cats.length; i++)
        Scope.track(train.replace(cats[i], train.vec(cats[i]).toEnum())._key);
      train.remove("ID").remove();
      DKV.put(train._key, train);

      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 8;
      parms._gamma_x = parms._gamma_y = 0.1;
      parms._regularization_x = GLRMParameters.Regularizer.L2;
      parms._regularization_y = GLRMParameters.Regularizer.L2;
      parms._init = GLRM.Initialization.PlusPlus;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._recover_svd = false;
      parms._max_iterations = 200;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        score = model.score(train);
        ModelMetricsGLRM mm = DKV.getGet(model._output._model_metrics[model._output._model_metrics.length - 1]);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (score != null) score.delete();
      if (model != null) {
        model._parms._loading_key.get().delete();
        model.delete();
      }
      Scope.exit();
    }
  }

  @Test public void testExpandCatsIris() throws InterruptedException, ExecutionException {
    double[][] iris = ard(ard(6.3, 2.5, 4.9, 1.5, 1),
                          ard(5.7, 2.8, 4.5, 1.3, 1),
                          ard(5.6, 2.8, 4.9, 2.0, 2),
                          ard(5.0, 3.4, 1.6, 0.4, 0),
                          ard(6.0, 2.2, 5.0, 1.5, 2));
    double[][] iris_expandR = ard(ard(0, 1, 0, 6.3, 2.5, 4.9, 1.5),
                                  ard(0, 1, 0, 5.7, 2.8, 4.5, 1.3),
                                  ard(0, 0, 1, 5.6, 2.8, 4.9, 2.0),
                                  ard(1, 0, 0, 5.0, 3.4, 1.6, 0.4),
                                  ard(0, 0, 1, 6.0, 2.2, 5.0, 1.5));
    String[] iris_cols = new String[] {"sepal_len", "sepal_wid", "petal_len", "petal_wid", "class"};
    String[][] iris_domains = new String[][] { null, null, null, null, new String[] {"setosa", "versicolor", "virginica"} };

    Frame fr = null;
    try {
      fr = parse_test_file(Key.make("iris.hex"), "smalldata/iris/iris_wheader.csv");
      DataInfo dinfo = new DataInfo(Key.make(), fr, null, 0, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, false, false, /* weights */ false, /* offset */ false, /* fold */ false);

      Log.info("Original matrix:\n" + colFormat(iris_cols, "%8.7s") + ArrayUtils.pprint(iris));
      double[][] iris_perm = ArrayUtils.permuteCols(iris, dinfo._permutation);
      Log.info("Permuted matrix:\n" + colFormat(iris_cols, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(iris_perm));

      double[][] iris_exp = GLRM.expandCats(iris_perm, dinfo);
      Log.info("Expanded matrix:\n" + colExpFormat(iris_cols, iris_domains, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(iris_exp));
      Assert.assertArrayEquals(iris_expandR, iris_exp);
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (fr != null) fr.delete();
    }
  }

  @Test public void testExpandCatsProstate() throws InterruptedException, ExecutionException {
    double[][] prostate = ard(ard(0, 71, 1, 0, 0,  4.8, 14.0, 7),
                              ard(1, 70, 1, 1, 0,  8.4, 21.8, 5),
                              ard(0, 73, 1, 3, 0, 10.0, 27.4, 6),
                              ard(1, 68, 1, 0, 0,  6.7, 16.7, 6));
    double[][] pros_expandR = ard(ard(1, 0, 0, 0, 0, 1, 0, 1, 0, 1, 0, 71,  4.8, 14.0, 7),
                                  ard(0, 1, 0, 0, 0, 1, 0, 0, 1, 1, 0, 70,  8.4, 21.8, 5),
                                  ard(0, 0, 0, 1, 0, 1, 0, 1, 0, 1, 0, 73, 10.0, 27.4, 6),
                                  ard(1, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 68,  6.7, 16.7, 6));
    String[] pros_cols = new String[]{"Capsule", "Age", "Race", "Dpros", "Dcaps", "PSA", "Vol", "Gleason"};
    String[][] pros_domains = new String[][]{new String[]{"No", "Yes"}, null, new String[]{"Other", "White", "Black"},
            new String[]{"None", "UniLeft", "UniRight", "Bilobar"}, new String[]{"No", "Yes"}, null, null, null};
    final int[] cats = new int[]{1,3,4,5};    // Categoricals: CAPSULE, RACE, DPROS, DCAPS

    Frame fr = null;
    try {
      Scope.enter();
      fr = parse_test_file(Key.make("prostate.hex"), "smalldata/logreg/prostate.csv");
      for(int i = 0; i < cats.length; i++)
        Scope.track(fr.replace(cats[i], fr.vec(cats[i]).toEnum())._key);
      fr.remove("ID").remove();
      DKV.put(fr._key, fr);
      DataInfo dinfo = new DataInfo(Key.make(), fr, null, 0, true, DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, false, false, false, /* weights */ false, /* offset */ false, /* fold */ false);

      Log.info("Original matrix:\n" + colFormat(pros_cols, "%8.7s") + ArrayUtils.pprint(prostate));
      double[][] pros_perm = ArrayUtils.permuteCols(prostate, dinfo._permutation);
      Log.info("Permuted matrix:\n" + colFormat(pros_cols, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(pros_perm));

      double[][] pros_exp = GLRM.expandCats(pros_perm, dinfo);
      Log.info("Expanded matrix:\n" + colExpFormat(pros_cols, pros_domains, "%8.7s", dinfo._permutation) + ArrayUtils.pprint(pros_exp));
      Assert.assertArrayEquals(pros_expandR, pros_exp);
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (fr != null) fr.delete();
      Scope.exit();
    }
  }
}
