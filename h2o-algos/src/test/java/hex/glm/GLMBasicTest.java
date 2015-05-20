package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;
import water.*;
import water.fvec.*;
import water.parser.ParseDataset;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 4/26/15.
 */
public class GLMBasicTest extends TestUtil {
  static Frame _prostate; // prostate_cat_replaced


  @Test
  public void testOffset() {
    GLM job = null;
    GLMModel model = null;
    Frame score = null;
    double [] offset = new double[] {
      -0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.4519851,-0.4519851,-0.3981391,-0.4519851, // 1
      -0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.4519851, // 2
      -0.3981391,+0.6931472,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 3
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 4
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,+0.6931472,-0.4519851,-0.3981391,-0.3981391,-0.3981391, // 5
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 6
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851, // 7
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 8
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 9
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 10
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 11
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391, // 12
      -0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 13
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 14
      -0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 15
      -0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 16
      -0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 17
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 18
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391, // 19
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391, // 20
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 21
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 22
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 23
      -0.4519851,-0.3981391,-0.3981391,-0.4519851,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851, // 24
      -0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.4519851,-0.3981391,-0.3981391, // 25
      -0.3981391,+0.6931472,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 26
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 27
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 28
      -0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 29
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 30
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 31
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391, // 32
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 33
      -0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 34
      -0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 35
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391, // 36
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.3981391,-0.3981391,-0.3981391, // 37
      -0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.3981391,-0.4519851,-0.4519851,-0.3981391,-0.4519851,-0.3981391  // 38
    };
    Vec v = _prostate.anyVec().makeZero();
    Vec.Writer vw = v.open();
    for(int i = 0; i < offset.length; ++i)
      vw.set(i,offset[i]);
    vw.close();
    Key fKey = Key.make("prostate_with_offset");
    Frame f = new Frame(fKey, new String[]{"offset"}, new Vec[]{v});
    f.add(_prostate.names(),_prostate.vecs());
    DKV.put(fKey,f);
//    _prostate.anyVec().m
//    Call:  glm(formula = CAPSULE ~ . - ID - RACE, family = binomial, data = D,
//      offset = p)
//
//    Coefficients:
//    (Intercept)          AGE        DPROS        DCAPS          PSA          VOL      GLEASON
//      -7.637276    -0.009604     0.542133     0.449798     0.024952    -0.012264     0.981099
//
//    Degrees of Freedom: 379 Total (i.e. Null);  373 Residual
//    Null Deviance:	    511.4
//    Residual Deviance: 380.8 	AIC: 394.8
    String [] cfs1 = new String [] {"AGE",     "DPROS",    "DCAPS",  "PSA",      "VOL",  "GLEASON"};
    double [] vals = new double [] {-0.009604, 0.542133,   0.449798, 0.024952, -0.012264, 0.981099};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID","RACE"};
    params._train = fKey;
    params._offset_column = "offset";
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._max_iterations = 100; // not expected to reach max iterations here
    try {
      for (Solver s : new Solver[]{Solver.AUTO, Solver.IRLSM, Solver.L_BFGS}) {
        try {
          params._solver = s;
          System.out.println("SOLVER = " + s);
          job = new GLM(Key.make("prostate_model"), "glm test simple poisson", params);
          model = job.trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs.toString());
          System.out.println("metrics = " + model._output._training_metrics);
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[i], coefs.get(cfs1[i]), 1e-4);
          assertEquals(511.4, GLMTest.nullDeviance(model), 1e-1);
          assertEquals(380.8, GLMTest.residualDeviance(model), 1e-1);
          assertEquals(379, GLMTest.nullDOF(model), 0);
          assertEquals(373, GLMTest.resDOF(model), 0);
          assertEquals(394.8, GLMTest.aic(model), 1e-1);
          // test scoring
          try {
            score = model.score(_prostate); // todo if I use wrong frame, silently ignored
            assertTrue("shoul've thrown IAE", false);
          } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Test dataset is missing offset vector"));
          }
          score = model.score(f); // todo if I use wrong frame, silently ignored
          hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, f);
          hex.AUC2 adata = mm._auc;
          assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
          Frame score1 = model.score(f);
          score1.remove();
          mm = hex.ModelMetricsBinomial.getFromDKV(model, f);
          assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
          assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
          assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
        } finally {
          if (model != null) model.delete();
          if (score != null) score.delete();
          if (job != null) job.remove();
        }
      }
    } finally {
      if (f != null) f.delete();
    }
  }


  @Test
  public void testNoIntercept() {
    GLM job = null;
    GLMModel model = null;
    Frame score = null;
//        Call:  glm(formula = CAPSULE ~ . - 1, family = binomial, data = D)
//
//      Coefficients:
//      ID        AGE        RACER1     RACER2     RACER3      DPROS      DCAPS      PSA        VOL        GLEASON
//      0.001588  -0.009589  -8.894088  -8.662311  -9.354025   0.556231   0.556395   0.027854  -0.011355   1.010179
//
//      Degrees of Freedom: 380 Total (i.e. Null);  370 Residual
//      Null Deviance:	    526.8
//      Residual Deviance: 376.6 	AIC: 396.6
    String [] cfs1 = new String [] {"AGE",      "RACE.R1",   "RACE.R2",  "RACE.R3",  "DPROS",    "DCAPS",    "PSA",      "VOL",     "GLEASON"};
    double [] vals = new double [] {-0.01368,   -8.14867,    -7.82530,    -8.52895,  0.55964,   0.49548,     0.02794    ,-0.01104   ,0.97704 };
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID"};
    params._train = _prostate._key;
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._intercept = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._max_iterations = 100; // not expected to reach max iterations here
    for(Solver s:new Solver[]{Solver.AUTO,Solver.IRLSM,Solver.L_BFGS}) {
      try {
        params._solver = s;
        System.out.println("SOLVER = " + s);
        job = new GLM(Key.make("prostate_model"), "glm test simple poisson", params);
        model = job.trainModel().get();
        HashMap<String, Double> coefs = model.coefficients();
        System.out.println("coefs = " + coefs.toString());
        System.out.println("metrics = " + model._output._training_metrics);
        for (int i = 0; i < cfs1.length; ++i)
          assertEquals(vals[i], coefs.get(cfs1[i]), 1e-4);
        assertEquals(526.8, GLMTest.nullDeviance(model), 1e-1);
        assertEquals(378.3, GLMTest.residualDeviance(model), 1e-1);
        assertEquals(380, GLMTest.nullDOF(model), 0);
        assertEquals(371, GLMTest.resDOF(model), 0);
        assertEquals(396.3, GLMTest.aic(model), 1e-1);
        model.delete();
        // test scoring
        score = model.score(_prostate);
        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostate);
        hex.AUC2 adata = mm._auc;
        assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
        assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
        Frame score1 = model.score(_prostate);
        score1.remove();
        mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostate);
        assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
        assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
      } finally {
        if (model != null) model.delete();
        if (score != null) score.delete();
        if (job != null) job.remove();
      }
    }
  }


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    File f = find_test_file_static("smalldata/glm_test/prostate_cat_replaced.csv");
    assert f.exists();

    NFSFileVec nfs = NFSFileVec.make(f);
    Key outputKey = Key.make("prostate_test.hex");
    _prostate = ParseDataset.parse(outputKey, nfs._key);
  }

  @AfterClass
  public static void cleanUp() {
    if(_prostate != null)
      _prostate.delete();
  }
}
