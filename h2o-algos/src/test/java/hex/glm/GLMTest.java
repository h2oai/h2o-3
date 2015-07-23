package hex.glm;

import hex.DataInfo;
import hex.DataInfo.TransformType;
import hex.ModelMetrics;
import hex.ModelMetricsBinomialGLM;
import hex.ModelMetricsRegressionGLM;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.glm.GLMTask.GLMIterationTask;
import hex.glm.GLMTask.GLMGradientTask;
import hex.glm.GLMTask.GLMLineSearchTask;
import hex.glm.GLMTask.LBFGS_LogisticGradientTask;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import water.*;
import water.H2O.H2OCountedCompleter;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.parser.ParseDataset;
import water.parser.ValueString;
import water.util.ArrayUtils;
import water.util.MathUtils;

import java.util.*;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GLMTest  extends TestUtil {
  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  //------------------- simple tests on synthetic data------------------------------------
  @Test
  public void testGaussianRegression() throws InterruptedException, ExecutionException {
    GLM job = null;
    Key raw = Key.make("gaussian_test_data_raw");
    Key parsed = Key.make("gaussian_test_data_parsed");
    Key modelKey = Key.make("gaussian_test");
    GLMModel model = null;
    Frame fr = null, res = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9");
      fr = ParseDataset.parse(parsed, raw);
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._train = fr._key;
      // params._response = 1;
      params._response_column = fr._names[1];
      params._lambda = new double[]{0};
//      params._standardize= false;
      job = new GLM(modelKey, "glm test simple gaussian", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      HashMap<String, Double> coefs = model.coefficients();
      assertEquals(0.0, coefs.get("Intercept"), 1e-4);
      assertEquals(0.1, coefs.get("x"), 1e-4);

      res = model.score(fr);
      // Build a POJO, validate same results
      Assert.assertTrue(model.testJavaScoring(fr, res, 1e-15));

    } finally {
      if (fr != null) fr.remove();
      if (res != null) res.remove();
      if (model != null) model.remove();
      if (job != null) job.remove();
    }
  }


  /**
   * Test Poisson regression on simple and small synthetic dataset.
   * Equation is: y = exp(x+1);
   */
  @Test
  public void testPoissonRegression() throws InterruptedException, ExecutionException {
    GLM job = null;
    Key raw = Key.make("poisson_test_data_raw");
    Key parsed = Key.make("poisson_test_data_parsed");
    Key modelKey = Key.make("poisson_test");
    GLMModel model = null;
    Frame fr = null, res = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,2\n1,4\n2,8\n3,16\n4,32\n5,64\n6,128\n7,256");
      fr = ParseDataset.parse(parsed, raw);
      Vec v = fr.vec(0);
      System.out.println(v.min() + ", " + v.max() + ", mean = " + v.mean());
      GLMParameters params = new GLMParameters(Family.poisson);
      params._train = fr._key;
      // params._response = 1;
      params._response_column = fr._names[1];
      params._lambda = new double[]{0};
      params._standardize = false;
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      for (double c : model.beta())
        assertEquals(Math.log(2), c, 1e-2); // only 1e-2 precision cause the perfect solution is too perfect -> will trigger grid search
      model.delete();
      fr.delete();
      job.remove();

      // Test 2, example from http://www.biostat.umn.edu/~dipankar/bmtry711.11/lecture_13.pdf
      FVecTest.makeByteVec(raw, "x,y\n1,0\n2,1\n3,2\n4,3\n5,1\n6,4\n7,9\n8,18\n9,23\n10,31\n11,20\n12,25\n13,37\n14,45\n");
      fr = ParseDataset.parse(parsed, raw);
      GLMParameters params2 = new GLMParameters(Family.poisson);
      params2._train = fr._key;
      // params2._response = 1;
      params2._response_column = fr._names[1];
      params2._lambda = new double[]{0};
      params2._standardize = false;
      params2._beta_epsilon = 1e-5;
      job = new GLM(modelKey, "glm test simple poisson", params2);
      model = job.trainModel().get();
      assertEquals(0.3396, model.beta()[1], 1e-4);
      assertEquals(0.2565, model.beta()[0], 1e-4);
      // test scoring
      res = model.score(fr);
      // Build a POJO, validate same results
      Assert.assertTrue(model.testJavaScoring(fr, res, 1e-15));

    } finally {
      if (fr != null) fr.delete();
      if (res != null) res.delete();
      if (model != null) model.delete();
      if (job != null) job.remove();
    }
  }


  /**
   * Test Gamma regression on simple and small synthetic dataset.
   * Equation is: y = 1/(x+1);
   *
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test
  public void testGammaRegression() throws InterruptedException, ExecutionException {
    GLM job = null;
    GLMModel model = null;
    Frame fr = null, res = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      Key raw = Key.make("gamma_test_data_raw");
      Key parsed = Key.make("gamma_test_data_parsed");
      FVecTest.makeByteVec(raw, "x,y\n0,1\n1,0.5\n2,0.3333333\n3,0.25\n4,0.2\n5,0.1666667\n6,0.1428571\n7,0.125");
      fr = ParseDataset.parse(parsed, raw);
//      /public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
//      double [] vals = new double[] {1.0,1.0};
      //public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
      GLMParameters params = new GLMParameters(Family.gamma);
      // params._response = 1;
      params._response_column = fr._names[1];
      params._train = parsed;
      params._lambda = new double[]{0};
      Key modelKey = Key.make("gamma_test");
      job = new GLM(modelKey, "glm test simple gamma", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      for (double c : model.beta()) assertEquals(1.0, c, 1e-4);
      // test scoring
      res = model.score(fr);
      // Build a POJO, validate same results
      Assert.assertTrue(model.testJavaScoring(fr, res, 1e-15));

    } finally {
      if (fr != null) fr.delete();
      if (res != null) res.delete();
      if (model != null) model.delete();
      if (job != null) job.remove();
    }
  }

////  //simple tweedie test
//  @Test public void testTweedieRegression() throws InterruptedException, ExecutionException{
//    Key raw = Key.make("gaussian_test_data_raw");
//    Key parsed = Key.make("gaussian_test_data_parsed");
//    Key modelKey = Key.make("gaussian_test");
//    Frame fr = null;
//    GLMModel model = null;
//    try {
//      // make data so that the expected coefficients is icept = col[0] = 1.0
//      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9\n0,0\n1,0\n2,0\n3,0\n4,0\n5,0\n6,0\n7,0\n8,0\n9,0");
//      fr = ParseDataset.parse(parsed, new Key[]{raw});
//      double [] powers = new double [] {1.5,1.1,1.9};
//      double [] intercepts = new double []{3.643,1.318,9.154};
//      double [] xs = new double []{-0.260,-0.0284,-0.853};
//      for(int i = 0; i < powers.length; ++i){
//        DataInfo dinfo = new DataInfo(fr, 1, false, DataInfo.TransformType.NONE);
//        GLMParameters glm = new GLMParameters(Family.tweedie);
//
//        new GLM2("GLM test of gaussian(linear) regression.",Key.make(),modelKey,dinfo,glm,new double[]{0},0).fork().get();
//        model = DKV.get(modelKey).get();
//        testHTML(model);
//        HashMap<String, Double> coefs = model.coefficients();
//        assertEquals(intercepts[i],coefs.get("Intercept"),1e-3);
//        assertEquals(xs[i],coefs.get("x"),1e-3);
//      }
//    }finally{
//      if( fr != null ) fr.delete();
//      if(model != null)model.delete();
//    }
//  }

  @Test
  public void testLineSearchTask() {
    Key parsed = Key.make("cars_parsed");
    Frame fr = null;
    DataInfo dinfo = null;
    double ymu = 0;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/mixcat_train.csv");
      GLMParameters params = new GLMParameters(Family.binomial, Family.binomial.defaultLink, new double[]{0}, new double[]{0});
      // params._response = fr.find(params._response_column);
      params._train = parsed;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      fr.add("Useless", fr.remove("Useless"));

      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);
      DKV.put(dinfo._key, dinfo);

      double[] beta = MemoryManager.malloc8d(dinfo.fullN() + 1);
      double[] pk = MemoryManager.malloc8d(dinfo.fullN() + 1);
      Random rnd = new Random(987654321);
      for (int i = 0; i < beta.length; ++i) {
        beta[i] = 1 - 2 * rnd.nextDouble();
        pk[i] = 10 * (1 - 2 * rnd.nextDouble());
      }
      GLMLineSearchTask glst = new GLMLineSearchTask(dinfo, params, 1, beta, pk, .7, 16, null).doAll(dinfo._adaptedFrame);
      double step = 1, stepDec = .7;
      for (int i = 0; i < glst._nSteps; ++i) {
        double[] b = beta.clone();
        for (int j = 0; j < b.length; ++j) {
          b[j] += step * pk[j];
        }
        GLMIterationTask glmt = new GLMTask.GLMIterationTask(null, dinfo, 0, params, true, b, ymu, null, null).doAll(dinfo._adaptedFrame);
        assertEquals("objective values differ at step " + i + ": " + step, glmt._likelihood, glst._likelihoods[i], 1e-8);
        System.out.println("step = " + step + ", obj = " + glmt._likelihood + ", " + glst._likelihoods[i]);
        step *= stepDec;
      }
    } finally {
      if (fr != null) fr.delete();
      if (dinfo != null) dinfo.remove();
    }
  }

  @Test
  public void testAllNAs() {
    Key raw = Key.make("gamma_test_data_raw");
    Key parsed = Key.make("gamma_test_data_parsed");
    FVecTest.makeByteVec(raw, "x,y,z\n1,0,NA\n2,NA,1\nNA,3,2\n4,3,NA\n5,NA,1\nNA,6,4\n7,NA,9\n8,NA,18\nNA,9,23\n10,31,NA\nNA,11,20\n12,NA,25\nNA,13,37\n14,45,NA\n");
    Frame fr = ParseDataset.parse(parsed, raw);
    try {
      GLMParameters params = new GLMParameters(Family.gamma);
      // params._response = 1;
      params._response_column = fr._names[1];
      params._train = parsed;
      params._lambda = new double[]{0};
      Key modelKey = Key.make("gamma_test");
      GLM job = new GLM(modelKey, "glm test simple gamma", params);
      job.trainModel().get();
      assertFalse("should've thrown IAE", true);
    } catch (H2OModelBuilderIllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Got no data to run on after filtering out the rows with missing values."));
    }
    fr.delete();
  }

  // Make sure all three implementations of gradient computation in GLM get the same results
  @Test
  public void testGradientTask() {
    Key parsed = Key.make("cars_parsed");
    Frame fr = null;
    DataInfo dinfo = null;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/mixcat_train.csv");
      GLMParameters params = new GLMParameters(Family.binomial, Family.binomial.defaultLink, new double[]{0}, new double[]{0});
      // params._response = fr.find(params._response_column);
      params._train = parsed;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      fr.add("Useless", fr.remove("Useless"));

      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);
      DKV.put(dinfo._key,dinfo);
      double [] beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      Random rnd = new Random(987654321);
      for (int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2 * rnd.nextDouble();

      GLMGradientTask grtCol = new GLMGradientTask(dinfo, params, params._lambda[0], beta, 1, null).forceColAccess().doAll(dinfo._adaptedFrame);
      GLMGradientTask grtRow = new GLMGradientTask(dinfo, params, params._lambda[0], beta, 1, null).forceRowAccess().doAll(dinfo._adaptedFrame);
      LBFGS_LogisticGradientTask logistic = (LBFGS_LogisticGradientTask) new LBFGS_LogisticGradientTask(dinfo, params, params._lambda[0], beta, 1, null).forceRowAccess().doAll(dinfo._adaptedFrame);
      for (int i = 0; i < beta.length; ++i) {
        assertEquals("gradients differ", grtRow._gradient[i], grtCol._gradient[i], 1e-4);
        assertEquals("gradients differ", grtRow._gradient[i], logistic._gradient[i], 1e-4);
      }
      params = new GLMParameters(Family.gaussian, Family.gaussian.defaultLink, new double[]{0}, new double[]{0});
      params._use_all_factor_levels = false;
      dinfo.remove();
      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);
      DKV.put(dinfo._key,dinfo);
      beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      rnd = new Random(1987654321);
      for (int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2 * rnd.nextDouble();
      grtCol = new GLMGradientTask(dinfo, params, params._lambda[0], beta, 1, null).forceColAccess().doAll(dinfo._adaptedFrame);
      grtRow = new GLMGradientTask(dinfo, params, params._lambda[0], beta, 1, null).forceRowAccess().doAll(dinfo._adaptedFrame);

      for (int i = 0; i < beta.length; ++i)
        assertEquals("gradients differ: " + Arrays.toString(grtRow._gradient) + " != " + Arrays.toString(grtCol._gradient), grtRow._gradient[i], grtCol._gradient[i], 1e-4);
      dinfo.remove();
      fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
      params = new GLMParameters(Family.poisson, Family.poisson.defaultLink, new double[]{0}, new double[]{0});
      // params._response = fr.find(params._response_column);
      params._train = parsed;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      dinfo = new DataInfo(Key.make(), fr, null, 1, params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);
      DKV.put(dinfo._key,dinfo);
      beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      rnd = new Random(987654321);
      for (int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2 * rnd.nextDouble();

      grtCol = new GLMGradientTask(dinfo, params, params._lambda[0], beta, 1, null).forceColAccess().doAll(dinfo._adaptedFrame);
      grtRow = new GLMGradientTask(dinfo, params, params._lambda[0], beta, 1, null).forceRowAccess().doAll(dinfo._adaptedFrame);
      for (int i = 0; i < beta.length; ++i)
        assertEquals("gradients differ: " + Arrays.toString(grtRow._gradient) + " != " + Arrays.toString(grtCol._gradient), grtRow._gradient[i], grtCol._gradient[i], 1e-4);
      dinfo.remove();
      // arcene takes too long
    } finally {
      if (fr != null) fr.delete();
      if (dinfo != null) dinfo.remove();
    }
  }
  //------------ TEST on selected files form small data and compare to R results ------------------------------------

  /**
   * Simple test for poisson, gamma and gaussian families (no regularization, test both lsm solvers).
   * Basically tries to predict horse power based on other parameters of the cars in the dataset.
   * Compare against the results from standard R glm implementation.
   *
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test
  public void testCars() throws InterruptedException, ExecutionException {
    GLM job = null;
    Key parsed = Key.make("cars_parsed");
    Key modelKey = Key.make("cars_model");
    Frame fr = null;
    GLMModel model = null;
    Frame score = null;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
      GLMParameters params = new GLMParameters(Family.poisson, Family.poisson.defaultLink, new double[]{0}, new double[]{0});
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      HashMap<String, Double> coefs = model.coefficients();
      String[] cfs1 = new String[]{"Intercept", "economy (mpg)", "cylinders", "displacement (cc)", "weight (lb)", "0-60 mph (s)", "year"};
      double[] vls1 = new double[]{4.9504805, -0.0095859, -0.0063046, 0.0004392, 0.0001762, -0.0469810, 0.0002891};
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls1[i], coefs.get(cfs1[i]), 1e-4);
      // test gamma
      double[] vls2 = new double[]{8.992e-03, 1.818e-04, -1.125e-04, 1.505e-06, -1.284e-06, 4.510e-04, -7.254e-05};
      score = model.score(fr);
      score.delete();
      model.delete();
      job.remove();

      params = new GLMParameters(Family.gamma, Family.gamma.defaultLink, new double[]{0}, new double[]{0});
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      params._beta_epsilon = 1e-5;
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls2[i], coefs.get(cfs1[i]), 1e-4);
      score = model.score(fr);
      model.delete();
      job.remove();
      // test gaussian
      double[] vls3 = new double[]{166.95862, -0.00531, -2.46690, 0.12635, 0.02159, -4.66995, -0.85724};
      params = new GLMParameters(Family.gaussian);
      params._response_column = "power (hp)";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"name"};
      params._train = parsed;
      params._lambda = new double[]{0};
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls3[i], coefs.get(cfs1[i]), 1e-4);
      // test scoring
    } finally {
      if (fr != null) fr.delete();
      if (score != null) score.delete();
      if (model != null) model.delete();
      if (job != null) job.remove();
    }
  }

  // Leask xval keys
//  @Test public void testXval() {
//    GLM job = null;
//    GLMModel model = null;
//    Frame fr = parse_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
//    Frame score = null;
//    try{
//      Scope.enter();
//      // R results
////      Coefficients:
////        (Intercept)           ID          AGE       RACER2       RACER3        DPROS        DCAPS          PSA          VOL      GLEASON
////          -8.894088     0.001588    -0.009589     0.231777    -0.459937     0.556231     0.556395     0.027854    -0.011355     1.010179
//      String [] cfs1 = new String [] {"Intercept","AGE", "RACE.R2","RACE.R3", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
//      double [] vals = new double [] {-8.14867, -0.01368, 0.32337, -0.38028, 0.55964, 0.49548, 0.02794, -0.01104, 0.97704};
//      GLMParameters params = new GLMParameters(Family.binomial);
//      params._n_folds = 10;
//      params._response_column = "CAPSULE";
//      params._ignored_columns = new String[]{"ID"};
//      params._train = fr._key;
//      params._lambda = new double[]{0};
//      job = new GLM(Key.make("prostate_model"),"glm test simple poisson",params);
//      model = job.trainModel().get();
//      HashMap<String, Double> coefs = model.coefficients();
//      for(int i = 0; i < cfs1.length; ++i)
//        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
//      GLMValidation val = model.trainVal();
////      assertEquals(512.3, val.nullDeviance(),1e-1);
////      assertEquals(378.3, val.residualDeviance(),1e-1);
////      assertEquals(396.3, val.AIC(),1e-1);
////      score = model.score(fr);
////
////      hex.ModelMetrics mm = hex.ModelMetrics.getFromDKV(model,fr);
////
////      AUCData adata = mm._aucdata;
////      assertEquals(val.auc(),adata.AUC(),1e-2);
////      GLMValidation val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("CAPSULE"),score.vec("1")})._val;
////      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-6);
////      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
//    } finally {
//      fr.delete();
//      if(model != null)model.delete();
//      if(score != null)score.delete();
//      if( job != null ) job.remove();
//      Scope.exit();
//    }
//  }

  /**
   * Test bounds on prostate dataset, 2 cases :
   * 1) test against known result in glmnet (with elastic net regularization) with elastic net penalty
   * 2) test with no regularization, check the gradient in the end.
   */
  @Test
  public void testBounds() {
//    glmnet's result:
//    res2 <- glmnet(x=M,y=D$CAPSULE,lower.limits=-.5,upper.limits=.5,family='binomial')
//    res2$beta[,58]
//    AGE        RACE          DPROS       PSA         VOL         GLEASON
//    -0.00616326 -0.50000000  0.50000000  0.03628192 -0.01249324  0.50000000 //    res2$a0[100]
//    res2$a0[58]
//    s57
//    -4.155864
//    lambda = 0.001108, null dev =  512.2888, res dev = 379.7597
    GLMModel model = null;

    Key parsed = Key.make("prostate_parsed");
    Key modelKey = Key.make("prostate_model");

    Frame fr = parse_test_file(parsed, "smalldata/logreg/prostate.csv");
    Key betaConsKey = Key.make("beta_constraints");

    String[] cfs1 = new String[]{"AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON", "Intercept"};
    double[] vals = new double[]{-0.006502588, -0.500000000, 0.500000000, 0.400000000, 0.034826559, -0.011661747, 0.500000000, -4.564024};

//    [AGE, RACE, DPROS, DCAPS, PSA, VOL, GLEASON, Intercept]
    FVecTest.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n AGE, -.5, .5\n RACE, -.5, .5\n DCAPS, -.4, .4\n DPROS, -.5, .5 \nPSA, -.5, .5\n VOL, -.5, .5\nGLEASON, -.5, .5");
    Frame betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.binomial;
      params._beta_constraints = betaConstraints._key;
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._alpha = new double[]{1};
      params._lambda = new double[]{0.001607};
      GLM job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      assertTrue(job.isDone());
      model = DKV.get(modelKey).get();
//      Map<String, Double> coefs =  model.coefficients();
//      for (int i = 0; i < cfs1.length; ++i)
//        assertEquals(vals[i], coefs.get(cfs1[i]), 1e-1);
      ModelMetricsBinomialGLM val = (ModelMetricsBinomialGLM) model._output._training_metrics;
      assertEquals(512.2888, val._nullDev, 1e-1);
      // 388.4952716196743
      assertEquals(388.4686, val._resDev, 1e-1);
      model.delete();
      params._lambda = new double[]{0};
      params._alpha = new double[]{0};
      FVecTest.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n RACE, -.5, .5\n DCAPS, -.4, .4\n DPROS, -.5, .5 \nPSA, -.5, .5\n VOL, -.5, .5");
      betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      assertTrue(job.isDone());
      model = DKV.get(modelKey).get();
      double[] beta = model.beta();
      System.out.println("beta = " + Arrays.toString(beta));
      fr.add("CAPSULE", fr.remove("CAPSULE"));
      fr.remove("ID").remove();
      DKV.put(fr._key, fr);
      // now check the gradient
      DataInfo dinfo = new DataInfo(Key.make(),fr, null, 1, true, TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);
      LBFGS_LogisticGradientTask lt = (LBFGS_LogisticGradientTask)new LBFGS_LogisticGradientTask(dinfo,params,0,beta,1.0/380.0, null).doAll(dinfo._adaptedFrame);
      double [] grad = lt._gradient;
      String [] names = model.dinfo().coefNames();
      ValueString vs = new ValueString();
      outer:
      for (int i = 0; i < names.length; ++i) {
        for (int j = 0; j < betaConstraints.numRows(); ++j) {
          if (betaConstraints.vec("names").atStr(vs, j).toString().equals(names[i])) {
            if (Math.abs(beta[i] - betaConstraints.vec("lower_bounds").at(j)) < 1e-4 || Math.abs(beta[i] - betaConstraints.vec("upper_bounds").at(j)) < 1e-4) {
              continue outer;
            }
          }
        }
        assertEquals(0, grad[i], 1e-2);
      }
    } finally {
      fr.delete();
      betaConstraints.delete();
      if (model != null) model.delete();
    }
  }

  @Test
  public void testCoordinateDescent_airlines() {
    GLMModel model = null;

    Key parsed = Key.make("airlines_parsed");
    Key modelKey = Key.make("airlines_model");

    Frame fr = parse_test_file(parsed, "smalldata/airlines/AirlinesTrain.csv.zip");

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.binomial;
      params._solver = Solver.COORDINATE_DESCENT;
      params._response_column = "IsDepDelayed";
      params._ignored_columns = new String[]{"IsDepDelayed_REC"};
      params._train = fr._key;
      GLM job = new GLM(modelKey, "glm test simple coordinate descent", params);
      job.trainModel().get();
      assertTrue(job.isDone());
      model = DKV.get(modelKey).get();
      System.out.println(model._output._training_metrics);

    } finally {
      fr.delete();
      if (model != null) model.delete();
    }
  }

  @Test
  public void testCoordinateDescent_anomaly() {
    GLMModel model = null;
    Key parsed = Key.make("anomaly_parsed");
    Key modelKey = Key.make("anomaly_model");

    Frame fr = parse_test_file(parsed, "smalldata/anomaly/ecg_discord_train.csv");

    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = true;
      params._family = Family.gaussian;
      params._solver = Solver.COORDINATE_DESCENT;//COORDINATE_DESCENT;
      params._response_column = "C1";
      params._train = fr._key;
      GLM job = new GLM(modelKey, "glm test simple coordinate descent", params);
      job.trainModel().get();
      assertTrue(job.isDone());
      model = DKV.get(modelKey).get();
      System.out.println(model._output._training_metrics);

    } finally {
      fr.delete();
      if (model != null) model.delete();
    }
  }


  @Test
  public void testProximal() {
//    glmnet's result:
//    res2 <- glmnet(x=M,y=D$CAPSULE,lower.limits=-.5,upper.limits=.5,family='binomial')
//    res2$beta[,58]
//    AGE        RACE          DPROS       PSA         VOL         GLEASON
//    -0.00616326 -0.50000000  0.50000000  0.03628192 -0.01249324  0.50000000 //    res2$a0[100]
//    res2$a0[58]
//    s57
//    -4.155864
//    lambda = 0.001108, null dev =  512.2888, res dev = 379.7597
    Key parsed = Key.make("prostate_parsed");
    Key modelKey = Key.make("prostate_model");
    GLMModel model = null;

    Frame fr = parse_test_file(parsed, "smalldata/logreg/prostate.csv");
    fr.remove("ID").remove();
    DKV.put(fr._key, fr);
    Key betaConsKey = Key.make("beta_constraints");

    FVecTest.makeByteVec(betaConsKey, "names, beta_given, rho\n AGE, 0.1, 1\n RACE, -0.1, 1 \n DPROS, 10, 1 \n DCAPS, -10, 1 \n PSA, 0, 1\n VOL, 0, 1\nGLEASON, 0, 1\n Intercept, 0, 0 \n");
    Frame betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);
    try {
      // H2O differs on intercept and race, same residual deviance though
      GLMParameters params = new GLMParameters();
      params._standardize = false;
      params._family = Family.binomial;
      params._beta_constraints = betaConstraints._key;
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._alpha = new double[]{0};
      params._lambda = new double[]{0};
      GLM job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();

      double[] beta_1 = model.beta();
      params._solver = Solver.L_BFGS;
      params._max_iterations = 1000;
      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      fr.add("CAPSULE", fr.remove("CAPSULE"));
      // now check the gradient
      DataInfo dinfo = new DataInfo(Key.make(),fr, null, 1, true, TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);
      // todo: remove, result from h2o.1
      // beta = new double[]{0.06644411112189823, -0.11172826074033719, 9.77360531534266, -9.972691681370678, 0.24664516432994327, -0.12369381230741447, 0.11330593275731994, -19.64465932744036};
      LBFGS_LogisticGradientTask lt = (LBFGS_LogisticGradientTask) new LBFGS_LogisticGradientTask(dinfo, params, 0, beta_1, 1.0 / 380.0, null).doAll(dinfo._adaptedFrame);
      new GLMGradientTask(dinfo, params, 0, beta_1, 1.0 / 380, null).doAll(dinfo._adaptedFrame);
      double[] grad = lt._gradient;
      for (int i = 0; i < beta_1.length; ++i)
        assertEquals(0, grad[i] + betaConstraints.vec("rho").at(i) * (beta_1[i] - betaConstraints.vec("beta_given").at(i)), 1e-4);
    } finally {
      for (Vec v : betaConstraints.vecs())
        v.remove();
      DKV.remove(betaConstraints._key);
      for (Vec v : fr.vecs()) v.remove();
      DKV.remove(fr._key);
      if (model != null) model.delete();
    }
  }


//  // test categorical autoexpansions, run on airlines which has several categorical columns,
//  // once on explicitly expanded data, once on h2o autoexpanded and compare the results
//  @Test public void testSparseCategoricals() {
//    GLM job = null;
//    GLMModel model1 = null, model2 = null, model3 = null, model4 = null;
//
//    Frame frMM = parse_test_file("smalldata/glm_tets/train-2.csv");
//
////    Vec xy = frG.remove("xy");
//    frMM.remove("").remove();
//    frMM.add("IsDepDelayed", frMM.remove("IsDepDelayed"));
//    DKV.put(frMM._key,frMM);
//    Frame fr = parse_test_file("smalldata/airlines/AirlinesTrain.csv.zip"), res = null;
//    //  Distance + Origin + Dest + UniqueCarrier
//    String [] ignoredCols = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "DepTime","ArrTime","IsDepDelayed_REC"};
//    try{
//      Scope.enter();
//      GLMParameters params = new GLMParameters(Family.gaussian);
//      params._response_column = "IsDepDelayed";
//      params._ignored_columns = ignoredCols;
//      params._train = fr._key;
//      params._lambda = new double[]{1e-5};
//      params._standardize = false;
//      job = new GLM(Key.make("airlines_cat_nostd"),"Airlines with auto-expanded categoricals, no standardization",params);
//      model1 = job.trainModel().get();
//      Frame score1 = model1.score(fr);
//      ModelMetricsRegressionGLM mm = (ModelMetricsRegressionGLM) ModelMetrics.getFromDKV(model1, fr);
//      Assert.assertEquals(model1.validation().residual_deviance, mm._resDev, 1e-4);
//      System.out.println("NDOF = " + model1.validation().nullDOF() + ", numRows = " + score1.numRows());
//      Assert.assertEquals(model1.validation().residual_deviance, mm._MSE * score1.numRows(), 1e-4);
//      mm.remove();
//      res = model1.score(fr);
//      // Build a POJO, validate same results
//      Assert.assertTrue(model1.testJavaScoring(fr, res, 1e-15));
//
//      params._train = frMM._key;
//      params._ignored_columns = new String[]{"X"};
//      job = new GLM(Key.make("airlines_mm"),"Airlines with pre-expanded (mode.matrix) categoricals, no standardization",params);
//      model2 = job.trainModel().get();
//      params._standardize = true;
//      params._train = frMM._key;
//      params._use_all_factor_levels = true;
//      // test the gram
//      DataInfo dinfo = new DataInfo(Key.make(),frMM, null, 1, true, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true);
//      GLMIterationTask glmt = new GLMIterationTask(null,dinfo,1e-5,params,false,null,0,null, null).doAll(dinfo._adaptedFrame);
//      for(int i = 0; i < glmt._xy.length; ++i) {
//        for(int j = 0; j <= i; ++j ) {
//          assertEquals(frG.vec(j).at(i), glmt._gram.get(i, j), 1e-5);
//        }
//        assertEquals(xy.at(i), glmt._xy[i], 1e-5);
//      }
//      frG.delete();
//      xy.remove();
//      params._standardize = true;
//      params._family = Family.binomial;
//      params._link = Link.logit;
//      job = new GLM(Key.make("airlines_mm"),"Airlines with pre-expanded (mode.matrix) categoricals, no standardization",params);
//      model3 = job.trainModel().get();
//      params._train = fr._key;
//      params._ignored_columns = ignoredCols;
//      job = new GLM(Key.make("airlines_mm"),"Airlines with pre-expanded (mode.matrix) categoricals, no standardization",params);
//      model4 = job.trainModel().get();
//      assertEquals(model3.validation().null_deviance,model4.validation().nullDeviance(),1e-4);
//      assertEquals(model4.validation().residual_deviance, model3.validation().residualDeviance(), model3.validation().null_deviance * 1e-3);
//      HashMap<String, Double> coefs1 = model1.coefficients();
//      HashMap<String, Double> coefs2 = model2.coefficients();
//      GLMValidation val1 = model1.validation();
//      GLMValidation val2 = model2.validation();
//      // compare against each other
//      for(String s:coefs2.keySet()) {
//        String s1 = s;
//        if(s.startsWith("Origin"))
//          s1 = "Origin." + s.substring(6);
//        if(s.startsWith("Dest"))
//          s1 = "Dest." + s.substring(4);
//        if(s.startsWith("UniqueCarrier"))
//          s1 = "UniqueCarrier." + s.substring(13);
//        assertEquals("coeff " + s1 + " differs, " + coefs1.get(s1) + " != " + coefs2.get(s), coefs1.get(s1), coefs2.get(s),1e-4);
//        DKV.put(frMM._key,frMM); // update the frame in the KV after removing the vec!
//      }
//      assertEquals(val1.nullDeviance(), val2.nullDeviance(),1e-4);
//      assertEquals(val1.residualDeviance(), val2.residualDeviance(),1e-4);
//      assertEquals(val1.aic, val2.aic,1e-2);
//      // compare result against glmnet
//      assertEquals(5336.918,val1.residualDeviance(),1);
//      assertEquals(6051.613,val1.nullDeviance(),1);
//
//
//      // lbfgs
////      params._solver = Solver.L_BFGS;
////      params._train = fr._key;
////      params._lambda = new double[]{.3};
////      job = new GLM(Key.make("lbfgs_cat"),"lbfgs glm built over categorical columns",params);
////      model3 = job.trainModel().get();
////      params._train = frMM._key;
////      job = new GLM(Key.make("lbfgs_mm"),"lbfgs glm built over pre-expanded categoricals (model.matrix)",params);
////      model4 = job.trainModel().get();
////      HashMap<String, Double> coefs3 = model3.coefficients();
////      HashMap<String, Double> coefs4 = model4.coefficients();
////      // compare against each other
////      for(String s:coefs4.keySet()) {
////        String s1 = s;
////        if(s.startsWith("Origin"))
////          s1 = "Origin." + s.substring(6);
////        if(s.startsWith("Dest"))
////          s1 = "Dest." + s.substring(4);
////        if(s.startsWith("UniqueCarrier"))
////          s1 = "UniqueCarrier." + s.substring(13);
////        assertEquals("coeff " + s1 + " differs, " + coefs3.get(s1) + " != " + coefs4.get(s), coefs3.get(s1), coefs4.get(s),1e-4);
////      }
//
//    } finally {
//      fr.delete();
//      frMM.delete();
//      if(res != null)res.delete();
//      if(model1 != null)model1.delete();
//      if(model2 != null)model2.delete();
//      if(model3 != null)model3.delete();
//      if(model4 != null)model4.delete();
////      if(score != null)score.delete();
//      if( job != null ) job.remove();
//      Scope.exit();
//    }
//  }

  /**
   * Test we get correct gram on dataset which contains categoricals and sparse and dense numbers
   */
  @Test
  public void testSparseGramComputation() {
    Random rnd = new Random(123456789l);
    double[] d0 = MemoryManager.malloc8d(1000);
    double[] d1 = MemoryManager.malloc8d(1000);
    double[] d2 = MemoryManager.malloc8d(1000);
    double[] d3 = MemoryManager.malloc8d(1000);
    double[] d4 = MemoryManager.malloc8d(1000);
    double[] d5 = MemoryManager.malloc8d(1000);
    double[] d6 = MemoryManager.malloc8d(1000);
    double[] d7 = MemoryManager.malloc8d(1000);
    double[] d8 = MemoryManager.malloc8d(1000);
    double[] d9 = MemoryManager.malloc8d(1000);

    int[] c1 = MemoryManager.malloc4(1000);
    int[] c2 = MemoryManager.malloc4(1000);
    String[] dom = new String[]{"a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"};
    for (int i = 0; i < d1.length; ++i) {
      c1[i] = rnd.nextInt(dom.length);
      c2[i] = rnd.nextInt(dom.length);
      d0[i] = rnd.nextDouble();
      d1[i] = rnd.nextDouble();
    }
    for (int i = 0; i < 30; ++i) {
      d2[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d3[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d4[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d5[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d6[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d7[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d8[rnd.nextInt(d2.length)] = rnd.nextDouble();
      d9[rnd.nextInt(d2.length)] = 1;
    }

    Vec v01 = Vec.makeVec(c1, dom, Vec.newKey());
    Vec v02 = Vec.makeVec(c2, dom, Vec.newKey());
    Vec v03 = Vec.makeVec(d0, Vec.newKey());
    Vec v04 = Vec.makeVec(d1, Vec.newKey());
    Vec v05 = Vec.makeVec(d2, Vec.newKey());
    Vec v06 = Vec.makeVec(d3, Vec.newKey());
    Vec v07 = Vec.makeVec(d4, Vec.newKey());
    Vec v08 = Vec.makeVec(d5, Vec.newKey());
    Vec v09 = Vec.makeVec(d6, Vec.newKey());
    Vec v10 = Vec.makeVec(d7, Vec.newKey());
    Vec v11 = Vec.makeVec(d8, Vec.newKey());
    Vec v12 = Vec.makeVec(d9, Vec.newKey());

    Key k = Key.make("TestData");
    Frame f = new Frame(v01,v02,v03,v04,v05,v05,v06,v07,v08,v09,v10,v11,v12);
    DKV.put(k,f);
    DataInfo dinfo = new DataInfo(Key.make(),f, null, 1, true, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false, false, false);
    GLMParameters params = new GLMParameters(Family.gaussian);
    final GLMIterationTask glmtSparse = new GLMIterationTask(null, dinfo, 1e-5, params, false, null, 0, null, null).setSparse(true).doAll(dinfo._adaptedFrame);
    final GLMIterationTask glmtDense = new GLMIterationTask(null, dinfo, 1e-5, params, false, null, 0, null, null).setSparse(false).doAll(dinfo._adaptedFrame);
    for (int i = 0; i < glmtDense._xy.length; ++i) {
      for (int j = 0; j <= i; ++j) {
        assertEquals(glmtDense._gram.get(i, j), glmtSparse._gram.get(i, j), 1e-8);
      }
      assertEquals(glmtDense._xy[i], glmtSparse._xy[i], 1e-8);
    }
    final double[] beta = MemoryManager.malloc8d(dinfo.fullN() + 1);
    // now do the same but wieghted, use LSM solution as beta to generate meaningfull weights
    H2O.submitTask(new H2OCountedCompleter() {
      @Override
      protected void compute2() {
        new GLM.GramSolver(glmtDense._gram, glmtDense._xy, true, 1e-5, 0, null, null, 0, null, null).solve(null, beta);
        tryComplete();
      }
    }).join();
    final GLMIterationTask glmtSparse2 = new GLMIterationTask(null, dinfo, 1e-5, params, false, beta, 0, null, null).setSparse(true).doAll(dinfo._adaptedFrame);
    final GLMIterationTask glmtDense2 = new GLMIterationTask(null, dinfo, 1e-5, params, false, beta, 0, null, null).setSparse(false).doAll(dinfo._adaptedFrame);
    for (int i = 0; i < glmtDense2._xy.length; ++i) {
      for (int j = 0; j <= i; ++j) {
        assertEquals(glmtDense2._gram.get(i, j), glmtSparse2._gram.get(i, j), 1e-8);
      }
      assertEquals(glmtDense2._xy[i], glmtSparse2._xy[i], 1e-8);
    }
    dinfo.remove();
    DKV.remove(k);
    f.remove();
  }

  // test categorical autoexpansions, run on airlines which has several categorical columns,
  // once on explicitly expanded data, once on h2o autoexpanded and compare the results
  @Test
  public void testAirlines() {
    GLM job = null;
    GLMModel model1 = null, model2 = null, model3 = null, model4 = null;
    Frame frMM = parse_test_file(Key.make("AirlinesMM"), "smalldata/airlines/AirlinesTrainMM.csv.zip");
    Frame frG = parse_test_file(Key.make("gram"), "smalldata/airlines/gram_std.csv", true);
    Vec xy = frG.remove("xy");
    frMM.remove("").remove();
    frMM.add("IsDepDelayed", frMM.remove("IsDepDelayed"));
    DKV.put(frMM._key, frMM);
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"), res = null;
    //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"fYear", "fMonth", "fDayofMonth", "fDayOfWeek", "DepTime", "ArrTime", "IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._standardize = false;
      job = new GLM(Key.make("airlines_cat_nostd"), "Airlines with auto-expanded categoricals, no standardization", params);
      model1 = job.trainModel().get();
      Frame score1 = model1.score(fr);
      ModelMetricsRegressionGLM mm = (ModelMetricsRegressionGLM) ModelMetrics.getFromDKV(model1, fr);
      Assert.assertEquals(((ModelMetricsRegressionGLM) model1._output._training_metrics)._resDev, mm._resDev, 1e-4);
      Assert.assertEquals(((ModelMetricsRegressionGLM) model1._output._training_metrics)._resDev, mm._MSE * score1.numRows(), 1e-4);
      mm.remove();
      res = model1.score(fr);
      // Build a POJO, validate same results
      Assert.assertTrue(model1.testJavaScoring(fr, res, 1e-15));
      params._train = frMM._key;
      params._ignored_columns = new String[]{"X"};
      job = new GLM(Key.make("airlines_mm"), "Airlines with pre-expanded (mode.matrix) categoricals, no standardization", params);
      model2 = job.trainModel().get();
      params._standardize = true;
      params._train = frMM._key;
      params._use_all_factor_levels = true;
      // test the gram
      DataInfo dinfo = new DataInfo(Key.make(),frMM, null, 1, true, DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.NONE, true, false, false, false);
      GLMIterationTask glmt = new GLMIterationTask(null,dinfo,1e-5,params,false,null,0,null, null).doAll(dinfo._adaptedFrame);
      for(int i = 0; i < glmt._xy.length; ++i) {
        for(int j = 0; j <= i; ++j ) {
          assertEquals(frG.vec(j).at(i), glmt._gram.get(i, j), 1e-5);
        }
        assertEquals(xy.at(i), glmt._xy[i], 1e-5);
      }
      xy.remove();
      params = (GLMParameters) params.clone();
      params._standardize = true;
      params._family = Family.binomial;
      params._link = Link.logit;
      job = new GLM(Key.make("airlines_mm"), "Airlines with pre-expanded (mode.matrix) categoricals, no standardization", params);
      model3 = job.trainModel().get();
      params._train = fr._key;
      params._ignored_columns = ignoredCols;
      job = new GLM(Key.make("airlines_mm"), "Airlines with pre-expanded (mode.matrix) categoricals, no standardization", params);
      model4 = job.trainModel().get();
      assertEquals(nullDeviance(model3), nullDeviance(model4), 1e-4);
      assertEquals(residualDeviance(model4), residualDeviance(model3), nullDeviance(model3) * 1e-3);
      HashMap<String, Double> coefs1 = model1.coefficients();
      HashMap<String, Double> coefs2 = model2.coefficients();
//      GLMValidation val1 = model1.validation();
//      GLMValidation val2 = model2.validation();
      // compare against each other
      for (String s : coefs2.keySet()) {
        String s1 = s;
        if (s.startsWith("Origin"))
          s1 = "Origin." + s.substring(6);
        if (s.startsWith("Dest"))
          s1 = "Dest." + s.substring(4);
        if (s.startsWith("UniqueCarrier"))
          s1 = "UniqueCarrier." + s.substring(13);
        assertEquals("coeff " + s1 + " differs, " + coefs1.get(s1) + " != " + coefs2.get(s), coefs1.get(s1), coefs2.get(s), 1e-4);
        DKV.put(frMM._key, frMM); // update the frame in the KV after removing the vec!
      }
      assertEquals(nullDeviance(model1), nullDeviance(model2), 1e-4);
      assertEquals(residualDeviance(model1), residualDeviance(model2), 1e-4);
//      assertEquals(val1.aic, val2.aic,1e-2);
      // compare result against glmnet
      assertEquals(5336.918, residualDeviance(model1), 1);
      assertEquals(6051.613, nullDeviance(model2), 1);


      // lbfgs
//      params._solver = Solver.L_BFGS;
//      params._train = fr._key;
//      params._lambda = new double[]{.3};
//      job = new GLM(Key.make("lbfgs_cat"),"lbfgs glm built over categorical columns",params);
//      model3 = job.trainModel().get();
//      params._train = frMM._key;
//      job = new GLM(Key.make("lbfgs_mm"),"lbfgs glm built over pre-expanded categoricals (model.matrix)",params);
//      model4 = job.trainModel().get();
//      HashMap<String, Double> coefs3 = model3.coefficients();
//      HashMap<String, Double> coefs4 = model4.coefficients();
//      // compare against each other
//      for(String s:coefs4.keySet()) {
//        String s1 = s;
//        if(s.startsWith("Origin"))
//          s1 = "Origin." + s.substring(6);
//        if(s.startsWith("Dest"))
//          s1 = "Dest." + s.substring(4);
//        if(s.startsWith("UniqueCarrier"))
//          s1 = "UniqueCarrier." + s.substring(13);
//        assertEquals("coeff " + s1 + " differs, " + coefs3.get(s1) + " != " + coefs4.get(s), coefs3.get(s1), coefs4.get(s),1e-4);
//      }

    } finally {
      fr.delete();
      frMM.delete();
      frG.delete();

      if (res != null) res.delete();
      if (model1 != null) model1.delete();
      if (model2 != null) model2.delete();
      if (model3 != null) model3.delete();
      if (model4 != null) model4.delete();
//      if(score != null)score.delete();
      if (job != null) job.remove();
      Scope.exit();
    }
  }


  // test categorical autoexpansions, run on airlines which has several categorical columns,
  // once on explicitly expanded data, once on h2o autoexpanded and compare the results
  @Test
  public void test_COD_Airlines_SingleLambda() {
    GLM job = null;
    GLMModel model1 = null;
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"); //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._valid = fr._key;
      params._lambda = new double[] {0.01};//null; //new double[]{0.02934};//{0.02934494}; // null;
      params._alpha = new double[]{1};
      params._standardize = false;
      params._solver = Solver.COORDINATE_DESCENT; //Solver.COORDINATE_DESCENT;
      params._lambda_search = true;
      job = new GLM(Key.make("airlines_cat_nostd"), "Airlines with auto-expanded categorical variables, no standardization", params);
      model1 = job.trainModel().get();
      double [] beta = model1.beta();
      double l1pen = ArrayUtils.l1norm(beta,true);
      double l2pen = ArrayUtils.l2norm(beta,true);
      //System.out.println( " lambda min " + params._lambda[params._lambda.length-1] );
      //System.out.println( " lambda_max " + model1._lambda_max);
      //System.out.println( " objective value " + objective);
      //System.out.println(" intercept " + beta[beta.length-1]);
      double objective = job.likelihood()/model1._nobs +
              params._lambda[params._lambda.length-1]*params._alpha[0]*l1pen + params._lambda[params._lambda.length-1]*(1-params._alpha[0])*l2pen/2  ;
      assertEquals(0.670921, objective,1e-4);
    } finally {
      fr.delete();
      if (model1 != null) model1.delete();
      if (job != null) job.remove();
    }
  }


  @Test
  public void test_COD_Airlines_LambdaSearch() {
    GLM job = null;
    GLMModel model1 = null;
    Frame fr = parse_test_file(Key.make("Airlines"), "smalldata/airlines/AirlinesTrain.csv.zip"); //  Distance + Origin + Dest + UniqueCarrier
    String[] ignoredCols = new String[]{"IsDepDelayed_REC"};
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "IsDepDelayed";
      params._ignored_columns = ignoredCols;
      params._train = fr._key;
      params._valid = fr._key;
      params._lambda = null; // new double [] {0.25};
      params._alpha = new double[]{1};
      params._standardize = false;
      params._solver = Solver.COORDINATE_DESCENT;
      params._lambda_search = true;
      job = new GLM(Key.make("airlines_cat_nostd"), "Airlines with auto-expanded categorical variables, no standardization", params);
      model1 = job.trainModel().get();
      GLMModel.Submodel sm = model1._output._submodels[model1._output._submodels.length-1];
      double [] beta = sm.beta;
      System.out.println("lambda " + sm.lambda_value);
      double l1pen = ArrayUtils.l1norm(beta,true);
      double l2pen = ArrayUtils.l2norm(beta,true);
      double objective = job.likelihood()/model1._nobs + // gives likelihood of the last lambda
              params._lambda[params._lambda.length-1]*params._alpha[0]*l1pen + params._lambda[params._lambda.length-1]*(1-params._alpha[0])*l2pen/2  ;
      assertEquals(0.65689, objective,1e-4);
    } finally {
      fr.delete();
      if (model1 != null) model1.delete();
      if (job != null) job.remove();
    }
  }



  @Test
  public void testYmuTsk() {

  }

  public static double residualDeviance(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._resDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._resDev;
    }
  }
  public static double residualDevianceTest(GLMModel m) {
    if(m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM)m._output._validation_metrics;
      return metrics._resDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM)m._output._validation_metrics;
      return metrics._resDev;
    }
  }
  public static double nullDevianceTest(GLMModel m) {
    if(m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM)m._output._validation_metrics;
      return metrics._nullDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM)m._output._validation_metrics;
      return metrics._nullDev;
    }
  }
  public static double aic(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._AIC;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._AIC;
    }
  }

  public static double nullDOF(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._nullDegressOfFreedom;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._nullDegressOfFreedom;
    }
  }

  public static double resDOF(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._residualDegressOfFreedom;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._residualDegressOfFreedom;
    }
  }

  public static double auc(GLMModel m) {
    ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
    return metrics.auc()._auc;
  }
  public static double logloss(GLMModel m) {
    ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
    return metrics._logloss;
  }

  public static double mse(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._MSE;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._MSE;
    }
  }

  public static double nullDeviance(GLMModel m) {
    if (m._parms._family == Family.binomial) {
      ModelMetricsBinomialGLM metrics = (ModelMetricsBinomialGLM) m._output._training_metrics;
      return metrics._nullDev;
    } else {
      ModelMetricsRegressionGLM metrics = (ModelMetricsRegressionGLM) m._output._training_metrics;
      return metrics._nullDev;
    }
  }

  // test class
  private static final class GLMIterationTaskTest extends GLMIterationTask {
    final GLMModel _m;
    GLMValidation _val2;

    public GLMIterationTaskTest(Key jobKey, DataInfo dinfo, double lambda, GLMParameters glm, boolean validate, double[] beta, double ymu, Vec rowFilter, GLMModel m) {
      super(jobKey, dinfo, lambda, glm, validate, beta, ymu, rowFilter, null);
      _m = m;
    }

    public void map(Chunk[] chks) {
      super.map(chks);

      _val2 = (GLMValidation) _m.makeMetricBuilder(chks[chks.length - 1].vec().domain());
      double[] ds = new double[3];

      float[] actual = new float[1];
      for (int i = 0; i < chks[0]._len; ++i) {
        _m.score0(chks, i, null, ds);
        actual[0] = (float) chks[chks.length - 1].atd(i);
        _val2.perRow(ds, actual, _m);
      }
    }

    public void reduce(GLMIterationTask gmt) {
      super.reduce(gmt);
      GLMIterationTaskTest g = (GLMIterationTaskTest) gmt;
      _val2.reduce(g._val2);
    }

    @Override
    public void postGlobal() {
      System.out.println("val1 = " + _val.toString());
      System.out.println("val2 = " + _val2.toString());
      ModelMetrics mm1 = _val.makeModelMetrics(_m, _dinfo._adaptedFrame, Double.NaN);
      ModelMetrics mm2 = _val2.makeModelMetrics(_m, _dinfo._adaptedFrame, Double.NaN);
      System.out.println("mm1 = " + mm1.toString());
      System.out.println("mm2 = " + mm2.toString());
      assert mm1.equals(mm2);
    }
  }

  @Test
  public void makeModel() {
    Frame fr = parse_test_file("smalldata/logreg/prostate.csv");
    Frame score = null;
//    Call:  glm(formula = CAPSULE ~ . - ID, family = binomial, data = D)
//
//    Coefficients:
//    (Intercept)          AGE         RACE        DPROS        DCAPS          PSA
//    -7.27968     -0.01213     -0.62424      0.55661      0.48375      0.02739
//    VOL      GLEASON
//    -0.01124      0.97632
//
//    Degrees of Freedom: 379 Total (i.e. Null);  372 Residual
//    Null Deviance:	    512.3
//    Residual Deviance:  378.6 	AIC: 394.6
    String[] cfs1 = new String[]{"AGE", "RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"}; // Intercept
    double[] vals = new double[]{-0.01213, -0.62424, 0.55661, 0.48375, 0.02739, -0.01124, 0.97632, -7.27968};
    GLMModel m = GLMModel.makeGLMModel(Family.binomial, vals, cfs1, "CAPSULE");
    try {
      score = m.score(fr);
      ModelMetricsBinomialGLM mm = (ModelMetricsBinomialGLM)ModelMetrics.getFromDKV(m,fr);
      assertEquals(378.6,mm._resDev,1e-1); // null deviance differs as makeGLMModel does not take response mean / prior
    } finally {
      if(fr != null) fr.delete();
      if(score != null) score.delete();
      if(m != null)m.delete();
    }
  }

  /**
   * Simple test for binomial family (no regularization, test both lsm solvers).
   * Runs the classical prostate, using dataset with race replaced by categoricals (probably as it's supposed to be?), in any case,
   * it gets to test correct processing of categoricals.
   *
   * Compare against the results from standard R glm implementation.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test public void testProstate() throws InterruptedException, ExecutionException {
    GLM job = null;
    GLMModel model = null, model2 = null, model3 = null, model4 = null;
    Frame fr = parse_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
    Frame score = null;
    try{
      Scope.enter();
      // R results
//      Coefficients:
//        (Intercept)           ID          AGE       RACER2       RACER3        DPROS        DCAPS          PSA          VOL      GLEASON
//          -8.894088     0.001588    -0.009589     0.231777    -0.459937     0.556231     0.556395     0.027854    -0.011355     1.010179
      String [] cfs1 = new String [] {"Intercept","AGE", "RACE.R2","RACE.R3", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
      double [] vals = new double [] {-8.14867, -0.01368, 0.32337, -0.38028, 0.55964, 0.49548, 0.02794, -0.01104, 0.97704};
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "CAPSULE";
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._standardize = false;
      job = new GLM(Key.make("prostate_model"),"glm test simple poisson",params);
      model = job.trainModel().get();

      HashMap<String, Double> coefs = model.coefficients();
      for(int i = 0; i < cfs1.length; ++i)
        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
      assertEquals(512.3, nullDeviance(model),1e-1);
      assertEquals(378.3, residualDeviance(model),1e-1);
      assertEquals(371,   resDOF(model),0);
      assertEquals(396.3, aic(model),1e-1);
      model.delete();
      // test scoring
      score = model.score(fr);
      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);
      hex.AUC2 adata = mm._auc;
      assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
      assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
      assertEquals(((ModelMetricsBinomialGLM)model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm)._resDev, 1e-8);
      Frame score1 = model.score(fr);
      mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);
      assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
      assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
      assertEquals(((ModelMetricsBinomialGLM)model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm)._resDev, 1e-8);
      double prior = 1e-5;
      params._prior = prior;
      job.remove();
      // test the same data and model with prior, should get the same model except for the intercept
      job = new GLM(Key.make("prostate_model2"),"glm test simple poisson",params);
      model2 = job.trainModel().get();

      for(int i = 0; i < model2.beta().length-1; ++i)
        assertEquals(model.beta()[i], model2.beta()[i], 1e-8);
      assertEquals(model.beta()[model.beta().length-1] -Math.log(model._ymu * (1-prior)/(prior * (1-model._ymu))),model2.beta()[model.beta().length-1],1e-10);

      // run with lambda search, check the final submodel
      params._lambda_search = true;
      params._lambda = null;
      params._alpha = new double[]{0};
      params._prior = -1;
      params._max_iterations = 500;
      job.remove();
      // test the same data and model with prior, should get the same model except for the intercept
      job = new GLM(Key.make("prostate_model2"),"glm test simple poisson",params);
      model3 = job.trainModel().get();
      double lambda =  model3._output._submodels[model3._output._best_lambda_idx].lambda_value;
      params._lambda_search = false;
      params._lambda = new double[]{lambda};
      job.remove();
      ModelMetrics mm3 = ModelMetrics.getFromDKV(model3,fr);
      assertEquals("mse don't match, " + model3._output._training_metrics._MSE + " != " + mm3._MSE,model3._output._training_metrics._MSE,mm3._MSE,1e-8);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev + " != " + ((ModelMetricsBinomialGLM)mm3)._resDev,((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm3)._resDev,1e-4);
      fr.add("CAPSULE", fr.remove("CAPSULE"));
      fr.remove("ID").remove();
      DKV.put(fr._key,fr);
      DataInfo dinfo = new DataInfo(Key.make(),fr, null, 1, true, TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false);
      new GLMIterationTaskTest(null,dinfo,1,params,true,model3.beta(),model3._ymu,null,model3).doAll(dinfo._adaptedFrame);
      score = model3.score(fr);
      mm3 = ModelMetrics.getFromDKV(model3,fr);

      assertEquals("mse don't match, " + model3._output._training_metrics._MSE + " != " + mm3._MSE,model3._output._training_metrics._MSE,mm3._MSE,1e-8);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev + " != " + ((ModelMetricsBinomialGLM)mm3)._resDev,((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm3)._resDev,1e-4);


      // test the same data and model with prior, should get the same model except for the intercept
      job = new GLM(Key.make("prostate_model2"),"glm test simple poisson",params);
      model4 = job.trainModel().get();
      assertEquals("mse don't match, " + model3._output._training_metrics._MSE + " != " + model4._output._training_metrics._MSE,model3._output._training_metrics._MSE,model4._output._training_metrics._MSE,1e-8);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev + " != " + ((ModelMetricsBinomialGLM)model4._output._training_metrics)._resDev,((ModelMetricsBinomialGLM)model3._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)model4._output._training_metrics)._resDev,1e-4);
      Frame fscore4 = model4.score(fr);
      ModelMetrics mm4 = ModelMetrics.getFromDKV(model4,fr);
      fscore4.delete();
      assertEquals("mse don't match, " + mm3._MSE + " != " + mm4._MSE,mm3._MSE,mm4._MSE,1e-8);
      assertEquals("res-devs don't match, " + ((ModelMetricsBinomialGLM)mm3)._resDev + " != " + ((ModelMetricsBinomialGLM)mm4)._resDev,((ModelMetricsBinomialGLM)mm3)._resDev, ((ModelMetricsBinomialGLM)mm4)._resDev,1e-4);
//      GLMValidation val2 = new GLMValidationTsk(params,model._ymu,rank(model.beta())).doAll(new Vec[]{fr.vec("CAPSULE"),score.vec("1")})._val;
//      assertEquals(val.residualDeviance(),val2.residualDeviance(),1e-6);
//      assertEquals(val.nullDeviance(),val2.nullDeviance(),1e-6);
    } finally {
      fr.delete();
      if(model != null)model.delete();
      if(model2 != null)model2.delete();
      if(model3 != null)model3.delete();
      if(model4 != null)model4.delete();
      if(score != null)score.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }

  @Test public void testSynthetic() throws Exception {
    GLM job = null;
    GLMModel model = null;
    Frame fr = parse_test_file("smalldata/glm_test/glm_test2.csv");
    Frame score = null;
    try {
      Scope.enter();
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response_column = "response";
      // params._response = fr.find(params._response_column);
      params._ignored_columns = new String[]{"ID"};
      params._train = fr._key;
      params._lambda = new double[]{0};
      params._standardize = false;
      params._max_iterations = 20;
      job = new GLM(Key.make("glm_model"), "glm test simple poisson", params);
      model = job.trainModel().get();
      double [] beta = model.beta();
      System.out.println("beta = " + Arrays.toString(beta));
      assertEquals(auc(model), 1, 1e-4);
      score = model.score(fr);

      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model,fr);

      hex.AUC2 adata = mm._auc;
      assertEquals(auc(model), adata._auc, 1e-2);
    } finally {
      fr.remove();
      if(model != null)model.delete();
      if(score != null)score.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }


  /**
   * Test strong rules on arcene datasets (10k predictors, 100 rows).
   * Should be able to obtain good model (~100 predictors, ~1 explained deviance) with up to 250 active predictors.
   * Scaled down (higher lambda min, fewer lambdas) to run at reasonable speed (whole test takes 20s on my laptop).
   *
   * Test runs glm with gaussian on arcene dataset and verifies it gets all lambda while limiting maximum actove predictors to reasonably small number.
   * Compares the objective value to expected one.
   */
  @Test public void testArcene() throws InterruptedException, ExecutionException{
    GLM job = null;
    Key parsed = Key.make("arcene_parsed");
    Key modelKey = Key.make("arcene_model");
    GLMModel model = null;
    Frame fr = parse_test_file(parsed, "smalldata/glm_test/arcene.csv");
    try{
      Scope.enter();
      // test LBFGS with l1 pen
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._solver = Solver.COORDINATE_DESCENT;
      params._response_column = fr._names[0];
      params._train = parsed;
      params._alpha = new double[]{0};

      job = new GLM(modelKey, "glm test simple poisson", params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      model.delete();
      params = new GLMParameters(Family.gaussian);
      // params._response = 0;
      params._lambda = null;
      params._response_column = fr._names[0];
      params._train = parsed;
      params._lambda_search = true;
      params._nlambdas = 35;
      params._lambda_min_ratio = 0.18;
      params._max_iterations = 100000;
      params._max_active_predictors = 215;
      params._alpha = new double[]{1};
      for(Solver s: new Solver[]{ Solver.IRLSM}){//Solver.COORDINATE_DESCENT,}) { // LBFGS lambda-search is too slow now
        params._solver = s;
        job = new GLM(modelKey, "glm test simple poisson", params);
        job.trainModel().get();
        model = DKV.get(modelKey).get();
        // assert on that we got all submodels (if strong rules work, we should be able to get the results with this many active predictors)
        assertEquals(params._nlambdas, model._output._submodels.length);
        // assert on the quality of the result, technically should compare objective value, but this should be good enough for now
        job.remove();
      }

      // test behavior when we can not fit within the active cols limit (should just bail out early and give us whatever it got)
      params = new GLMParameters(Family.gaussian);
      // params._response = 0;
      params._lambda = null;
      params._response_column = fr._names[0];
      params._train = parsed;
      params._lambda_search = true;
      params._nlambdas = 35;
      params._lambda_min_ratio = 0.18;
      params._max_active_predictors = 20;
      params._alpha = new double[]{1};
      job = new GLM(modelKey,"glm test simple poisson",params);
      job.trainModel().get();
      model = DKV.get(modelKey).get();
      assertTrue(model._output._submodels.length > 3);
      assertTrue(residualDeviance(model) <= 93);
    } finally {
      fr.delete();
      if(model != null)model.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }

}
