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
  static Frame _prostateTrain; // prostate_cat_replaced
  static Frame _prostateTest; // prostate_cat_replaced

  @Test
  public void testOffset() {
    GLM job = null;
    GLMModel model = null;
    Frame score = null;
    double [] offset = new double[] {
      -0.31952698,+0.03826429,-1.35454055,-0.80760533,-1.35454055,+0.03065382,+0.03065382,-1.84261890,+0.03065382,-0.31952698,
      +0.51873218,+0.51873218,-1.35454055,-0.31952698,-1.84261890,-0.31952698,-1.35454055,-0.44981407,-0.31952698,+1.15273788,
      +0.03826429,-1.35454055,+0.03826429,+0.03826429,+0.51873218,+0.03826429,-0.31952698,+0.51873218,+0.03826429,-0.31952698,
      -1.35454055,+0.03826429,+0.03826429,-1.35454055,-0.31952698,-0.31952698,-0.44981407,+0.67227000,-0.80760533,-0.31952698,
      -0.31952698,-0.31952698,-1.35454055,+0.51873218,+0.03826429,+0.03826429,-0.31952698,+0.03826429,+0.03826429,-0.31952698,
      +0.03826429,+0.51873218,-0.31952698,-1.35454055,-0.31952698,-0.44981407,-0.31952698,+0.03826429,+0.03826429,+0.03826429,
      -1.35454055,-1.84261890,+0.03826429,-0.31952698,-0.31952698,-1.35454055,-0.31952698,-1.35454055,+0.03826429,+0.51873218,
      +0.51873218,-1.35454055,-1.35454055,+0.03826429,-1.35454055,-1.35454055,+0.03826429,-1.35454055,+0.03826429,-0.31952698,
      +0.03826429,-1.35454055,+0.03826429,-0.31952698,-1.35454055,+0.03826429,+0.03826429,+0.03826429,-1.35454055,-0.31952698,
      -1.35454055,+0.03065382,-0.31952698,-0.31952698,-0.31952698,-1.35454055,-1.35454055,+0.03065382,-0.31952698,-0.31952698,
      -0.31952698,-0.31952698,-1.35454055,-1.35454055,-1.35454055,-0.31952698,+0.51873218,+0.51873218,-1.35454055,+0.51873218,
      -1.35454055,+0.51873218,+0.03826429,+0.51873218,-0.31952698,-0.80760533,-1.35454055,-0.31952698,-0.31952698,-0.31952698,
      -0.31952698,-1.35454055,-0.31952698,-0.80760533,+0.03826429,+0.03826429,-0.31952698,-0.31952698,-0.31952698,+0.03826429,
      -0.31952698,-0.31952698,+0.03826429,+0.51873218,-1.35454055,-1.35454055,-0.31952698,+0.03826429,-1.35454055,-0.31952698,
      +0.03826429,-1.35454055,-0.80760533,+0.51873218,+0.51873218,-1.35454055,-1.35454055,-0.31952698,+0.03826429,+0.03065382,
      +0.03826429,-0.31952698,-1.35454055,+0.03826429,-0.31952698,-0.31952698,-1.35454055,-0.31952698,-0.31952698,+0.03826429,
      -0.31952698,-0.31952698,+0.51873218,+0.03826429,+0.51873218,+0.03826429,-0.31952698,-0.31952698,+0.03826429,-1.35454055,
      +0.03826429,-1.35454055,-0.31952698,-0.44981407,+0.03826429,+0.03826429,+0.03065382,-0.80760533,-0.31952698,-0.31952698,
      -0.31952698,+0.51873218,-1.84261890,-1.35454055,+0.03826429,+0.03065382,-1.35454055,-1.35454055,-1.84261890,-1.35454055,
      +0.03826429,-0.31952698,+0.31447873,-0.31952698,-0.31952698,-0.31952698,+0.51873218,-1.35454055,-0.31952698,-1.35454055,
      -0.31952698,-0.31952698,+0.03826429,+0.51873218,-1.35454055,-0.31952698,-1.35454055,-1.35454055,-0.31952698,-0.31952698,
      +0.03826429,-1.35454055,-0.80760533,-1.35454055,-0.31952698,+0.03826429,-0.31952698,+0.03826429,-1.35454055,-1.35454055,
      +0.03826429,-1.35454055,-0.31952698,+0.51873218,+0.03826429,-1.35454055,-1.35454055,+0.51873218,-0.31952698,-0.31952698,
      -1.35454055,+0.03826429,+0.03826429,-1.35454055,+0.03826429,-1.35454055,+0.51873218,+0.03826429,-1.35454055,+0.03826429,
      -0.31952698,-0.31952698,+0.03826429,+0.51873218,+0.03826429,-1.35454055,-1.35454055,-1.35454055,-0.31952698,-0.31952698,
      -0.31952698,-0.31952698,+0.03826429,-0.80760533,+0.03826429,-0.44981407,+0.51873218,-0.31952698,-0.31952698,-0.31952698,
      -0.31952698,-0.44981407,-1.35454055,+0.03826429,-0.31952698,-0.31952698,-1.35454055,-1.35454055,+0.03826429,+0.51873218,
      +0.03826429,-0.31952698,-0.80760533,-1.35454055,-1.35454055,-1.35454055,-1.35454055,-0.31952698,+0.51873218,-1.35454055,
      -0.80760533,-1.35454055,-1.35454055,+0.03826429,+0.51873218,-1.35454055,-0.44981407,-1.84261890,-0.80760533,-0.31952698
    };
    Vec v = _prostateTrain.anyVec().makeZero();
    Vec.Writer vw = v.open();
    for(int i = 0; i < offset.length; ++i)
      vw.set(i,offset[i]);
    vw.close();
    Key fKey = Key.make("prostate_with_offset");
    Frame f = new Frame(fKey, new String[]{"offset"}, new Vec[]{v});
    f.add(_prostateTrain.names(),_prostateTrain.vecs());
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
            score = model.score(_prostateTrain); // todo if I use wrong frame, silently ignored
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

//    Call:  glm(formula = CAPSULE ~ . - 1 - RACE - DCAPS, family = binomial,
//      data = train)
//
//    Coefficients:
//    AGE        DPROSa    DPROSb    DPROSc    DPROSd       PSA       VOL   GLEASON
//    -0.00743  -6.46499  -5.60120  -5.18213  -5.70027   0.02753  -0.01235   0.86122
//
//    Degrees of Freedom: 290 Total (i.e. Null);  282 Residual
//    Null Deviance:	    402
//    Residual Deviance: 302.9 	AIC: 318.9
    String [] cfs1 = new String [] {"AGE",     "DPROS.a",  "DPROS.b",    "DPROS.c",  "DPROS.d",      "PSA",    "VOL",  "GLEASON"};
    double [] vals = new double [] {-0.00743,   -6.46499,  -5.60120,   -5.18213,    -5.70027,    0.02753,  -0.01235,   0.86122};
    GLMParameters params = new GLMParameters(Family.binomial);
    params._response_column = "CAPSULE";
    params._ignored_columns = new String[]{"ID","RACE","DCAPS"};
    params._train = _prostateTrain._key;
    params._valid = _prostateTest._key;
    params._lambda = new double[]{0};
    params._alpha = new double[]{0};
    params._standardize = false;
    params._intercept = false;
    params._objective_epsilon = 0;
    params._gradient_epsilon = 1e-6;
    params._max_iterations = 100; // not expected to reach max iterations here
    for(Solver s:new Solver[]{Solver.AUTO,Solver.IRLSM,Solver.L_BFGS}) {
      Frame scoreTrain = null, scoreTest = null;
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
        assertEquals(402,   GLMTest.nullDeviance(model), 1e-1);
        assertEquals(302.9, GLMTest.residualDeviance(model), 1e-1);
        assertEquals(290,   GLMTest.nullDOF(model), 0);
        assertEquals(282,   GLMTest.resDOF(model), 0);
        assertEquals(318.9, GLMTest.aic(model), 1e-1);
        System.out.println("VAL METRICS: " + model._output._validation_metrics);
        // compare validation res dev matches R
        // sum(binomial()$dev.resids(y=test$CAPSULE,mu=p,wt=1))
        // [1]80.92923
        assertEquals(80.92923, GLMTest.residualDevianceTest(model), 1e-4);
//      compare validation null dev against R
//      sum(binomial()$dev.resids(y=test$CAPSULE,mu=.5,wt=1))
//      [1] 124.7665
        assertEquals(124.7665, GLMTest.nullDevianceTest(model),1e-4);
        model.delete();
        // test scoring
        scoreTrain = model.score(_prostateTrain);
        hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTrain);
        hex.AUC2 adata = mm._auc;
        assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
        assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
        scoreTest = model.score(_prostateTest);
        mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostateTest);
        adata = mm._auc;
        assertEquals(model._output._validation_metrics.auc()._auc, adata._auc, 1e-8);
        assertEquals(model._output._validation_metrics._MSE, mm._MSE, 1e-8);
        assertEquals(((ModelMetricsBinomialGLM) model._output._validation_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);

      } finally {
        if (model != null) model.delete();
        if (scoreTrain != null) scoreTrain.delete();
        if(scoreTest != null) scoreTest.delete();
        if (job != null) job.remove();
      }
    }
  }


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    File f = find_test_file_static("smalldata/glm_test/prostate_cat_train.csv");
    assert f.exists();

    NFSFileVec nfs = NFSFileVec.make(f);
    Key outputKey = Key.make("prostate_cat_train.hex");
    _prostateTrain = ParseDataset.parse(outputKey, nfs._key);

    f = find_test_file_static("smalldata/glm_test/prostate_cat_test.csv");
    assert f.exists();

    nfs = NFSFileVec.make(f);
    outputKey = Key.make("prostate_cat_test.hex");
    _prostateTest = ParseDataset.parse(outputKey, nfs._key);
  }

  @AfterClass
  public static void cleanUp() {
    if(_prostateTrain != null)
      _prostateTrain.delete();
    if(_prostateTest != null)
      _prostateTest.delete();
  }
}
