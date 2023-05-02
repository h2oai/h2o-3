package hex.glm;

import hex.ModelMetricsBinomialGLM;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

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
      Scope.track(pred);
      //manually calculate loglikelihood and AIC directly from formula
      int nRow = (int) pred.numRows();
      double logLike = 0.0;
      for (int index=0; index<nRow; index++) {
        logLike += trainData.vec("CAPSULE").at(index) == 0 ? Math.log(pred.vec(1).at(index)) : Math.log(pred.vec(2).at(index));
      }
      assertTrue("Log likelihood from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood+".  Manual AIC: "+logLike+" and they are different.", Math.abs(logLike-((ModelMetricsBinomialGLM) model._output._training_metrics)._loglikelihood)<1e-6);
      double aic = -2*logLike + 2*model._output.rank();
      assertTrue("AIC from model: "+((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC+".  Manual AIC: "+aic+" and they are different.", Math.abs(aic-((ModelMetricsBinomialGLM) model._output._training_metrics)._AIC)<1e-6);
    } finally {
      Scope.exit();
    }
  }

  // test quasibinomial
  // test fractionalbinomial
  // test poisson
  // test negative binomial
  // test gamma
  // test multinomial
}
