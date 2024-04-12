package hex.optimization;

import hex.DataInfo;
import hex.glm.ComputationState;
import hex.glm.ConstrainedGLMUtils;
import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.Random;

import static hex.glm.ConstrainedGLMUtils.calGradient;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class ExactLineSearchTest extends TestUtil {
  DataInfo _dinfo;
  ComputationState _state;
  GLMModel.GLMParameters _params;
  String _responseCol;
  Frame _train;
  GLM.BetaInfo _betaInfo;
  GLM.GLMGradientSolver _ginfo;
  Random _generator;
  double[] _beta;
  String[] _coefNames;
  public Job<GLMModel> _job;
  
  @Before
  public void setup() {
    Scope.enter();
    prepareTrainFrame("smalldata/glm_test/gaussian_20cols_10000Rows.csv");
    prepareParms(124);
  }
  
  public void genRandomArrays(long seed) {
    _generator = new Random(seed);
    _beta = _generator.doubles(_coefNames.length+1, -1, 1).toArray();
  }
  public void prepareParms(long seed) {
    _params = new GLMModel.GLMParameters(gaussian);
    _params._response_column = "C21";
    _params._standardize = true;
    _params._train = _train._key;
    _params._alpha = new double[]{0.0};
    _dinfo = new DataInfo(_train.clone(), null, 1, _params._use_all_factor_levels || _params._lambda_search,
            _params._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE,
            DataInfo.TransformType.NONE, _params.missingValuesHandling()==GLMModel.GLMParameters.MissingValuesHandling.Skip,
            _params.imputeMissing(), _params.makeImputer(), false, false, false, false, _params.interactionSpec());
    _coefNames = _dinfo.coefNames();
    genRandomArrays(seed);
    _betaInfo = new GLM.BetaInfo(1, _dinfo.fullN() + 1);
    Key<GLMModel> jobKey = Key.make();
    _job = new Job<>(jobKey, _params.javaName(), _params.algoName());
    _state = new ComputationState(null, _params, _dinfo, null, _betaInfo, null, null);
    _state._csGLMState = new ConstrainedGLMUtils.ConstraintGLMStates(_coefNames, null, _params);
    _ginfo = new GLM.GLMGradientSolver(_job, _params, _dinfo, 0,null, _betaInfo);
    GLM.GLMGradientInfo gradientInfo = calGradient(_beta, _state, _ginfo, null, null,
            null, null);
    _state.updateState(_beta, gradientInfo);
  }
  
  public void prepareTrainFrame(String fileWPath) {
    _train = parseAndTrackTestFile(fileWPath);
    for (int colInd=0; colInd < 10; colInd++)
      _train.replace((colInd), _train.vec(colInd).toCategoricalVec()).remove();
    _responseCol = "C21";
    DKV.put(_train);
  }

  // in this test, call ExactLinesearch with inner product of direction and gradient to be +ve
  @Test
  public void testExactLineSearchBadDir() {
    try {
      double[] dir = _generator.doubles(_coefNames.length + 1, -1, 1).toArray();
      if (ArrayUtils.innerProduct(dir, _state.ginfo()._gradient) <= 0)
        ArrayUtils.mult(dir, -1); // make sure inner product of direction and gradient > 0
      double[] betaCnd = _beta.clone();
      ArrayUtils.add(betaCnd, dir);
      OptimizationUtils.ExactLineSearch ls = new OptimizationUtils.ExactLineSearch(betaCnd, _state, Arrays.asList(_coefNames));
      ls.findAlpha(null, null, _state, null, null, _ginfo);
    } catch(AssertionError ex) {
      Assert.assertTrue(ex.getMessage().contains("direction is not an descent direction!"));
    }
  }

  @After
  public void teardown() {
    Scope.exit();
  }
  
  @Test
  public void testWolfeConditions() {
    double[] dir = _generator.doubles(_coefNames.length + 1, -1, 1).toArray();
    if (ArrayUtils.innerProduct(dir, _state.ginfo()._gradient) > 0)
      ArrayUtils.mult(dir, -1); // make sure inner product of direction and gradient <= 0
    double[] betaCnd = _beta.clone();
    ArrayUtils.add(betaCnd, dir);
    OptimizationUtils.ExactLineSearch ls = new OptimizationUtils.ExactLineSearch(betaCnd, _state, Arrays.asList(_coefNames));
    GLM.GLMGradientInfo newGrad =  calGradient(betaCnd, _state, _ginfo, null, null, null, null);
    boolean firstWolfe = ls.evaluateFirstWolfe(newGrad);
    Assert.assertTrue("First Wolfe should equal to objective condition but is not.", 
            firstWolfe==(newGrad._objVal <= _state.ginfo()._objVal));
    boolean secondWolfe = ls.evaluateSecondWolfe(newGrad);
    Assert.assertFalse("Second Wolfe should equal condition on gradient info but is not!", 
            secondWolfe == (ArrayUtils.innerProduct(newGrad._gradient, ls._direction) < ls._currGradDirIP));
  }

  @Test
  public void testFindAlphai() {
    // direction is exactly the same as the gradient
    assertAlphai(_state.ginfo()._gradient.clone(), 5000);
    // random directions with different magnitude distributions
    assertAlphai(_generator.doubles(_coefNames.length + 1, -1, 1).toArray(), 20);
    assertAlphai(_generator.doubles(_coefNames.length + 1, -2, 2).toArray(), 200);
    assertAlphai(_generator.doubles(_coefNames.length + 1, -5, 5).toArray(), 2000);
  }
  
  public void assertAlphai(double[] dir, int maxiter) {
    if (ArrayUtils.innerProduct(dir, _state.ginfo()._gradient) > 0)
      ArrayUtils.mult(dir, -1); // make sure inner product of direction and gradient <= 0
    double[] betaCnd = _beta.clone();
    ArrayUtils.add(betaCnd, dir);
    OptimizationUtils.ExactLineSearch ls = new OptimizationUtils.ExactLineSearch(betaCnd, _state, Arrays.asList(_coefNames));
    ls._maxIteration = maxiter;
    boolean foundAlpha = ls.findAlpha(null, null, _state, null, 
            null, _ginfo);
    // new ExactLineSearch with newly found beta.  If foundAlpha = true, WolfeConditions on ls2 should be both true.
    // If foundAlpha is false, then at least one of the WolfeConditions is false
    if (foundAlpha)
      betaCnd = ls._newBeta;
    OptimizationUtils.ExactLineSearch ls2 = new OptimizationUtils.ExactLineSearch(betaCnd, _state, Arrays.asList(_coefNames));
    GLM.GLMGradientInfo newGrad = calGradient(betaCnd, _state, _ginfo, null, null, null,
            null);
    Assert.assertTrue("Wolfe conditions and foundAlphai disagree!", 
            foundAlpha == (ls2.evaluateFirstWolfe(newGrad) && ls2.evaluateSecondWolfe(newGrad)));
  }
}
