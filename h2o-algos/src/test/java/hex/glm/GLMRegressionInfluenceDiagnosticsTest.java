package hex.glm;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.stream.IntStream;

import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.glm.GLMModel.GLMParameters.Influence.dfbetas;
import static hex.glm.GLMModel.GLMParameters.Solver.IRLSM;
import static org.junit.Assert.assertArrayEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMRegressionInfluenceDiagnosticsTest extends TestUtil {

  /**
   * This test aims to test that RID is generated for Gaussian family with the same size as the original training 
   * dataset and we do not have any leaked keys.  We get the same RID when standardize = true and false
   */
  @Test
  public void testGaussianRID(){
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/prostate_cat_train.csv");
      // set cat columns
      train.replace(3, train.vec(3).toCategoricalVec()).remove(); // race to be enum
      train.replace(4, train.vec(4).toCategoricalVec()).remove(); // DPROS to be enum
      train.replace(5, train.vec(5).toCategoricalVec()).remove(); // DCAPS to be enum
      DKV.put(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._response_column = "PSA";
      params._ignored_columns = new String[]{"ID"};
      params._solver = IRLSM;
      params._train = train._key;
      params._influence = dfbetas;
      params._standardize = false;
      params._lambda = new double[]{0.0};
    //  params._standardize = false;
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);
      Frame gaussianRID = glm.getRIDFrame();
      Scope.track(gaussianRID);
      params._standardize = true;
      GLMModel glmS = new GLM(params).trainModel().get();
      Scope.track_generic(glmS);
      Frame gaussianRIDS = glmS.getRIDFrame();
      Scope.track(gaussianRIDS);
      // rid frame should still be the same
      TestUtil.compareFrames(gaussianRID, gaussianRIDS, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  /**
   * test binomial family to make sure there is no leaked keys and same RID results with standardize = true and false
   */
  @Test
  public void testBinomialRID() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/bodyfat.csv");
      train.replace(20, train.vec("bmi").toCategoricalVec()).remove();
      DKV.put(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(binomial);
      params._response_column = "bmi";
      params._ignored_columns = new String[]{"id", "pctfat.siri","density","weight","height","adiposity","chest",
              "abdomen","hip","thigh","knee","ankle","biceps","forearm","wrist","bicepts"};
      params._solver = IRLSM;
      params._train = train._key;
      params._influence = dfbetas;
      params._standardize = false;
      params._lambda = new double[]{0.0};
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);
      Frame gaussianRID = glm.getRIDFrame();
      Scope.track(gaussianRID);
      
      // with standardization
      params._standardize = true;
      GLMModel glmS = new GLM(params).trainModel().get();
      Scope.track_generic(glmS);
      Frame gaussianRIDS = glmS.getRIDFrame();
      Scope.track(gaussianRIDS);
      
      // rid frame should still be the same
      TestUtil.compareFrames(gaussianRID, gaussianRIDS, 1e-6);
    } finally {
      Scope.exit();
    }
  }

  /***
   * This test is used to make sure that we estimate the correct coefficients for Gaussian when there is one less 
   * data row i where i = 0, 1, 2. 
   */
  @Test
  public void testBetaMinusOneGaussian() {
    Scope.enter();
    try {
      Frame train = parseAndTrackTestFile("smalldata/glm_test/bodyfat.csv");
      train.replace(20, train.vec("bmi").toCategoricalVec()).remove();
      DKV.put(train);
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      params._response_column = "pctfat.brozek";
      params._ignored_columns = new String[]{"id", "pctfat.siri","density","weight","height","adiposity","chest",
              "abdomen","hip","thigh","knee","ankle","biceps","forearm","wrist","bicepts"};
      params._solver = IRLSM;
      params._train = train._key;
      params._influence = dfbetas;
      params._standardize = false;
      params._lambda = new double[]{0.0};
      params._keepBetaDiffVar = true;
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);
      Frame betaMinusOneVar = DKV.getGet(glm._output._betadiff_var);
      Scope.track(betaMinusOneVar);
      
      // check the coefficients when there is no row 0, or row 1, or row 2
      Frame trainMinusRow0 = parseAndTrackTestFile("smalldata/glm_test/bodyfatNoRow0.csv");
      trainMinusRow0.replace(20, trainMinusRow0.vec("bmi").toCategoricalVec()).remove();
      Frame trainMinusRow1 = parseAndTrackTestFile("smalldata/glm_test/bodyfatNoRow1.csv");
      trainMinusRow1.replace(20, trainMinusRow1.vec("bmi").toCategoricalVec()).remove();
      Frame trainMinusRow2 = parseAndTrackTestFile("smalldata/glm_test/bodyfatNoRow2.csv");
      trainMinusRow2.replace(20, trainMinusRow2.vec("bmi").toCategoricalVec()).remove();
      DKV.put(trainMinusRow0);
      DKV.put(trainMinusRow1);
      DKV.put(trainMinusRow2);
      assertBetaAgree(betaMinusOneVar, 0, trainMinusRow0, true);
      assertBetaAgree(betaMinusOneVar, 1, trainMinusRow1, false);
      assertBetaAgree(betaMinusOneVar, 2, trainMinusRow2, true);
    } finally {
      Scope.exit();
    }
  }
  
  public void assertBetaAgree(Frame betaVarFrame, int rowIndex, Frame train, boolean standardize) {
    Scope.enter();
    try {
      GLMModel.GLMParameters params = new GLMModel.GLMParameters(gaussian);
      train.replace(20, train.vec("bmi").toCategoricalVec()).remove();
      DKV.put(train);
      params._response_column = "pctfat.brozek";
      params._ignored_columns = new String[]{"id", "pctfat.siri","density","weight","height","adiposity","chest",
              "abdomen","hip","thigh","knee","ankle","biceps","forearm","wrist","bicepts"};
      params._solver = IRLSM;
      params._train = train._key;
      params._standardize = standardize;
      params._lambda = new double[]{0.0};
      GLMModel glm = new GLM(params).trainModel().get();
      Scope.track_generic(glm);
      double[] glmCoeffs = glm.beta();  // build without row rowIndex in dataset train
      int coeffLen = glmCoeffs.length;
      double[] ridCoeffs = IntStream.range(0, coeffLen).mapToDouble(x -> betaVarFrame.vec(x).at(rowIndex)).toArray();
      assertArrayEquals(glmCoeffs, ridCoeffs, 1e-6);
    } finally {
      Scope.exit();
    }
  }
}
