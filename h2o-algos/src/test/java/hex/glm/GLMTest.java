package hex.glm;

import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GetScoringModelTask;
import hex.glm.GLMModel.Submodel;
import hex.glm.GLMTask.GLMIterationTask;
import hex.utils.MSETsk;
import junit.framework.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.api.AUC;
import water.api.AUCData;
import water.fvec.FVecTest;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.ParseDataset2;
import water.util.ModelUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GLMTest  extends TestUtil {
  @BeforeClass public static void setup() { stall_till_cloudsize(1);   jobKey = Key.make("job");}
  static Key jobKey;

  //------------------- simple tests on synthetic data------------------------------------
  @Test
  public void testGaussianRegression() throws InterruptedException, ExecutionException {
    Key raw = Key.make("gaussian_test_data_raw");
    Key parsed = Key.make("gaussian_test_data_parsed");
    Key modelKey = Key.make("gaussian_test");
    GLMModel model = null;
    Frame fr = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9");
      fr = ParseDataset2.parse(parsed, new Key[]{raw});
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._training_frame = fr._key;
      params._response = 1;
      params.lambda = new double[]{0};
      new GLM(jobKey,modelKey,"glm test simple gaussian",params).train().get();
      model = DKV.get(modelKey).get();
      HashMap<String, Double> coefs = model.coefficients();
      assertEquals(0.0,coefs.get("Intercept"),1e-4);
      assertEquals(0.1,coefs.get("x"),1e-4);
    }finally{
      if( fr != null ) fr.remove();
      if(model != null)model.remove();
      DKV.remove(jobKey);
    }
  }

  /**
   * Test Poisson regression on simple and small synthetic dataset.
   * Equation is: y = exp(x+1);
   */
  @Test public void testPoissonRegression() throws InterruptedException, ExecutionException {
    Key raw = Key.make("poisson_test_data_raw");
    Key parsed = Key.make("poisson_test_data_parsed");
    Key modelKey = Key.make("poisson_test");
    GLMModel model = null;
    Frame fr = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      FVecTest.makeByteVec(raw, "x,y\n0,2\n1,4\n2,8\n3,16\n4,32\n5,64\n6,128\n7,256");
      fr = ParseDataset2.parse(parsed, new Key[]{raw});
      Vec v = fr.vec(0);
      System.out.println(v.min() + ", " + v.max()  + ", mean = " + v.mean());
      GLMParameters params = new GLMParameters(Family.poisson);
      params._training_frame = fr._key;
      params._response = 1;
      params.lambda = new double[]{0};
      params.higher_accuracy = true;
      params._standardize = false;
      new GLM(jobKey,modelKey,"glm test simple poisson",params).train().get();
      model = DKV.get(modelKey).get();
      for(double c:model.beta())assertEquals(Math.log(2),c,1e-2); // only 1e-2 precision cause the perfect solution is too perfect -> will trigger grid search
      // Test 2, example from http://www.biostat.umn.edu/~dipankar/bmtry711.11/lecture_13.pdf
      model.delete();
      fr.delete();
      FVecTest.makeByteVec(raw, "x,y\n1,0\n2,1\n3,2\n4,3\n5,1\n6,4\n7,9\n8,18\n9,23\n10,31\n11,20\n12,25\n13,37\n14,45\n");
      fr = ParseDataset2.parse(parsed, new Key[]{raw});
      params._training_frame = fr._key;
      params.higher_accuracy = true;
      params._standardize = false;
      new GLM(jobKey,modelKey,"glm test simple poisson",params).train().get();
      model = DKV.get(modelKey).get();
      assertEquals(0.3396,model.beta()[1],1e-4);
      assertEquals(0.2565,model.beta()[0],1e-4);
    }finally{
      if( fr != null ) fr.delete();
      if(model != null)model.delete();
      DKV.remove(jobKey);
    }
  }


  /**
   * Test Gamma regression on simple and small synthetic dataset.
   * Equation is: y = 1/(x+1);
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test public void testGammaRegression() throws InterruptedException, ExecutionException {
    GLMModel model = null;
    Frame fr = null;
    try {
      // make data so that the expected coefficients is icept = col[0] = 1.0
      Key raw = Key.make("gamma_test_data_raw");
      Key parsed = Key.make("gamma_test_data_parsed");
      FVecTest.makeByteVec(raw, "x,y\n0,1\n1,0.5\n2,0.3333333\n3,0.25\n4,0.2\n5,0.1666667\n6,0.1428571\n7,0.125");
      fr = ParseDataset2.parse(parsed, new Key[]{raw});
//      /public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
      double [] vals = new double[] {1.0,1.0};
      //public GLM2(String desc, Key dest, Frame src, Family family, Link link, double alpha, double lambda) {
      GLMParameters params = new GLMParameters(Family.gamma);
      params._response = 1;
      params._training_frame = parsed;
      params.lambda = new double[]{0};
      Key modelKey = Key.make("gamma_test");
      new GLM(jobKey,modelKey,"glm test simple gamma",params).train().get();
      model = DKV.get(modelKey).get();
      for(double c:model.beta())assertEquals(1.0, c,1e-4);
    }finally{
      if( fr != null ) fr.delete();
      if(model != null)model.delete();
      DKV.remove(jobKey);
    }
  }

//  //simple tweedie test
//  @Test public void testTweedieRegression() throws InterruptedException, ExecutionException{
//    Key raw = Key.make("gaussian_test_data_raw");
//    Key parsed = Key.make("gaussian_test_data_parsed");
//    Key modelKey = Key.make("gaussian_test");
//    Frame fr = null;
//    GLMModel model = null;
//    try {
//      // make data so that the expected coefficients is icept = col[0] = 1.0
//      FVecTest.makeByteVec(raw, "x,y\n0,0\n1,0.1\n2,0.2\n3,0.3\n4,0.4\n5,0.5\n6,0.6\n7,0.7\n8,0.8\n9,0.9\n0,0\n1,0\n2,0\n3,0\n4,0\n5,0\n6,0\n7,0\n8,0\n9,0");
//      fr = ParseDataset2.parse(parsed, new Key[]{raw});
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

  //------------ TEST on selected files form small data and compare to R results ------------------------------------
  /**
   * Simple test for poisson, gamma and gaussian families (no regularization, test both lsm solvers).
   * Basically tries to predict horse power based on other parameters of the cars in the dataset.
   * Compare against the results from standard R glm implementation.
   * @throws ExecutionException
   * @throws InterruptedException
   */
  @Test public void testCars() throws InterruptedException, ExecutionException{
    Key parsed = Key.make("cars_parsed");
    Key modelKey = Key.make("cars_model");
    Frame fr = null;
    GLMModel model = null;
    try {
      fr = parse_test_file(parsed, "smalldata/junit/cars.csv");
      GLMParameters params = new GLMParameters(Family.poisson, Family.poisson.defaultLink, new double[]{0}, new double[]{0});
      params._response = fr.find("power (hp)");
      params._ignored_cols = new int[]{fr.find("name")};
      params._training_frame = parsed;
      params.lambda = new double[]{0};
      new GLM(jobKey, modelKey, "glm test simple poisson", params).train().get();
      model = DKV.get(modelKey).get();
      HashMap<String, Double> coefs = model.coefficients();
      String[] cfs1 = new String[]{"Intercept", "economy (mpg)", "cylinders", "displacement (cc)", "weight (lb)", "0-60 mph (s)", "year"};
      double[] vls1 = new double[]{4.9504805, -0.0095859, -0.0063046, 0.0004392, 0.0001762, -0.0469810, 0.0002891};
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls1[i], coefs.get(cfs1[i]), 1e-4);
      // test gamma
      double[] vls2 = new double[]{8.992e-03, 1.818e-04, -1.125e-04, 1.505e-06, -1.284e-06, 4.510e-04, -7.254e-05};
      model.delete();
      params = new GLMParameters(Family.gamma, Family.gamma.defaultLink, new double[]{0}, new double[]{0});
      params._response = fr.find("power (hp)");
      params._ignored_cols = new int[]{fr.find("name")};
      params._training_frame = parsed;
      params.lambda = new double[]{0};
      new GLM(jobKey, modelKey, "glm test simple poisson", params).train().get();
      model = DKV.get(modelKey).get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls2[i], coefs.get(cfs1[i]), 1e-4);
      model.delete();
      // test gaussian
      double[] vls3 = new double[]{166.95862, -0.00531, -2.46690, 0.12635, 0.02159, -4.66995, -0.85724};
      params = new GLMParameters(Family.gaussian);
      params._response = fr.find("power (hp)");
      params._ignored_cols = new int[]{fr.find("name")};
      params._training_frame = parsed;
      params.lambda = new double[]{0};
      new GLM(jobKey, modelKey, "glm test simple poisson", params).train().get();
      model = DKV.get(modelKey).get();
      coefs = model.coefficients();
      for (int i = 0; i < cfs1.length; ++i)
        assertEquals(vls3[i], coefs.get(cfs1[i]), 1e-4);
    } finally {
      if( fr != null ) fr.delete();
      if(model != null)model.delete();
      DKV.remove(jobKey);
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
    Key parsed = Key.make("prostate_parsed");
    Key modelKey = Key.make("prostate_model");
    GLMModel model = null;
    Frame fr = parse_test_file(parsed, "smalldata/glm_test/prostate_cat_replaced.csv");
    Frame score = null;
    try{
      // R results
//      Coefficients:
//        (Intercept)           ID          AGE       RACER2       RACER3        DPROS        DCAPS          PSA          VOL      GLEASON
//          -8.894088     0.001588    -0.009589     0.231777    -0.459937     0.556231     0.556395     0.027854    -0.011355     1.010179
      String [] cfs1 = new String [] {"Intercept","AGE", "RACE.R2","RACE.R3", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"};
      double [] vals = new double [] {-8.14867, -0.01368, 0.32337, -0.38028, 0.55964, 0.49548, 0.02794, -0.01104, 0.97704};
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response = fr.find("CAPSULE");
      params._ignored_cols = new int[]{fr.find("ID")};
      params._training_frame = parsed;
      params.lambda = new double[]{0};
      new GLM(jobKey,modelKey,"glm test simple poisson",params).train().get();
      model = DKV.get(modelKey).get();
      HashMap<String, Double> coefs = model.coefficients();
      for(int i = 0; i < cfs1.length; ++i)
        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
      GLMValidation val = model.validation();
      assertEquals(512.3, val.nullDeviance(),1e-1);
      assertEquals(378.3, val.residualDeviance(),1e-1);
      assertEquals(396.3, val.aic(),1e-1);
      score = model.score(fr);
      AUC auc = new AUC();
      auc.predict = score;
      auc.actual = fr;
      auc.vactual = fr.vec("CAPSULE");
      auc.vpredict = score.vec("1");
      auc.execImpl();
      AUCData adata = auc.data();
        assertEquals(val.auc(),adata.AUC(),1e-2);
    } finally {
      fr.delete();
      if(model != null)model.delete();
      if(score != null)score.delete();
      DKV.remove(jobKey);
    }
  }

  @Test public void testSynthetic() throws Exception {
    Key parsed = Key.make("glm_parsed");
    Key modelKey = Key.make("glm_model");
    GLMModel model = null;
    Frame fr = parse_test_file(parsed, "smalldata/glm_test/glm_test2.csv");
    try {
      GLMParameters params = new GLMParameters(Family.binomial);
      params._response = fr.find("response");
      params._ignored_cols = new int[]{fr.find("ID")};
      params._training_frame = parsed;
      params.lambda = new double[]{0};
      new GLM(jobKey, modelKey, "glm test simple poisson", params).train().get();
      model = DKV.get(modelKey).get();
      assertEquals(model.validation().auc(),1,1e-4);
      double [] beta = model.beta();
      for(double d:beta)
        assertTrue(Math.abs(d) < 16);
    } finally {
      fr.remove();
      if(model != null)model.delete();
      DKV.remove(jobKey);
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
    Key parsed = Key.make("arcene_parsed");
    Key modelKey = Key.make("arcene_model");
    GLMModel model = null;
    Frame fr = parse_test_file(parsed, "smalldata/glm_test/arcene.csv");
    try{
      GLMParameters params = new GLMParameters(Family.gaussian);
      params._response = 0;
      params._training_frame = parsed;
      params.lambda_search = true;
      params.nlambdas = 35;
      params.lambda_min_ratio = 0.18;
      params.maxActivePredictors = 200;
      params.alpha = new double[]{1};
      new GLM(jobKey,modelKey,"glm test simple poisson",params).train().get();
      model = DKV.get(modelKey).get();
      // assert on that we got all submodels (if strong rules work, we should be able to get the results with this many active predictors)
      assertEquals(params.nlambdas,model._output._submodels.length);
      GLMValidation val = model.validation();
      // assert on the quality of the result, technically should compare objective value, but this should be good enough for now
      model._output.setSubmodelIdx(model._output._submodels.length-1);
      Submodel sm = model._output._submodels[model._output._best_lambda_idx];
      double l1norm = 0;
      for(double d:sm.norm_beta) l1norm += Math.abs(d);
      double objval = sm.validation.residual_deviance / sm.validation.nobs + sm.lambda_value*l1norm;
      assertEquals(0.32922849120947384,objval,0.32922849120947384*1e-2);
      // test scoring on several submodels
      GLMModel m = new GetScoringModelTask(null,model._key,sm.lambda_value).invokeTask()._res;
      Frame score = m.score(fr);
      MSETsk mse = new MSETsk().doAll(score.anyVec(), fr.vec(m._output.responseName()));
      assertEquals(val.residualDeviance(),mse._resDev,1e-6);
      score.remove();
      // try scoring another model
      model._output.setSubmodelIdx(model._output._submodels.length>>1);
      sm = model._output._submodels[model._output._best_lambda_idx];
      val = model._output._submodels[model._output._best_lambda_idx].validation;
      m = new GetScoringModelTask(null,model._key,sm.lambda_value).invokeTask()._res;
      score = m.score(fr);
      mse = new MSETsk().doAll(score.anyVec(), fr.vec(m._output.responseName()));
      assertEquals(val.residualDeviance(),mse._resDev,1e-6);
      score.remove();
      // test behavior when we can not fit within the active cols limit (should just bail out early and give us whatever it got)
      params = new GLMParameters(Family.gaussian);
      params._response = 0;
      params._training_frame = parsed;
      params.lambda_search = true;
      params.nlambdas = 35;
      params.lambda_min_ratio = 0.18;
      params.maxActivePredictors = 20;
      params.alpha = new double[]{1};
      new GLM(jobKey,modelKey,"glm test simple poisson",params).train().get();
      model = DKV.get(modelKey).get();
      assertTrue(model._output._submodels.length > 3);
      assertTrue(model.validation().residualDeviance() <= 93);
    } finally {
      fr.delete();
      if(model != null)model.delete();
      DKV.remove(jobKey);
    }
  }
}
