package hex.glrm;

import hex.DataInfo;
import hex.ModelMetrics;
import hex.glrm.GLRMModel.GLRMParameters;
import org.junit.Assert;
import org.junit.BeforeClass;
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

  public static void checkLossbyCol(GLRMParameters parms, GLRMModel model) {
    int ncats = model._output._ncats;
    GLRMParameters.Loss[] actual = model._output._lossFunc;
    assert ncats >= 0 && ncats <= actual.length;
    if(null == parms._loss_by_col || null == parms._loss_by_col_idx) return;
    Assert.assertEquals(parms._loss_by_col.length, parms._loss_by_col_idx.length);

    // Map original to adapted frame column indices
    int[] loss_idx_adapt = new int[parms._loss_by_col_idx.length];
    for(int i = 0; i < parms._loss_by_col_idx.length; i++) {
      int idx_adapt = -1;
      for(int j = 0; j < model._output._permutation.length; j++) {
        if(model._output._permutation[j] == parms._loss_by_col_idx[i]) {
          idx_adapt = j; break;
        }
      }
      loss_idx_adapt[i] = idx_adapt;
    }
    Arrays.sort(loss_idx_adapt);

    // Check loss function for each column matches input parameter
    // Categorical columns
    for(int i = 0; i < ncats; i++) {
      int idx = Arrays.binarySearch(loss_idx_adapt, i);
      GLRMParameters.Loss comp = idx >= 0 ? parms._loss_by_col[idx] : parms._multi_loss;
      Assert.assertEquals(comp, actual[i]);
    }

    // Numeric columns
    for(int i = ncats; i < actual.length; i++) {
      int idx = Arrays.binarySearch(loss_idx_adapt, i);
      GLRMParameters.Loss comp = idx >= 0 ? parms._loss_by_col[idx] : parms._loss;
      Assert.assertEquals(comp, actual[i]);
    }
  }

  @Test public void testArrests() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of standardized training frame
    Frame yinit = ArrayUtils.frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
                                      ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
                                      ard(0.07163341, 1.4788032, 0.9989801, 1.042878388)));
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;
    long seed = 1234;

    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._gamma_x = parms._gamma_y = 0.5;
      parms._regularization_x = GLRMParameters.Regularizer.Quadratic;
      parms._regularization_y = GLRMParameters.Regularizer.Quadratic;
      parms._k = 3;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.User;
      parms._recover_svd = false;
      parms._user_y = yinit._key;
      parms._seed = seed;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM) ModelMetrics.getFromDKV(model, train);
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
      if (model != null) model.delete();
    }
  }

  @Test public void testBenignSVD() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;

    try {
      train = parse_test_file(Key.make("benign.hex"), "smalldata/logreg/benign.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 10;
      parms._gamma_x = parms._gamma_y = 0.25;
      parms._regularization_x = GLRMParameters.Regularizer.Quadratic;
      parms._regularization_y = GLRMParameters.Regularizer.Quadratic;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.SVD;
      parms._min_step_size = 1e-5;
      parms._recover_svd = false;
      parms._max_iterations = 2000;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
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
      if (model != null) model.delete();
    }
  }

  @Test public void testArrestsSVD() throws InterruptedException, ExecutionException {
    // Initialize using first k rows of standardized training frame
    Frame yinit = ArrayUtils.frame(ard(ard(1.24256408, 0.7828393, -0.5209066, -0.003416473),
                                      ard(0.50786248, 1.1068225, -1.2117642, 2.484202941),
                                      ard(0.07163341, 1.4788032, 0.9989801, 1.042878388),
                                      ard(0.23234938, 0.2308680, -1.0735927, -0.184916602)));
    double[] sval = new double[] {11.024148, 6.964086, 4.179904, 2.915146};
    double[][] eigvec = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
                            ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
                            ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
                            ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    GLRMModel model = null;
    Frame train = null;
    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      // parms._init = GLRM.Initialization.PlusPlus;
      parms._init = GLRM.Initialization.User;
      parms._user_y = yinit._key;
      parms._max_iterations = 1000;
      parms._min_step_size = 1e-8;
      parms._recover_svd = true;

      GLRM job = new GLRM(parms);
      try {
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        // checkStddev(sval, model._output._singular_vals, 1e-4);
        // checkEigvec(eigvec, model._output._eigenvectors_raw, 1e-4);
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
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
      if (model != null) model.delete();
    }
  }

  @Test public void testArrestsPlusPlus() throws InterruptedException, ExecutionException {
    GLRMModel model = null;
    Frame train = null;
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
      parms._max_iterations = 100;
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
      if (model != null) model.delete();
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
    Frame train = null;
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
        parms._loss = GLRMParameters.Loss.Quadratic;
        parms._regularization_x = GLRMParameters.Regularizer.None;
        parms._regularization_y = GLRMParameters.Regularizer.None;
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
          double ev_err = errEigvec(eigvec, model._output._eigenvectors_raw) / parms._k;
          Log.info("Avg SSE in Std Dev = " + sd_err + "\tAvg SSE in Eigenvectors = " + ev_err);
          sd_map.put(missing_fraction, sd_err);
          ev_map.put(missing_fraction, ev_err);

          model.score(train).delete();
          ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
          Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
          Assert.assertEquals(model._output._objective, mm._numerr, TOLERANCE);
        } catch (Throwable t) {
          t.printStackTrace();
          throw new RuntimeException(t);
        } finally {
          job.remove();
        }
        Scope.exit();
      } catch(Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        if (train != null) train.delete();
        if (model != null) model.delete();
      }
    }
    sb.append("\nMissing Fraction --> Avg SSE in Std Dev\n");
    for (String s : Arrays.toString(sd_map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
    sb.append("\n");
    sb.append("Missing Fraction --> Avg SSE in Eigenvectors\n");
    for (String s : Arrays.toString(ev_map.entrySet().toArray()).split(",")) sb.append(s.replace("=", " --> ")).append("\n");
    Log.info(sb.toString());
  }

  @Test public void testSetColumnLoss() throws InterruptedException, ExecutionException {
    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;

    try {
      train = parse_test_file(Key.make("benign.hex"), "smalldata/logreg/benign.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 12;
      parms._loss = GLRMParameters.Loss.Quadratic;
      parms._loss_by_col = new GLRMParameters.Loss[] { GLRMParameters.Loss.Absolute, GLRMParameters.Loss.Huber };
      parms._loss_by_col_idx = new int[] { 2 /* AGMT */, 5 /* DEG */ };
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GLRM.Initialization.PlusPlus;
      parms._min_step_size = 1e-5;
      parms._recover_svd = false;
      parms._max_iterations = 2000;

      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        checkLossbyCol(parms, model);

        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
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
      if (model != null) model.delete();
    }
  }

  @Test public void testRegularizers() throws InterruptedException, ExecutionException {
    // Initialize using first 4 rows of USArrests
    Frame init = ArrayUtils.frame(ard(ard(13.2, 236, 58, 21.2),
                                      ard(10.0, 263, 48, 44.5),
                                      ard(8.1, 294, 80, 31.0),
                                      ard(8.8, 190, 50, 19.5)));

    GLRM job = null;
    GLRMModel model = null;
    Frame train = null;
    long seed = 1234;

    try {
      train = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._init = GLRM.Initialization.User;
      parms._user_y = init._key;
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
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (model != null) model.delete();
      }

      Log.info("\nOrthogonal non-negative matrix factorization");
      parms._gamma_x = parms._gamma_y = 1;
      parms._regularization_x = GLRMParameters.Regularizer.OneSparse;
      parms._regularization_y = GLRMParameters.Regularizer.NonNegative;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (model != null) model.delete();
      }

      Log.info("\nQuadratic clustering (k-means)");
      parms._gamma_x = 1; parms._gamma_y = 0;
      parms._regularization_x = GLRMParameters.Regularizer.UnitOneSparse;
      parms._regularization_y = GLRMParameters.Regularizer.None;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (model != null) model.delete();
      }

      Log.info("\nQuadratic mixture (soft k-means)");
      parms._gamma_x = 1; parms._gamma_y = 0;
      parms._regularization_x = GLRMParameters.Regularizer.UnitOneSparse;
      parms._regularization_y = GLRMParameters.Regularizer.None;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        job.remove();
        if (model != null) model.delete();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      init.delete();
      if (train != null) train.delete();
    }
  }
}
