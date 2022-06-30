package hex.glrm;

import hex.*;
import hex.genmodel.algos.glrm.GlrmInitialization;
import hex.genmodel.algos.glrm.GlrmLoss;
import hex.genmodel.algos.glrm.GlrmRegularizer;
import hex.glrm.GLRMModel.GLRMParameters;
import hex.pca.PCA;
import hex.pca.PCAModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;
import water.fvec.UploadFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.ArrayUtils;
import water.util.FileUtils;
import water.util.FrameUtils;
import water.util.Log;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;
import static org.junit.Assert.assertTrue;

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
    GlrmLoss[] actual = model._output._lossFunc;
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
      GlrmLoss comp = idx >= 0 ? parms._loss_by_col[idx] : parms._multi_loss;
      Assert.assertEquals(comp, actual[i]);
    }

    // Numeric columns
    for(int i = ncats; i < actual.length; i++) {
      int idx = Arrays.binarySearch(loss_idx_adapt, i);
      GlrmLoss comp = idx >= 0 ? parms._loss_by_col[idx] : parms._loss;
      Assert.assertEquals(comp, actual[i]);
    }
  }

  @Ignore
  @Test public void testSubset() throws InterruptedException, ExecutionException {
    //Analogous to pyunit_subset_glrm.py
    GLRM job = null;
    GLRMModel model = null;
    Frame train;
    InputStream is;
    try {
      is = new FileInputStream(FileUtils.getFile("bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip"));
      UploadFileVec.ReadPutStats stats = new UploadFileVec.ReadPutStats();
      UploadFileVec.readPut("train",is,stats);
    } catch (Exception e) {
      e.printStackTrace();
    }
    ParseDataset.parse(Key.make("train_parsed"),Key.make("train"));
    train = DKV.getGet("train_parsed");
    try {
      Log.info("num chunks: ", train.anyVec().nChunks());
      Vec[] acs_zcta_vec = {train.vec(0).toCategoricalVec()};
      Frame acs_zcta_fr = new Frame(Key.<Frame>make("acs_zcta_fr"),new String[] {"name"}, acs_zcta_vec);
      DKV.put(acs_zcta_fr);
      train.remove(0).remove();
      DKV.put(train);
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._gamma_x = 0.25;
      parms._gamma_y = 0.5;
      parms._regularization_x = GlrmRegularizer.Quadratic;
      parms._regularization_y = GlrmRegularizer.L1;
      parms._k = 10;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._max_iterations = 1;
      parms._loss = GlrmLoss.Quadratic;
      try {
        Scope.enter();
        job = new GLRM(parms);
        model = job.trainModel().get();
        String s = "(tmp= py_4 (rows (cols_py " + model._output._representation_key + " [0 1]) (tmp= py_3 (| (| (| (| (| (== (tmp= py_2 " + acs_zcta_fr._key + ") \"10065\") (== py_2 \"11219\")) (== py_2 \"66753\")) (== py_2 \"84104\")) (== py_2 \"94086\")) (== py_2 \"95014\")))))";
        Val val = Rapids.exec(s);
      } catch (Throwable t) {
        t.printStackTrace();
        throw new RuntimeException(t);
      } finally {
        acs_zcta_fr.delete();
        Scope.exit();
      }
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    } finally {
      if (train != null) train.delete();
      if (model != null) model.delete();
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
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._gamma_x = parms._gamma_y = 0.5;
      parms._regularization_x = GlrmRegularizer.Quadratic;
      parms._regularization_y = GlrmRegularizer.Quadratic;
      parms._k = 3;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GlrmInitialization.User;
      parms._recover_svd = false;
      parms._user_y = yinit._key;
      parms._seed = seed;

      job = new GLRM(parms);
      model = job.trainModel().get();
      Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      model.score(train).delete();
      ModelMetricsGLRM mm = (ModelMetricsGLRM) ModelMetrics.getFromDKV(model, train);
      Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
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
      train = parseTestFile(Key.make("benign.hex"), "smalldata/logreg/benign.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 10;
      parms._gamma_x = parms._gamma_y = 0.25;
      parms._regularization_x = GlrmRegularizer.Quadratic;
      parms._regularization_y = GlrmRegularizer.Quadratic;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GlrmInitialization.SVD;
      parms._min_step_size = 1e-5;
      parms._recover_svd = false;
      parms._max_iterations = 2000;

      job = new GLRM(parms);
      model = job.trainModel().get();
      Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      model.score(train).delete();
      ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
      Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
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
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      // parms._init = GLRM.Initialization.PlusPlus;
      parms._init = GlrmInitialization.User;
      parms._user_y = yinit._key;
      parms._max_iterations = 1000;
      parms._min_step_size = 1e-8;
      parms._recover_svd = true;

      GLRM job = new GLRM(parms);
      model = job.trainModel().get();
      Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      // checkStddev(sval, model._output._singular_vals, 1e-4);
      // checkEigvec(eigvec, model._output._eigenvectors_raw, 1e-4);
      model.score(train).delete();
      ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
      Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      Assert.assertEquals(model._output._objective, mm._numerr, TOLERANCE);
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
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._loss = GlrmLoss.Huber;
      parms._regularization_x = GlrmRegularizer.NonNegative;
      parms._regularization_y = GlrmRegularizer.NonNegative;
      parms._gamma_x = parms._gamma_y = 1;
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GlrmInitialization.PlusPlus;
      parms._max_iterations = 100;
      parms._min_step_size = 1e-8;
      parms._recover_svd = true;

      GLRM job = new GLRM(parms);
      model = job.trainModel().get();
      Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
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
        train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");

        // Add missing values to the training data
        if (missing_fraction > 0) {
          Frame frtmp = new Frame(Key.<Frame>make(), train.names(), train.vecs());
          DKV.put(frtmp._key, frtmp); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
          FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frtmp._key, seed, missing_fraction);
          j.execImpl().get(); // MissingInserter is non-blocking, must block here explicitly
          DKV.remove(frtmp._key); // Delete the frame header (not the data)
        }

        parms = new GLRMParameters();
        parms._train = train._key;
        parms._k = train.numCols();
        parms._loss = GlrmLoss.Quadratic;
        parms._regularization_x = GlrmRegularizer.None;
        parms._regularization_y = GlrmRegularizer.None;
        parms._transform = DataInfo.TransformType.STANDARDIZE;
        parms._init = GlrmInitialization.PlusPlus;
        parms._max_iterations = 1000;
        parms._seed = seed;
        parms._recover_svd = true;

        GLRM job = new GLRM(parms);
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
        Scope.exit();
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
      train = parseTestFile(Key.make("benign.hex"), "smalldata/logreg/benign.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 12;
      parms._loss = GlrmLoss.Quadratic;
      parms._loss_by_col = new GlrmLoss[] { GlrmLoss.Absolute, GlrmLoss.Huber };
      parms._loss_by_col_idx = new int[] { 2 /* AGMT */, 5 /* DEG */ };
      parms._transform = DataInfo.TransformType.STANDARDIZE;
      parms._init = GlrmInitialization.PlusPlus;
      parms._min_step_size = 1e-5;
      parms._recover_svd = false;
      parms._max_iterations = 2000;

      job = new GLRM(parms);
      model = job.trainModel().get();
      Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
      checkLossbyCol(parms, model);

      model.score(train).delete();
      ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
      Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
    } finally {
      if (train != null) train.delete();
      if (model != null) model.delete();
    }
  }

  // Need to test when we are calling predict with the training dataset and calling predict with a new
  // dataset, the operations here will be differ                                                                                                                                                ent.
  @Test
  public void testGLRMPredMojo() {
    try {
      Scope.enter();
      CreateFrame cf = new CreateFrame();
      Random generator = new Random();
      int numRows = generator.nextInt(10000) + 50000;
      int numCols = generator.nextInt(17) + 3;
      cf.rows = numRows;
      cf.cols = numCols;
      cf.binary_fraction = 0;
      cf.string_fraction = 0;
      cf.time_fraction = 0;
      cf.has_response = false;
      cf.positive_response = true;
      cf.missing_fraction = 0.1;
      cf.seed = System.currentTimeMillis();
      System.out.println("Createframe parameters: rows: " + numRows + " cols:" + numCols + " seed: " + cf.seed);

      Frame trainMultinomial = Scope.track(cf.execImpl().get());
      double tfrac = 0.2;
      SplitFrame sf = new SplitFrame(trainMultinomial, new double[]{1 - tfrac, tfrac}, new Key[]{Key.make("train.hex"), Key.make("test.hex")});
      sf.exec().get();
      Key[] ksplits = sf._destination_frames;
      Frame tr = DKV.get(ksplits[0]).get();
      Frame te = DKV.get(ksplits[1]).get();
      Scope.track(tr);
      Scope.track(te);

      GLRMParameters parms = new GLRMParameters();
      parms._train = tr._key;
      parms._k = 3;
      parms._loss = GlrmLoss.Quadratic;
      parms._init = GlrmInitialization.SVD;
      parms._max_iterations = 10;
      parms._regularization_x = GlrmRegularizer.Quadratic;
      parms._gamma_x = 0;
      parms._gamma_y = 0;
      parms._seed = cf.seed;

      GLRM glrm = new GLRM(parms);
      GLRMModel model = glrm.trainModel().get();
      Scope.track_generic(model);
      Frame xfactorTr = DKV.get(model.gen_representation_key(tr)).get();
      Scope.track(xfactorTr);
      Frame predTr = model.score(tr);
      Scope.track(predTr);
      assertEquals(predTr.numRows(), xfactorTr.numRows()); // make sure x factor is derived from tr
      Frame predT = model.score(te); // predict on new data and compare with mojo
      Scope.track(predT);
      Frame xfactorTe = DKV.get(model.gen_representation_key(te)).get();
      Scope.track(xfactorTe);
      assertEquals(predT.numRows(), xfactorTe.numRows()); // make sure x factor is derived from te
      Assert.assertTrue(model.testJavaScoring(te, predT, 1e-6, 1e-6, 1));
    } finally {
      Scope.exit();
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
      Scope.enter();
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 4;
      parms._init = GlrmInitialization.User;
      parms._user_y = init._key;
      parms._transform = DataInfo.TransformType.NONE;
      parms._recover_svd = false;
      parms._max_iterations = 1000;
      parms._seed = seed;

      Log.info("\nNon-negative matrix factorization");
      parms._gamma_x = parms._gamma_y = 1;
      parms._regularization_x = GlrmRegularizer.NonNegative;
      parms._regularization_y = GlrmRegularizer.NonNegative;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } finally {
        if (model != null) model.delete();
      }

      Log.info("\nOrthogonal non-negative matrix factorization");
      parms._gamma_x = parms._gamma_y = 1;
      parms._regularization_x = GlrmRegularizer.OneSparse;
      parms._regularization_y = GlrmRegularizer.NonNegative;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } finally {
        if (model != null) model.delete();
      }

      Log.info("\nQuadratic clustering (k-means)");
      parms._gamma_x = 1; parms._gamma_y = 0;
      parms._regularization_x = GlrmRegularizer.UnitOneSparse;
      parms._regularization_y = GlrmRegularizer.None;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } finally {
        if (model != null) model.delete();
      }

      Log.info("\nQuadratic mixture (soft k-means)");
      parms._gamma_x = 1; parms._gamma_y = 0;
      parms._regularization_x = GlrmRegularizer.UnitOneSparse;
      parms._regularization_y = GlrmRegularizer.None;
      try {
        job = new GLRM(parms);
        model = job.trainModel().get();
        Log.info("Iteration " + model._output._iterations + ": Objective value = " + model._output._objective);
        Log.info("Archetypes:\n" + model._output._archetypes.toString());
        model.score(train).delete();
        ModelMetricsGLRM mm = (ModelMetricsGLRM)ModelMetrics.getFromDKV(model,train);
        Log.info("Numeric Sum of Squared Error = " + mm._numerr + "\tCategorical Misclassification Error = " + mm._caterr);
      } finally {
        if (model != null) model.delete();
      }
    } finally {
      init.delete();
      if (train != null) train.delete();
      Scope.exit();
    }
  }
  
  @Test
  public void testTransform() {
    Scope.enter();
    try {
      Frame train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      Frame test = parseTestFile(Key.make("arrestsT.hex"), "smalldata/pca_test/USArrests.csv");
      Scope.track(train);
      Scope.track(test);
      GLRMParameters gparms = new GLRMParameters();  // build GLRM
      gparms._train = train._key;
      gparms._k = 3;
      gparms._seed=12345;
      
      GLRMModel gmodel = new GLRM(gparms).trainModel().get();
      Scope.track_generic(gmodel);
      
      Frame predTe = gmodel.score(test);
      Scope.track(predTe);
      Frame transformF = gmodel.transform(test);  // call transform after calling scoring
      Scope.track(transformF);

      GLRMModel gmodel2 = new GLRM(gparms).trainModel().get();
      Scope.track_generic(gmodel2);
      Frame transformF2 = gmodel2.transform(test);  // call transform without calling scoring
      Scope.track(transformF2);
      TestUtil.assertFrameEquals(transformF, transformF2, 1e-6);
      
      // test rapids transform
      String rapidString = "(transform "+gmodel.getKey()+" "+test.getKey()+")";
      Val result = Rapids.exec(rapidString);
      Frame transform3 = result.getFrame();
      Scope.track(transform3);
      TestUtil.assertFrameEquals(transformF, transform3, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  // PUBDEV-3501: Variance metrics for GLRM.  I compared the variance metrics calculated by PCA
  // and by GLRM to make sure they agree.
  @Test public void testArrestsVarianceMetrics() throws InterruptedException, ExecutionException {
    // Results with de-meaned training frame
    double[] stddev = new double[] {83.732400, 14.212402, 6.489426, 2.482790};
    double[][] eigvec = ard(ard(0.04170432, -0.04482166, 0.07989066, -0.99492173),
            ard(0.99522128, -0.05876003, -0.06756974, 0.03893830),
            ard(0.04633575, 0.97685748, -0.20054629, -0.05816914),
            ard(0.07515550, 0.20071807, 0.97408059, 0.07232502));

    // Results with standardized training frame
    double[] stddev_std = new double[] {1.5748783, 0.9948694, 0.5971291, 0.4164494};
    double[][] eigvec_std = ard(ard(-0.5358995, 0.4181809, -0.3412327, 0.64922780),
            ard(-0.5831836, 0.1879856, -0.2681484, -0.74340748),
            ard(-0.2781909, -0.8728062, -0.3780158, 0.13387773),
            ard(-0.5434321, -0.1673186, 0.8177779, 0.08902432));

    Frame train = null;
    PCAModel model = null;
    GLRMModel gmodel = null;

    try {
      train = parseTestFile(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
      for (DataInfo.TransformType std : new DataInfo.TransformType[] {
              DataInfo.TransformType.DEMEAN,
              DataInfo.TransformType.STANDARDIZE }) {
        try {
          PCAModel.PCAParameters parms = new PCAModel.PCAParameters();  // build PCA
          parms._train = train._key;
          parms._k = 4;
          parms._transform = std;
          parms._max_iterations = 1000;
          parms._pca_method = PCAModel.PCAParameters.Method.Power;
          model = new PCA(parms).trainModel().get();

          GLRMParameters gparms = new GLRMParameters();  // build GLRM
          gparms._train = train._key;
          gparms._k = 4;
          gparms._transform = std;
          gparms._loss = GlrmLoss.Quadratic;
          gparms._init = GlrmInitialization.SVD;
          gparms._max_iterations = 2000;
          gparms._gamma_x = 0;
          gparms._gamma_y = 0;
          gparms._recover_svd = true;

          gmodel = new GLRM(gparms).trainModel().get();
          assert gmodel != null;

          IcedWrapper[][] pcaInfo = model._output._importance.getCellValues();
          IcedWrapper[][] glrmInfo = gmodel._output._importance.getCellValues();

          if (std == DataInfo.TransformType.DEMEAN) { // check to make sure PCA generated correct results first
            TestUtil.checkStddev(stddev, model._output._std_deviation, TOLERANCE);
            TestUtil.checkEigvec(eigvec, model._output._eigenvectors, TOLERANCE);
          } else if (std == DataInfo.TransformType.STANDARDIZE) {
            TestUtil.checkStddev(stddev_std, model._output._std_deviation, TOLERANCE);
            TestUtil.checkEigvec(eigvec_std, model._output._eigenvectors, TOLERANCE);
          }

          // compare PCA and
          // variance metrics here after we know PCA has worked correctly
          TestUtil.checkIcedArrays(model._output._importance.getCellValues(),
                  gmodel._output._importance.getCellValues(), TOLERANCE);

        } finally {
          if( model != null ) model.delete();
          if (gmodel != null) gmodel.delete();
        }
      }
    } finally {
      if(train != null) train.delete();
    }
  }

  /*
    Test GLRM with wide dataset.  I chose prostate_cat.csv because it contains both enums and
    numerical data columns.  First, generate the models with normal GLRM operation.  Next, force
    wideDataset and generate the model again.  Finally, compare the two models and there should
    be the same.
   */
  @Test public void testWideDataSetGLRMCat() throws InterruptedException, ExecutionException {
    Scope.enter();
    GLRMModel modelN = null;     // store PCA models generated with original implementation
    GLRMModel modelW = null;     // store PCA models generated with wideDataSet set to true
    Frame train = null, scoreN = null, scoreW = null;
    double tolerance = 1e-6;

    try {
      train = parseTestFile(Key.make("Prostrate_CAT"), "smalldata/prostate/prostate_cat.csv");
      train.vec(0).setNA(0);          // set NAs
      train.vec(3).setNA(10);
      train.vec(5).setNA(20);
      Scope.track(train);
      DKV.put(train);

      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 3;
      parms._transform = DataInfo.TransformType.DEMEAN;
      parms._seed = 12345;
      parms._gamma_x = 1;
      parms._gamma_y = 0.5;
      if (!Arrays.asList(train.typesStr()).contains("Enum")) {
        parms._init = GlrmInitialization.SVD;
      }
      parms._regularization_x = GlrmRegularizer.Quadratic;
      parms._regularization_y = GlrmRegularizer.Quadratic;
      parms._recover_svd = true;
      GLRM glrmParms = new GLRM(parms);
      modelN = glrmParms.trainModel().get();
      scoreN = modelN.score(train);
      Scope.track(scoreN);
      Scope.track_generic(modelN);

      GLRM glrmParmsW = new GLRM(parms);
      glrmParmsW.setWideDataset(true);  // force to treat dataset as wide even though it is not.
      modelW = glrmParmsW.trainModel().get();
      scoreW = modelW.score(train);
      Scope.track(scoreW);
      Scope.track_generic(modelW);

      // compare eigenvectors and eigenvalues generated by original PCA and wide dataset PCA.
						TestUtil.checkStddev(modelW._output._std_deviation, modelN._output._std_deviation, tolerance);
      boolean[] flippedArch = TestUtil.checkEigvec(modelN._output._archetypes_raw._archetypes,
              modelW._output._archetypes_raw._archetypes, tolerance);    // check archetypes
						boolean[] flippedEig = TestUtil.checkEigvec(modelW._output._eigenvectors, modelN._output._eigenvectors,
              tolerance);
						assertTrue(Arrays.equals(flippedArch, flippedEig)); // should be the same
      TestUtil.assertIdenticalUpToRelTolerance(scoreW, scoreN, tolerance);
    } finally {
      Scope.exit();
    }
  }

  @Test public void testWideDataSetGLRMDec() throws InterruptedException, ExecutionException {
    Scope.enter();
    GLRMModel modelN = null;     // store PCA models generated with original implementation
    GLRMModel modelW = null;     // store PCA models generated with wideDataSet set to true
    Frame train = null, scoreN = null, scoreW = null;
    double tolerance = 1e-6;

    try {
      train = parseTestFile(Key.make("deacathlon"), "smalldata/pca_test/decathlon.csv");
      Scope.track(train);
      DKV.put(train);

      GLRMParameters parms = new GLRMParameters();
      parms._train = train._key;
      parms._k = 3;
      parms._transform = DataInfo.TransformType.NONE;
      parms._seed = 12345;
      parms._gamma_x = 1;
      parms._gamma_y = 0.5;
      if (!Arrays.asList(train.typesStr()).contains("Enum")) {
        parms._init = GlrmInitialization.SVD;
      }
      parms._regularization_x = GlrmRegularizer.Quadratic;
      parms._regularization_y = GlrmRegularizer.Quadratic;
      parms._recover_svd = true;
      GLRM glrmParms = new GLRM(parms);
      modelN = glrmParms.trainModel().get();
      scoreN = modelN.score(train);
      Scope.track(scoreN);
      Scope.track_generic(modelN);

      GLRM glrmParmsW = new GLRM(parms);
      glrmParmsW.setWideDataset(true);  // force to treat dataset as wide even though it is not.
      modelW = glrmParmsW.trainModel().get();
      scoreW = modelW.score(train);
      Scope.track(scoreW);
      Scope.track_generic(modelW);

      // compare eigenvectors and eigenvalues generated by original PCA and wide dataset PCA.
      TestUtil.checkStddev(modelW._output._std_deviation, modelN._output._std_deviation, tolerance);
      boolean[] flippedArch = TestUtil.checkEigvec(modelN._output._archetypes_raw._archetypes,
              modelW._output._archetypes_raw._archetypes, tolerance);    // check archetypes
      boolean[] flippedEig = TestUtil.checkEigvec(modelW._output._eigenvectors, modelN._output._eigenvectors,
              tolerance);
      assertTrue(Arrays.equals(flippedArch, flippedEig)); // should be the same
      TestUtil.assertIdenticalUpToRelTolerance(scoreW, scoreN, tolerance);
    } finally {
      Scope.exit();
    }
  }
}
