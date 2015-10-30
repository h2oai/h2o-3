package hex.glm;

import hex.FrameSplitter;
import hex.ModelMetricsBinomialGLM;
import hex.ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM;
import hex.ModelMetricsMultinomial;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.H2O;
import water.Key;
import water.TestUtil;

import water.fvec.*;
import water.util.FrameUtils;

import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 10/28/15.
 */
public class GLMBasicTestMultinomial extends TestUtil {
  static Frame _covtype;
  static Frame _train;
  static Frame _test;

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
    _covtype = parse_test_file("smalldata/covtype/covtype.20k.data");
    _covtype.replace(_covtype.numCols()-1,_covtype.lastVec().toCategoricalVec()).remove();
    Key[] keys = new Key[]{Key.make("train"),Key.make("test")};
    H2O.submitTask(new FrameSplitter(_covtype, new double[]{.8},keys,null)).join();
    _train = DKV.getGet(keys[0]);
    _test = DKV.getGet(keys[1]);
  }

  @AfterClass
  public static void cleanUp() {
    if(_covtype != null)  _covtype.delete();
    if(_train != null) _train.delete();
    if(_test != null) _test.delete();
  }


  @Test
  public void testCovtypeBasic(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLM job = null;
    GLMModel model = null;
    Frame preds = null;
    try {
      params._response_column = "C55";
      params._train = _covtype._key;
      params._valid = _covtype._key;
      params._lambda = new double[]{4.881e-05};
      params._alpha = new double[]{1};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_iterations = 100;
      double[] alpha = new double[]{1.0};
      double[] expected_deviance = new double[]{25499.76};
      double[] lambda = new double[]{2.544750e-05};
      for (Solver s : new Solver[]{Solver.L_BFGS,Solver.IRLSM}) {
        System.out.println("solver = " + s);
        params._solver = s;
        for (int i = 0; i < alpha.length; ++i) {
          params._alpha[0] = alpha[i];
          params._lambda[0] = lambda[i];
          job = new GLM(Key.make("covtype_multinomial_model_" + s.toString()), "glm test", params);
          model = job.trainModel().get();
          System.out.println(model._output._training_metrics);
          System.out.println(model._output._validation_metrics);
          assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
          assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance[i] * 1.1);

          preds = model.score(_covtype);
          ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
          assertTrue(model._output._training_metrics.equals(mmTrain));
          model.testJavaScoring(_covtype, preds, 1e-5);
          model.delete();
          model = null;
          job.remove();
          job = null;
          preds.delete();
          preds = null;
        }
      }
    } finally{
      if(job != null) job.remove();
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }
}
