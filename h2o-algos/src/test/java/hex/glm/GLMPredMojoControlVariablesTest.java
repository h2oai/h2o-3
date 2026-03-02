package hex.glm;

import hex.CreateFrame;
import hex.SplitFrame;
import hex.api.MakeGLMModelHandler;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.schemas.MakeUnrestrictedGLMModelV3;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.api.schemas3.KeyV3;
import water.fvec.Frame;
import water.fvec.Vec;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Random;

/**
 * Tests MOJO/POJO scoring for GLM models with control variables across all supported families.
 * For each family, verifies that both restricted and unrestricted models produce correct
 * MOJO/POJO predictions and that their predictions differ from each other.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMPredMojoControlVariablesTest extends TestUtil {

  private static final long SEED = 42L;
  double _tol = 1e-6;

  @Test
  public void testGaussianPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(1, true, false, "gaussian");
      GLMParameters params = createCommonParams(Family.gaussian, data[0]);
      checkMojoRestrictedAndUnrestricted(params, data[1], "gaussian");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testPoissonPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(20, false, true, "poisson");
      GLMParameters params = createCommonParams(Family.poisson, data[0]);
      checkMojoRestrictedAndUnrestricted(params, data[1], "poisson");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGammaPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(1, true, false, "gamma");
      GLMParameters params = createCommonParams(Family.gamma, data[0]);
      checkMojoRestrictedAndUnrestricted(params, data[1], "gamma");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTweediePredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(1, true, false, "tweedie");
      GLMParameters params = createCommonParams(Family.tweedie, data[0]);
      params._tweedie_variance_power = 1.5;
      params._tweedie_link_power = 1 - 1.5;
      checkMojoRestrictedAndUnrestricted(params, data[1], "tweedie");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testNegativeBinomialPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(20, false, true, "negativebinomial");
      GLMParameters params = createCommonParams(Family.negativebinomial, data[0]);
      params._theta = 0.5;
      checkMojoRestrictedAndUnrestricted(params, data[1], "negativebinomial");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testFractionalBinomialPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(2, true, true, "fractionalbinomial");
      GLMParameters params = createCommonParams(Family.fractionalbinomial, data[0]);
      checkMojoRestrictedAndUnrestricted(params, data[1], "fractionalbinomial");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testBinomialPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(2, false, false, "binomial");
      GLMParameters params = createCommonParams(Family.binomial, data[0]);
      checkMojoRestrictedAndUnrestricted(params, data[1], "binomial");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testGaussianStandardizedPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(1, true, false, "gaussian_std");
      GLMParameters params = createCommonParams(Family.gaussian, data[0]);
      params._standardize = true;
      checkMojoRestrictedAndUnrestricted(params, data[1], "gaussian_std");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testBinomialStandardizedPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(2, false, false, "binomial_std");
      GLMParameters params = createCommonParams(Family.binomial, data[0]);
      params._standardize = true;
      checkMojoRestrictedAndUnrestricted(params, data[1], "binomial_std");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTweedieStandardizedPredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData(1, true, false, "tweedie_std");
      GLMParameters params = createCommonParams(Family.tweedie, data[0]);
      params._tweedie_variance_power = 1.5;
      params._tweedie_link_power = 1 - 1.5;
      params._standardize = true;
      checkMojoRestrictedAndUnrestricted(params, data[1], "tweedie_std");
    } finally {
      Scope.exit();
    }
  }

  /**
   * Tests that a model trained with a validation frame and control variables
   * still produces correct MOJO/POJO predictions. The validation frame exercises
   * a separate code path in scorePostProcessing / scorePostProcessingControlVal.
   */
  @Test
  public void testGaussianWithValidationFramePredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData3Way(1, true, false, "gaussian_valid");
      GLMParameters params = createCommonParams(Family.gaussian, data[0]);
      params._valid = data[1]._key;
      params._generate_scoring_history = true;
      checkMojoRestrictedAndUnrestricted(params, data[2], "gaussian_valid");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testTweedieWithValidationFramePredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData3Way(1, true, false, "tweedie_valid");
      GLMParameters params = createCommonParams(Family.tweedie, data[0]);
      params._tweedie_variance_power = 1.5;
      params._tweedie_link_power = 1 - 1.5;
      params._valid = data[1]._key;
      params._generate_scoring_history = true;
      checkMojoRestrictedAndUnrestricted(params, data[2], "tweedie_valid");
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testBinomialWithValidationFramePredMojoControlVariables() {
    try {
      Scope.enter();
      Frame[] data = createAndSplitData3Way(2, false, false, "binomial_valid");
      GLMParameters params = createCommonParams(Family.binomial, data[0]);
      params._valid = data[1]._key;
      params._generate_scoring_history = true;
      checkMojoRestrictedAndUnrestricted(params, data[2], "binomial_valid");
    } finally {
      Scope.exit();
    }
  }

  /**
   * Creates a synthetic dataset and splits it 80/20 into train/test frames.
   */
  private Frame[] createAndSplitData(int responseFactors, boolean positiveResponse,
                                     boolean convertResponseToNumeric, String suffix) {
    CreateFrame cf = new CreateFrame();
    Random generator = new Random(SEED);
    int numRows = generator.nextInt(10000) + 15000 + 200;
    int numCols = generator.nextInt(17) + 3;
    cf.rows = numRows;
    cf.cols = numCols;
    cf.factors = 10;
    cf.has_response = true;
    cf.response_factors = responseFactors;
    cf.positive_response = positiveResponse;
    cf.missing_fraction = 0;
    cf.seed = SEED;

    Frame trainData = cf.execImpl().get();
    if (convertResponseToNumeric) {
      Vec v = trainData.remove("response");
      trainData.add("response", v.toNumericVec());
      Scope.track(v);
      DKV.put(trainData);
    }
    Scope.track(trainData);

    SplitFrame sf = new SplitFrame(trainData, new double[]{0.8, 0.2},
            new Key[]{Key.make("train_" + suffix + ".hex"), Key.make("test_" + suffix + ".hex")});
    sf.exec().get();
    Key[] ksplits = sf._destination_frames;
    Frame tr = DKV.get(ksplits[0]).get();
    Frame te = DKV.get(ksplits[1]).get();
    Scope.track(tr);
    Scope.track(te);
    return new Frame[]{tr, te};
  }

  /**
   * Creates a synthetic dataset and splits it 60/20/20 into train/valid/test frames.
   */
  private Frame[] createAndSplitData3Way(int responseFactors, boolean positiveResponse,
                                         boolean convertResponseToNumeric, String suffix) {
    CreateFrame cf = new CreateFrame();
    Random generator = new Random(SEED);
    int numRows = generator.nextInt(10000) + 15000 + 200;
    int numCols = generator.nextInt(17) + 3;
    cf.rows = numRows;
    cf.cols = numCols;
    cf.factors = 10;
    cf.has_response = true;
    cf.response_factors = responseFactors;
    cf.positive_response = positiveResponse;
    cf.missing_fraction = 0;
    cf.seed = SEED;

    Frame trainData = cf.execImpl().get();
    if (convertResponseToNumeric) {
      Vec v = trainData.remove("response");
      trainData.add("response", v.toNumericVec());
      Scope.track(v);
      DKV.put(trainData);
    }
    Scope.track(trainData);

    SplitFrame sf = new SplitFrame(trainData, new double[]{0.6, 0.2, 0.2},
            new Key[]{Key.make("train_" + suffix + ".hex"), Key.make("valid_" + suffix + ".hex"),
                    Key.make("test_" + suffix + ".hex")});
    sf.exec().get();
    Key[] ksplits = sf._destination_frames;
    Frame tr = DKV.get(ksplits[0]).get();
    Frame va = DKV.get(ksplits[1]).get();
    Frame te = DKV.get(ksplits[2]).get();
    Scope.track(tr);
    Scope.track(va);
    Scope.track(te);
    return new Frame[]{tr, va, te};
  }

  private GLMParameters createCommonParams(Family family, Frame tr) {
    GLMParameters params = new GLMParameters(family, family.defaultLink,
            new double[]{0}, new double[]{0}, 0, 0);
    params._train = tr._key;
    params._lambda_search = false;
    params._response_column = "response";
    params._lambda = new double[]{0};
    params._alpha = new double[]{0.001};
    params._objective_epsilon = 1e-6;
    params._beta_epsilon = 1e-4;
    params._standardize = false;
    params._control_variables = new String[]{"C1", "C2"};
    return params;
  }

  /**
   * Tests both the restricted (control variables active) and unrestricted models:
   * 1. Restricted model: MOJO/POJO predictions match H2O predictions
   * 2. Unrestricted model: MOJO/POJO predictions match H2O predictions
   * 3. Restricted vs unrestricted predictions differ
   */
  private void checkMojoRestrictedAndUnrestricted(GLMParameters params, Frame te, String suffix) {
    // Train and check restricted model (control variables zeroed during scoring)
    GLMModel restrictedModel = new GLM(params).trainModel().get();
    Scope.track_generic(restrictedModel);
    Frame predRestricted = restrictedModel.score(te);
    Scope.track(predRestricted);
    Assert.assertTrue(restrictedModel.haveMojo());
    Assert.assertTrue(restrictedModel.testJavaScoring(te, predRestricted, _tol));

    // Create and check unrestricted model (control variable betas included in scoring)
    GLMModel unrestrictedModel = createUnrestrictedModel(restrictedModel, suffix);
    Scope.track_generic(unrestrictedModel);
    Frame predUnrestricted = unrestrictedModel.score(te);
    Scope.track(predUnrestricted);
    Assert.assertTrue(unrestrictedModel.haveMojo());
    Assert.assertTrue(unrestrictedModel.testJavaScoring(te, predUnrestricted, _tol));

    // Restricted and unrestricted predictions must differ
    assertPredictionsDiffer(predRestricted, predUnrestricted);
  }

  private GLMModel createUnrestrictedModel(GLMModel model, String suffix) {
    MakeGLMModelHandler handler = new MakeGLMModelHandler();
    MakeUnrestrictedGLMModelV3 args = new MakeUnrestrictedGLMModelV3();
    args.model = new KeyV3.ModelKeyV3(model._key);
    args.dest = "unrestricted_" + suffix;
    handler.make_unrestricted_model(3, args);
    return DKV.getGet(Key.make("unrestricted_" + suffix));
  }

  /**
   * Asserts that at least 10% of prediction values differ between two prediction frames.
   * Uses the probability column for classification (vec index 1) and the predict column
   * for regression (vec index 0).
   */
  private void assertPredictionsDiffer(Frame pred1, Frame pred2) {
    int colIdx = pred1.numCols() > 1 ? 1 : 0;
    long nrows = Math.min(pred1.numRows(), 100);
    int differ = 0;
    for (int i = 0; i < nrows; i++) {
      if (Math.abs(pred1.vec(colIdx).at(i) - pred2.vec(colIdx).at(i)) > 1e-10) differ++;
    }
    Assert.assertTrue("Restricted and unrestricted predictions should differ (only " +
            differ + "/" + nrows + " rows differed)", differ > nrows / 10);
  }
}
