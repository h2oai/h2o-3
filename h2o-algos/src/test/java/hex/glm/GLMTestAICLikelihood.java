package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.ModelMetricsRegressionGLM;
import org.apache.commons.math3.special.Gamma;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.MathUtils;

import static hex.DistributionFactory.LogExpUtil.log;
import static org.junit.Assert.assertTrue;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMTestAICLikelihood extends TestUtil {
  // test binomial generated from model and from manually generated one using single thread only
  @Test
  public void testBinomialAICLikelihood() {
    Scope.enter();
    try {
      Frame trainData =  parseTestFile("smalldata/prostate/prostate.csv");
      Scope.track(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.binomial;
      parms._response_column = "CAPSULE"; 
      parms._calc_like = true;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      final Frame pred = model.score(trainData);
      final Vec responseCol = trainData.vec(parms._response_column);
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      double probabilityOf1;
      double w = 1.0;
      double yr;
      for (int index=0; index<nRow; index++) {
        yr = responseCol.at(index);
        probabilityOf1 = pred.numCols() > 1 ? pred.vec(2).at(index) : pred.vec(0).at(index); // probability of 1 equals prediction
        logLike +=  w * (trainData.vec("CAPSULE").at(index) * log(probabilityOf1) + (1-yr) * log(1 - probabilityOf1))
                + w * (Gamma.digamma(2) - Gamma.digamma(yr + 1)
                - Gamma.digamma(1 - yr + 1));
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood+".  Manual loglikelihood: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC)<1e-6);
      System.out.println(((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood + " " + ((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC);
      System.out.println(logLike + " " + aic);
    } finally {
      Scope.exit();
    }
  }

  // test quasibinomial
  @Test
  public void testQuasibinomialAICLikelihood() {
    Scope.enter();
    try {
      final Frame trainData = Scope.track(parseTestFile("smalldata/prostate/prostate.csv"));
      Scope.track(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.quasibinomial;
      parms._response_column = "CAPSULE";
      parms._ignored_columns = new String[]{"ID"};
      parms._calc_like = true;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      model._output.resetThreshold(0.5);
      final Frame pred = model.score(trainData);
      final Vec responseCol = trainData.vec(parms._response_column);
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      double probabilityOf1;
      double yr;
      for (int index=0; index<nRow; index++) {
        yr = responseCol.at(index);
        probabilityOf1 = pred.numCols() > 1 ? pred.vec(2).at(index) : pred.vec(0).at(index); // probability of 1 equals prediction
        if(yr == pred.vec(0).at(index)) {
          continue; // logLike += 0.0;
        } else if (pred.vec(0).at(index) > 1) {
          logLike += -1 * yr * log(probabilityOf1);
        } else {
          logLike += -1 * (yr * log(probabilityOf1) + (1 - yr) * log(1 - probabilityOf1));
        }
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood+".  Manual AIC: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC)<1e-6);
      System.out.println(((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood + " " + ((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC);
      System.out.println(logLike + " " + aic);
    } finally {
      Scope.exit();
    }
  }

  // test fractionalbinomial
  @Test
  public void testFractionalbinomialAICLikelihood() {
    Scope.enter();
    try {
      Frame trainData =  parseTestFile("smalldata/prostate/prostate.csv");
      Scope.track(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.fractionalbinomial;
      parms._response_column = "CAPSULE";
      parms._calc_like = true;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      model._output.resetThreshold(0.5);
      final Frame pred = model.score(trainData);
      final Vec responseCol = trainData.vec(parms._response_column);
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      double probabilityOf1;
      double yr;
      for (int index=0; index<nRow; index++) {
        yr = responseCol.at(index);
        probabilityOf1 = pred.numCols() > 1 ? pred.vec(2).at(index) : pred.vec(0).at(index); // probability of 1 equals prediction
        if(yr == pred.vec(0).at(index)) {
          continue; // logLike += 0.0;
        } else {
          logLike +=  (MathUtils.y_log_y(yr, probabilityOf1)) + MathUtils.y_log_y(1 - yr, 1 - probabilityOf1);
        }
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood+".  Manual loglikelihood: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC)<1e-6);
      System.out.println(((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood + " " + ((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC);
      System.out.println(logLike + " " + aic);
    } finally {
      Scope.exit();
    }
  }
  
  // test poisson
  @Test
  public void testPoissonAICLikelihood() {
    Scope.enter();
    try {
      Frame trainData =  parseTestFile("smalldata/junit/cars.csv");
      Scope.track(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.poisson;
      parms._response_column = "power (hp)";
      parms._calc_like = true;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      final Frame pred = model.score(trainData);
      final Vec responseCol = trainData.vec(parms._response_column);
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      double prediction;
      double yr;
      for (int index=0; index<nRow; index++) {
        yr = responseCol.at(index);
        prediction = pred.vec(0).at(index);
        if(!Double.isNaN(yr) && !Double.isNaN(prediction)) {
          logLike += yr * log(prediction) - prediction - Gamma.digamma(yr + 1);
        }
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood+".  Manual loglikelihood: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC)<1e-6);
      System.out.println(((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood + " " + ((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC);
      System.out.println(logLike + " " + aic);
    } finally {
      Scope.exit();
    }
  }
  
  // test negative binomial
  @Test
  public void testNegativeBinomialAICLikelihood() {
    Scope.enter();
    try {
      Frame trainData =  parseTestFile("smalldata/prostate/prostate.csv");
      Scope.track(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.negativebinomial;
      parms._response_column = "CAPSULE";
      parms._calc_like = true;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      final Frame pred = model.score(trainData);
      final Vec responseCol = trainData.vec(parms._response_column);
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      double prediction;
      double yr;
      for (int index=0; index<nRow; index++) {
        yr = responseCol.at(index);
        prediction = pred.vec(0).at(index);
        logLike += yr * log(parms._invTheta * prediction) - (yr + 1/parms._invTheta) * log(1 + parms._invTheta * prediction)
                + log(Gamma.gamma(yr + 1/parms._invTheta) / (Gamma.gamma(yr + 1) * Gamma.gamma(1/parms._invTheta)));
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood+".  Manual loglikelihood: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC)<1e-6);
      System.out.println(((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood + " " + ((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC);
      System.out.println(logLike + " " + aic);
    } finally {
      Scope.exit();
    }
  }
  
  // test gamma
  @Test
  public void testGammaAICLikelihood() {
    Scope.enter();
    try {
      Frame trainData =  parseTestFile("smalldata/junit/cars.csv");
      Scope.track(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.gamma;
      parms._response_column = "power (hp)";
      parms._calc_like = true;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      final Frame pred = model.score(trainData);
      final Vec responseCol = trainData.vec(parms._response_column);
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      double prediction;
      double yr;
      double digamma = Gamma.digamma(1);
      for (int index=0; index<nRow; index++) {
        yr = responseCol.at(index);
        prediction = pred.vec(0).at(index);
        if(!Double.isNaN(yr) && !Double.isNaN(prediction)) {
          logLike += log(yr / prediction) - yr / prediction - log(yr) - digamma;
        }
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood+".  Manual loglikelihood: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC)<1e-6);
      System.out.println(((ModelMetricsRegressionGLM) model._output._training_metrics)._loglikelihood + " " + ((ModelMetricsRegressionGLM) model._output._training_metrics)._AIC);
      System.out.println(logLike + " " + aic);
    } finally {
      Scope.exit();
    }
  }
  // test multinomial
  @Test
  public void testMultinomialAICLikelihood() {
    Scope.enter();
    try {
      Frame trainData =  parseTestFile("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv");
      Scope.track(trainData);
      trainData.replace(trainData.numCols()-1, trainData.vec("C11").toCategoricalVec()).remove();
      DKV.put(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.multinomial;
      parms._response_column = "C11";
      parms._calc_like = true;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      final Frame pred = model.score(trainData);
      final Vec responseCol = trainData.vec(parms._response_column);
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      double prediction;
      double yr;
      for (int index=0; index<nRow; index++) {
        yr = responseCol.at(index);
        prediction = pred.vec(0).at(index);
//        probabilityOf1 = pred.numCols() > 1 ? pred.vec(2).at(index) : pred.vec(0).at(index); // probability of 1 equals prediction
        double predictedProbabilityOfActualClass = pred.numCols() > 1 ? pred.vec((int) yr + 1).at(index) : (prediction == yr ? 1.0 : 0.0);
        logLike += log(predictedProbabilityOfActualClass);
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM) model._output._training_metrics)._loglikelihood+".  Manual loglikelihood: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM) model._output._training_metrics)._AIC)<1e-6);
      System.out.println(((ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM) model._output._training_metrics)._loglikelihood + " " + ((ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM) model._output._training_metrics)._AIC);
      System.out.println(logLike + " " + aic);
    } finally {
      Scope.exit();
    }
  }
}
