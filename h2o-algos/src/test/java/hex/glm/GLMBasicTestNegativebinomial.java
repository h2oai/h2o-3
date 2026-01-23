package hex.glm;

import hex.CreateFrame;
import hex.DataInfo;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import hex.glm.GLMModel.GLMWeightsFun;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Random;

import static org.junit.Assert.assertTrue;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMBasicTestNegativebinomial {

  // test and compare mojo/pojo/predict values
  @Test
  public void testMojoPojoPredict() {
    GLMModel model;
    Frame tfr, pred;
    
    try {
      Scope.enter();
      tfr = createData(5000, 11, 0.4, 0.5, 0);
      Vec v = tfr.remove("response");
      tfr.add("response", v.toNumericVec());
      Scope.track(v);
      Scope.track(tfr);
      DKV.put(tfr);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.negativebinomial,
              GLMModel.GLMParameters.Family.negativebinomial.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
      params._train = tfr._key;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      params._standardize = false;
      params._theta = 0.5;
      params._response_column="response";

      GLM glm = new GLM( params);
      model = glm.trainModel().get();
      pred = model.score(tfr);
      Scope.track(pred);
      Scope.track_generic(model);
      Assert.assertTrue(model.testJavaScoring(tfr, pred, 1e-6));
    } finally {
      Scope.exit();
    }
  }
  
  // Test gradient/hessian/likelhood generation for negativebinomial with log link with the following paper:
  // Negative Binomial Regression, Chapter 326, NCSS.com
  //
  // This test is abandoned for now until the bug in DataInfo is corrected.
  @Test
  public void testGradientLikelihoodTask() {
    GLMModel model;
    Frame tfr;

    DataInfo dinfo = null;
    try {
      Scope.enter();
      tfr = createData(500, 8, 0, 0.5, 0);
      Vec v = tfr.remove("response");
      Scope.track(tfr);
      DKV.put(tfr);
      Scope.track(v);
      tfr.add("response", v.toNumericVec());

      GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.negativebinomial,
              GLMModel.GLMParameters.Family.negativebinomial.defaultLink, new double[]{0}, new double[]{0}, 0, 0);

      params._train = tfr._key;
      params._lambda = new double[]{0};
      params._use_all_factor_levels = true;
      params._standardize = false;
      params._theta = 0.5;
      params._response_column="response";
      params._obj_reg = 1.0;

      dinfo = new DataInfo(tfr, null, 1,
              params._use_all_factor_levels || params._lambda_search, params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true, false, false, false, false, false);
      DKV.put(dinfo._key, dinfo);
      Scope.track_generic(dinfo);

      // randomly generate GLM coefficients
      int betaSize = dinfo.fullN()+1;
      double[] beta = MemoryManager.malloc8d(betaSize);
      Random rnd = new Random(987654321);
      for (int i = 0; i < beta.length; ++i)
        beta[i] = 1 - 2 * rnd.nextDouble();

      // invoke Gradient task to calculate GLM gradients, hessian and likelihood
      GLMTask.GLMGradientTask grtGen = new GLMTask.GLMNegativeBinomialGradientTask(null, dinfo, params, params._lambda[0], beta).doAll(dinfo._adaptedFrame);
      // invoke task to generate the gradient and the hessian
      GLMTask.GLMIterationTask heg =  new GLMTask.GLMIterationTask(null,dinfo, new GLMWeightsFun(params),beta).doAll(dinfo._adaptedFrame);
      
      double[][] hessian = new double[betaSize][];
      for (int i=0; i<betaSize; i++) {
        hessian[i] = new double[betaSize];
      }
      double[] gradient = new double[betaSize];
      double manualLLH = manualGradientNHess(tfr, beta, hessian, gradient, params._theta);
      assertTrue("Likelihood from GLMIterationTask and GLMGradientTask should equal but not...", 
              Math.abs(grtGen._likelihood-heg._likelihood)<1e-10);
      assertTrue("Likelihood from GLMIterationTask and Manual calculation should equal but not...",
              Math.abs(grtGen._likelihood-manualLLH)<1e-10);
      compareArrays(grtGen._gradient, gradient, 1e-10, true); // compare gradients
      double[][] hess = heg.getGram().getXX();
      for (int index=0; index < betaSize; index++)
        compareArrays(hess[index], hessian[index], 1e-10, false);
    } finally {
      Scope.exit();
      DKV.remove(dinfo._key);
      if (dinfo != null) dinfo.remove();
    }
  }
  
  @Test
  public void testNegativeBinomialDispersionEstimationLeakCheck() {
    Scope.enter();
    try {
      Frame trainData = parseTestFile("smalldata/prostate/prostate.csv");
      Scope.track(trainData);
      final GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._train = trainData._key;
      parms._family = GLMModel.GLMParameters.Family.negativebinomial;
      parms._response_column = "CAPSULE";
      parms._compute_p_values = true;
      parms._remove_collinear_columns = true;
      parms._dispersion_parameter_method = GLMModel.GLMParameters.DispersionMethod.ml;
      final GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);

    } finally {
      Scope.exit();
    }
  }
  
  void  compareArrays(double[] arr1, double[] arr2, double eps, boolean careAboutLengths) {
    int arrLength = arr1.length;
    if (careAboutLengths)
      assertTrue("Array lengths should be equal but not.", arrLength==arr2.length);
    
    for (int index=0; index < arrLength; index++) 
      assertTrue("Array elements should be equal within tolerance but not...", Math.abs(arr1[index]-arr2[index])<eps);
  }

  // function to manually generate the gradient and hessian for negative binomial
  double manualGradientNHess(Frame fr, double[] beta, double[][] hessian, double[] gradient, double theta) {
    int nrow = (int) fr.numRows();
    int numBeta = beta.length;
    int interceptInd = numBeta - 1; // index into the intercept
    double likelihood = 0;
    double es;
    double he;
    Vec respVec = fr.vec(interceptInd);
    
    for (int rowInd = 0; rowInd < nrow; rowInd++) {
      double temp = 0;
      for (int colInd = 0; colInd < interceptInd; colInd++) {
        temp += fr.vec(colInd).at(rowInd)*beta[colInd];
      } // calculate eta
      temp += beta[interceptInd];
      double eta = Math.exp(temp);
      
      if (respVec.at(rowInd) == 0) {
        es = eta/(1+theta*eta);
        he = eta/Math.pow(1+theta*eta,2);
        likelihood += Math.log(1+theta*eta)/theta;
      } else {
        es = (eta-respVec.at(rowInd))/(1+theta*eta);
        he = eta*(1+theta*respVec.at(rowInd))/Math.pow(1+theta*eta,2);
        likelihood -= logGammas(respVec.at(rowInd), 1 / theta) - (respVec.at(rowInd) + 1 / theta) *
                Math.log(1 + theta * eta) + respVec.at(rowInd) * Math.log(eta) + respVec.at(rowInd) * Math.log(theta);
      }
      for (int colInd = 0; colInd < interceptInd; colInd++) { // take care of non-intercept cross-terms
        gradient[colInd] += fr.vec(colInd).at(rowInd)*es;
        for (int colInd2 = 0; colInd2 < interceptInd; colInd2++) {
          hessian[colInd][colInd2] += fr.vec(colInd).at(rowInd)*fr.vec(colInd2).at(rowInd)*he;
        }
        hessian[interceptInd][colInd] += he*fr.vec(colInd).at(rowInd); // fix the intercept and non-intercept terms
        hessian[colInd][interceptInd] = hessian[interceptInd][colInd];
      }
      // fix the intercept here
      gradient[interceptInd] += es;
      hessian[interceptInd][interceptInd] += he;
    }

    return likelihood;
  }

  double logGammas(double resp, double invTheta) {
    double result = 0;
    int rep = (int) resp;
    for (int j=0; j < rep; j++) {
      result += Math.log((j+invTheta)/(j+1));
    }
    return result;
  }

  public Frame createData(long nrow, int ncol, double catFrac, double intFrac, double missFrac) {
    // generate synthetic dataset
    CreateFrame cf = new CreateFrame();
    cf.rows = nrow;
    cf.cols = ncol;
    cf.categorical_fraction = catFrac;
    cf.integer_fraction = intFrac;
    cf.string_fraction = 0;
    cf.time_fraction = 0.0;
    cf.real_range = 10;
    cf.integer_range = 10;
    cf.seed = 1234;
    cf.has_response = true;
    cf.response_factors = 20;
    cf.missing_fraction = missFrac;
    return cf.execImpl().get();
  }

}
