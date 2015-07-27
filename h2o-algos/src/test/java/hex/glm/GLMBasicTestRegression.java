package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.ModelMetricsRegressionGLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

import java.io.File;
import java.util.HashMap;

import water.fvec.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 6/4/15.
 */
public class GLMBasicTestRegression extends TestUtil {
  static Frame _canCarTrain;
  static Vec _merit, _class;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    File f = find_test_file_static("smalldata/glm_test/cancar_logIn.csv");
    assert f.exists();
    NFSFileVec nfs = NFSFileVec.make(f);
    Key outputKey = Key.make("prostate_cat_train.hex");
    _canCarTrain = ParseDataset.parse(outputKey, nfs._key);
    _canCarTrain.add("Merit", (_merit = _canCarTrain.remove("Merit")).toEnum());
    _canCarTrain.add("Class",(_class = _canCarTrain.remove("Class")).toEnum());
    DKV.put(_canCarTrain._key, _canCarTrain);
  }

  @Test
  public void testPoissonWithOffset(){
    GLM job = null;
    GLMModel model = null;
    Frame scoreTrain = null;

//    Call:  glm(formula = formula, family = poisson, data = D)
//
//    Coefficients:
//    (Intercept)       Merit1       Merit2       Merit3       Class2       Class3       Class4       Class5
//    -2.0357      -0.1378      -0.2207      -0.4930       0.2998       0.4691       0.5259       0.2156
//
//    Degrees of Freedom: 19 Total (i.e. Null);  12 Residual
//    Null Deviance:	    33850
//    Residual Deviance: 579.5 	AIC: 805.9

    String [] cfs1 = new String [] { "Intercept", "Merit.1", "Merit.2", "Merit.3", "Class.2", "Class.3", "Class.4", "Class.5"};
    double [] vals = new double [] { -2.0357,     -0.1378,  -0.2207,  -0.4930,   0.2998,   0.4691,   0.5259,    0.2156};

      GLMParameters parms = new GLMParameters(Family.poisson);
      parms._train = _canCarTrain._key;
      parms._ignored_columns = new String[]{"Insured", "Premium", "Cost"};
      // "response_column":"Claims","offset_column":"logInsured"
      parms._response_column = "Claims";
      parms._offset_column = "logInsured";
      parms._standardize = false;
      parms._lambda = new double[]{0};
      parms._alpha = new double[]{0};
      parms._objective_epsilon = 0;
      parms._gradient_epsilon = 1e-10;
      parms._max_iterations = 1000;
      for (Solver s : GLMParameters.Solver.values()) {
        try {
          parms._solver = s;
          job = new GLM(Key.make("prostate_model"), "glm test simple poisson", parms);
          model = job.trainModel().get();
          HashMap<String, Double> coefs = model.coefficients();
          System.out.println("coefs = " + coefs);
          for (int i = 0; i < cfs1.length; ++i)
            assertEquals(vals[i], coefs.get(cfs1[i]), 1e-4);
          assertEquals(33850, GLMTest.nullDeviance(model), 5);
          assertEquals(579.5, GLMTest.residualDeviance(model), 1e-4*579.5);
          assertEquals(19,   GLMTest.nullDOF(model), 0);
          assertEquals(12,   GLMTest.resDOF(model), 0);
          assertEquals(805.9, GLMTest.aic(model), 1e-4*805.9);
          // test scoring
          try {
            Frame fr = new Frame(_canCarTrain.names(),_canCarTrain.vecs());
            fr.remove(parms._offset_column);
            scoreTrain = model.score(fr);
            assertTrue("shoul've thrown IAE", false);
          } catch (IllegalArgumentException iae) {
            assertTrue(iae.getMessage().contains("Test dataset is missing offset vector"));
          }
          scoreTrain = model.score(_canCarTrain);
          hex.ModelMetricsRegressionGLM mmTrain = (ModelMetricsRegressionGLM)hex.ModelMetricsRegression.getFromDKV(model, _canCarTrain);
          assertEquals(model._output._training_metrics._MSE, mmTrain._MSE, 1e-8);
          assertEquals(GLMTest.residualDeviance(model), mmTrain._resDev, 1e-8);
          assertEquals(GLMTest.nullDeviance(model), mmTrain._nullDev, 1e-8);
        } finally {
          if(job != null)job.remove();
          if(model != null) model.delete();
          if(scoreTrain != null) scoreTrain.delete();
        }
      }
  }

  @AfterClass
  public static void cleanUp() {
    if(_canCarTrain != null)
      _canCarTrain.delete();
    if(_merit != null)
      _merit.remove();
    if(_class != null)
      _class.remove();
  }


}
