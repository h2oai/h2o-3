package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
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

/**
 * Created by tomasnykodym on 4/26/15.
 */
public class GLMBasicTest extends TestUtil {
  static Frame _prostate; // prostate_cat_replaced

  @Test
  public void testNoIntercept() {
    GLM job = null;
    GLMModel model = null;
    Frame score = null;
    try{
      Scope.enter();
//      Call:  glm(formula = CAPSULE ~ . - 1, family = binomial, data = D)
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
      job = new GLM(Key.make("prostate_model"),"glm test simple poisson",params);
      model = job.trainModel().get();
      HashMap<String, Double> coefs = model.coefficients();
      System.out.println("coefs = " + coefs.toString());
      System.out.println("val   = " + model.validation());
      for(int i = 0; i < cfs1.length; ++i)
        assertEquals(vals[i], coefs.get(cfs1[i]),1e-4);
      GLMValidation val = model.validation();
      assertEquals(526.8, val.nullDeviance(),1e-1);
      assertEquals(378.3, val.residualDeviance(),1e-1);
      assertEquals(380,val.nullDOF());
      assertEquals(371,val.resDOF());
      assertEquals(396.3, val.aic,1e-1);
      model.delete();
      // test scoring
      score = model.score(_prostate);
      hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostate);
      hex.AUC2 adata = mm._auc;
      assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
      assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
      assertEquals(((ModelMetricsBinomialGLM)model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm)._resDev, 1e-8);
      Frame score1 = model.score(_prostate);
      score1.remove();
      mm = hex.ModelMetricsBinomial.getFromDKV(model, _prostate);
      assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
      assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
      assertEquals(((ModelMetricsBinomialGLM)model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM)mm)._resDev, 1e-8);
    } finally {
      if(model != null)model.delete();
      if(score != null)score.delete();
      if( job != null ) job.remove();
      Scope.exit();
    }
  }


  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    File f = find_test_file("smalldata/glm_test/prostate_cat_replaced.csv");
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
