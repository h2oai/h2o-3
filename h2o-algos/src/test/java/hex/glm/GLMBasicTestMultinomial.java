package hex.glm;

import hex.FrameSplitter;
import hex.ModelMetricsBinomialGLM;
import hex.ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM;
import hex.ModelMetricsMultinomial;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;
import org.junit.*;
import org.junit.rules.ExpectedException;
import water.*;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.*;
import water.util.FrameUtils;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by tomasnykodym on 10/28/15.
 */
public class GLMBasicTestMultinomial extends TestUtil {
  static Frame _covtype;
  static Frame _train;
  static Frame _test;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

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
  public void testCovtypeNoIntercept(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    Vec weights = _covtype.anyVec().makeCon(1);
    Key k = Key.<Frame>make("cov_with_weights");
    Frame f = new Frame(k,_covtype.names(),_covtype.vecs());
    f.add("weights",weights);
    DKV.put(f);
    try {
      params._response_column = "C55";
      params._train = k;
      params._valid = _covtype._key;
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._weights_column = "weights";
      params._missing_values_handling = DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip;
      params._intercept = false;
      double[] alpha = new double[]{0,.5,.1};
      Solver s = Solver.L_BFGS;
      System.out.println("solver = " + s);
      params._solver = s;
      params._max_iterations = 5000;
      for (int i = 0; i < alpha.length; ++i) {
        params._alpha = new double[]{alpha[i]};
//        params._lambda[0] = lambda[i];
        model = new GLM(params).trainModel().get();
        System.out.println(model.coefficients());
//        Assert.assertEquals(0,model.coefficients().get("Intercept"),0);
        double [][] bs = model._output.getNormBetaMultinomial();
        for(double [] b:bs)
          Assert.assertEquals(0,b[b.length-1],0);
        System.out.println(model._output._model_summary);
        System.out.println(model._output._training_metrics);
        System.out.println(model._output._validation_metrics);
        preds = model.score(_covtype);
        ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
        assertTrue(model._output._training_metrics.equals(mmTrain));
        model.delete();
        model = null;
        preds.delete();
        preds = null;
      }
    } finally{
      weights.remove();
      DKV.remove(k);
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }


  @Test
  public void testCovtypeBasic(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    Vec weights = _covtype.anyVec().makeCon(1);
    Key k = Key.<Frame>make("cov_with_weights");
    Frame f = new Frame(k,_covtype.names(),_covtype.vecs());
    f.add("weights",weights);
    DKV.put(f);
    try {
      params._response_column = "C55";
      params._train = k;
      params._valid = _covtype._key;
      params._lambda = new double[]{4.881e-05};
      params._alpha = new double[]{1};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._weights_column = "weights";
      params._missing_values_handling = DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip;
      double[] alpha = new double[]{1};
      double[] expected_deviance = new double[]{25499.76};
      double[] lambda = new double[]{2.544750e-05};
      for (Solver s : new Solver[]{Solver.IRLSM, Solver.COORDINATE_DESCENT, Solver.L_BFGS}) {
        System.out.println("solver = " + s);
        params._solver = s;
        params._max_iterations = params._solver == Solver.L_BFGS?300:10;
        for (int i = 0; i < alpha.length; ++i) {
          params._alpha[0] = alpha[i];
          params._lambda[0] = lambda[i];
          model = new GLM(params).trainModel().get();
          System.out.println(model._output._model_summary);
          System.out.println(model._output._training_metrics);
          System.out.println(model._output._validation_metrics);
          assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
          assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance[i] * 1.1);
          preds = model.score(_covtype);
          ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
          assertTrue(model._output._training_metrics.equals(mmTrain));
          model.delete();
          model = null;
          preds.delete();
          preds = null;
        }
      }
    } finally{
      weights.remove();
      DKV.remove(k);
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }

  @Test
  public void testCovtypeMinActivePredictors(){
    GLMParameters params = new GLMParameters(Family.multinomial);
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
      params._max_active_predictors = 50;
      params._max_iterations = 10;
      double[] alpha = new double[]{.99};
      double expected_deviance = 33000;
      double[] lambda = new double[]{2.544750e-05};
      Solver s = Solver.COORDINATE_DESCENT;
      System.out.println("solver = " + s);
      params._solver = s;
      model = new GLM(params).trainModel().get();
      System.out.println(model._output._model_summary);
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      System.out.println("rank = " + model._output.rank() + ", max active preds = " + (params._max_active_predictors + model._output.nclasses()));
      assertTrue(model._output.rank() <= params._max_active_predictors + model._output.nclasses());
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance * 1.1);
      preds = model.score(_covtype);
      ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
      assertTrue(model._output._training_metrics.equals(mmTrain));
      model.delete();
      model = null;
      preds.delete();
      preds = null;
    } finally{
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }


  @Test
  public void testCovtypeLS(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    try {
      double expected_deviance = 33000;
      params._nlambdas = 3;
      params._response_column = "C55";
      params._train = _covtype._key;
      params._valid = _covtype._key;
      params._alpha = new double[]{.99};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_active_predictors = 50;
      params._max_iterations = 500;
      params._solver = Solver.AUTO;
      params._lambda_search = true;
      model = new GLM(params).trainModel().get();
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      preds = model.score(_covtype);
      ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, _covtype);
      assertTrue(model._output._training_metrics.equals(mmTrain));
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance);
      System.out.println(model._output._model_summary);
      model.delete();
      model = null;
      preds.delete();
      preds = null;
    } finally{
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }

  @Test
  public void testCovtypeNAs(){
    GLMParameters params = new GLMParameters(Family.multinomial);
    GLMModel model = null;
    Frame preds = null;
    Frame covtype_subset = null, covtype_copy = null;
    try {
      double expected_deviance = 26000;
      covtype_copy = _covtype.deepCopy("covtype_copy");
      DKV.put(covtype_copy);
      Vec.Writer w = covtype_copy.vec(54).open();
      w.setNA(10);
      w.setNA(20);
      w.setNA(30);
      w.close();
      covtype_subset = new Frame(Key.<Frame>make("covtype_subset"),new String[]{"C51","C52","C53","C54","C55"},covtype_copy.vecs(new int[]{50,51,52,53,54}));
      DKV.put(covtype_subset);
//      params._nlambdas = 3;
      params._response_column = "C55";
      params._train = covtype_copy._key;
      params._valid = covtype_copy._key;
      params._alpha = new double[]{.99};
      params._objective_epsilon = 1e-6;
      params._beta_epsilon = 1e-4;
      params._max_active_predictors = 50;
      params._max_iterations = 500;
      params._solver = Solver.L_BFGS;
      params._missing_values_handling = DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip;
//      params._lambda_search = true;
      model = new GLM(params).trainModel().get();
      assertEquals(covtype_copy.numRows()-3-1,model._nullDOF);
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      preds = model.score(covtype_copy);
      ModelMetricsMultinomialGLM mmTrain = (ModelMetricsMultinomialGLM) hex.ModelMetricsMultinomial.getFromDKV(model, covtype_copy);
      assertTrue(model._output._training_metrics.equals(mmTrain));
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= expected_deviance);
      System.out.println(model._output._model_summary);
      model.delete();
      model = null;
      preds.delete();
      preds = null;
      // now run the same on the subset
      params._train = covtype_subset._key;
      model = new GLM(params).trainModel().get();
      assertEquals(covtype_copy.numRows()-3-1,model._nullDOF);
      System.out.println(model._output._training_metrics);
      System.out.println(model._output._validation_metrics);
      assertTrue(model._output._training_metrics.equals(model._output._validation_metrics));
      preds = model.score(_covtype);
      System.out.println(model._output._model_summary);
      assertTrue(((ModelMetricsMultinomialGLM) model._output._training_metrics)._resDev <= 66000);
      model.delete();
      model = null;
      preds.delete();
      preds = null;

    } finally{
      if(covtype_subset != null) covtype_subset.delete();
      if(covtype_copy != null)covtype_copy.delete();
      if(model != null)model.delete();
      if(preds != null)preds.delete();
    }
  }

  @Test
  public void testNaiveCoordinateDescent() {
    expectedException.expect(H2OIllegalArgumentException.class);
    expectedException.expectMessage("Naive coordinate descent is not supported.");
    GLMParameters params = new GLMParameters(Family.multinomial);
    params._solver = Solver.COORDINATE_DESCENT_NAIVE;

    // Should throw exception with information about unsupported message
    new GLM(params).trainModel().get();
  }


}
