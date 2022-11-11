package hex.glm;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.*;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.stream.Collectors;

import static hex.glm.GLMBasicTestBinomial.genZValues;
import static hex.glm.GLMBasicTestBinomial.manualGenSE;
import static hex.glm.GLMModel.GLMParameters.Family.*;
import static org.junit.Assert.*;

/***
 * Test the implementation of p-value computation in GLM when regularization is enabled.
 */
@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMPValuesWithRegularizationTest extends TestUtil {

  /**
   * Test p-values, z-values and standard error calculations for binomial family with regularization.
   */
  @Test
  public void testBinomial() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = binomial;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._ignored_columns = ignoreCols;
      parms._compute_p_values = true;
      parms._lambda = new double[]{0.7, 0.5}; // largest lambda at the beginning
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      assert model != null;
      System.out.println(model._output._submodels.length);
      // manually calculate standard error for final model and for both submodels
      double[] standardErr = manualGenSE(bigFrame, model._output.beta()); // redundant, calculate just for control
      double[] standardErrSub0 = manualGenSE(bigFrame, model._output._submodels[0].beta);
      double[] standardErrSub1 = manualGenSE(bigFrame, model._output._submodels[1].beta);
      // assert final model
      assertStandardErr(model, standardErr, 1, 1e-1, 1e-2, 1e-2);
      // assert first submodel
      assertStandardErrForSubmodels(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub0, 1, 1e-2, 1e-2, 1e-2);
      // assert second submodel
      assertStandardErrForSubmodels(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub1, 1, 1e-2, 1e-2, 1e-2);
    } finally {
      Scope.exit();
    }
  }

  /**
   * Test p-values, z-values and standard error calculations for binomial family with regularization and cross validation.
   */
  @Test
  public void testBinomialWithCrossValidation() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = binomial;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._ignored_columns = ignoreCols;
      parms._compute_p_values = true;
      parms._lambda = new double[]{0.7, 0.5}; // largest lambda at the beginning
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      parms._nfolds = 3;
      GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      assert model != null;
      System.out.println(model._output._submodels.length);
      // manually calculate standard error for final model and for both submodels
      double[] standardErr = manualGenSE(bigFrame, model._output.beta()); // redundant, calculate just for control
      double[] standardErrSub0 = manualGenSE(bigFrame, model._output._submodels[0].beta);
      // assert final model
      assertStandardErr(model, standardErr, 1, 1e-1, 1e-2, 1e-2);
      // assert first submodel
      assertStandardErrForSubmodels(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub0, 1, 1e-2, 1e-2, 1e-2);
    } finally {
      Scope.exit();
    }
  }

  /**
   * Test p-values, z-values and standard error calculations for binomial family with regularization.
   */
  @Test
  public void testBinomialLambdaSearchAndCrossValidation() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = binomial;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._ignored_columns = ignoreCols;
      parms._compute_p_values = true;
      parms._lambda_search = true;
      parms._nlambdas = 2;
      parms._standardize = false; // has to be false for calculating p-values
      parms._nfolds = 5;
      parms._keep_cross_validation_models = false;
      GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      assert model != null;
      System.out.println(model._output._submodels.length);
      System.out.println(Arrays.stream(model._output._submodels).map(a -> a.lambda_value + "-" + a.alpha_value).collect(Collectors.joining(", ")));
      // manually calculate standard error for final model and for both submodels
      double[] standardErr = manualGenSE(bigFrame, model._output.beta()); // redundant, calculate just for control
      double[] standardErrSub0 = manualGenSE(bigFrame, model._output._submodels[0].beta);
      double[] standardErrSub1 = manualGenSE(bigFrame, model._output._submodels[1].beta);
      assertStandardErrNotEmpty(model);
      // assert final model
      assertStandardErr(model, standardErr, 1, 1e1, 1e-1, 1e-1);
      // assert first submodel
      assertStandardErrForSubmodels(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub0, 1, 1e-2, 1e-2, 1e-2);
      // assert second submodel
      assertStandardErrForSubmodels(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub1, 1, 1e1, 1e-2, 1e-2);
    } finally {
      Scope.exit();
    }
  }

  /***
   * Test p-values, z-values and standard error calculations for gaussian family with regularization.
   */
  @Test
  public void testGaussian() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_gaussian_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = gaussian;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._ignored_columns = ignoreCols;
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._alpha = new double[]{0.5, 0.7};
      parms._lambda_search = true;
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      assert model != null;
      Scope.track_generic(model);
      // assert final model
      assertStandardErrNotEmpty(model);
      // assert submodels
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom());
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom());
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[2],
              model._output._training_metrics.residual_degrees_of_freedom());
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[3],
              model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
  }

  /***
   * Test p-values, z-values and standard error calculations for fractionalbinomial family with regularization.
   */
  @Test
  public void testFractionalbinomial() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = fractionalbinomial;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._ignored_columns = ignoreCols;
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._lambda_search = true;
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      assert model != null;
      Scope.track_generic(model);
      // assert final model
      assertStandardErrNotEmpty(model);
      // assert first submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom());
      // assert second submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
  }

  /***
   * Test p-values, z-values and standard error calculations for quasibinomial family with regularization.
   */
  @Test
  public void testQuasibinomial() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = quasibinomial;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._ignored_columns = ignoreCols;
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._lambda_search = true;
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      assert model != null;
      Scope.track_generic(model);
      // assert final model
      assertStandardErrNotEmpty(model);
      // assert first submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom());
      // assert second submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
  }

  /***
   * Test p-values, z-values and standard error calculations for poisson family with regularization.
   */
  @Test
  public void testPoisson() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = poisson;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._compute_p_values = true;
      parms._lambda_search = true;
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      assert model != null;
      Scope.track_generic(model);
      // assert final model
      assertStandardErrNotEmpty(model);
      // assert first submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom());
      // assert second submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
  }

  /***
   * Test p-values, z-values and standard error calculations for gamma family with regularization.
   */
  @Test
  public void testGamma() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/prostate/prostate.csv");
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = gamma;
      parms._train = bigFrame._key;
      parms._response_column = "DPROS";
      parms._max_iterations = 1;
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._alpha = new double[]{0.5, 0.5};
      parms._lambda_search = true;
      parms._standardize = false; // has to be false for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      assert model != null;
      Scope.track_generic(model);
      // assert final model
      assertStandardErrNotEmpty(model);
      // assert first submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom());
      // assert second submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
  }


  /***
   * Test p-values, z-values and standard error calculations for tweedie family with regularization.
   */
  @Test
  public void testTweedie() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = tweedie;
      parms._train = bigFrame._key;
      parms._response_column = "response";
      parms._ignored_columns = ignoreCols;
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      assert model != null;
      Scope.track_generic(model);
      // assert final model
      assertStandardErrNotEmpty(model);
      // assert first submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom());
      // assert second submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
  }


  /***
   * Test p-values, z-values and standard error calculations for negativebinomial family with regularization.
   */
  @Test
  public void testNegativebinomial() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/prostate/prostate.csv");
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = negativebinomial;
      parms._train = bigFrame._key;
      parms._response_column = "DPROS";
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._standardize = false; // has to be false for calculating p-values
      parms._use_all_factor_levels = true;
      parms._theta = 0.5;
      GLMModel model = new GLM(parms).trainModel().get();
      assert model != null;
      Scope.track_generic(model);
      // assert final model
      assertStandardErrNotEmpty(model);
      // assert first submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom());
      // assert second submodel
      assertStandardErrForSubmodelsNotEmpty(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
  }


  /**
   * Check if z-values, standard error and p-values for the given model are computed - fields are not null
   * and arrays are not empty.
   *
   * @param model model to check
   */
  private void assertStandardErrNotEmpty(GLMModel model) {
    double[] zValues = model._output.zValues();
    double[] stdErr = model._output.stdErr();
    double[] pValues = model._output.pValues();
    assertNotNull(zValues);
    assertTrue(zValues.length > 0);
    assertNotNull(stdErr);
    assertTrue(stdErr.length > 0);
    assertNotNull(pValues);
    assertTrue(pValues.length > 0);
  }

  /**
   * Check if z-values, standard error and p-values for the given submodel are computed - fields are not null
   * and arrays are not empty.
   *
   * @param submodel submodel to check
   */
  private void assertStandardErrForSubmodelsNotEmpty(GLMModel.Submodel submodel, long residualDegreesOfFreedom) {
    double[] zValues = submodel.zValues();
    double[] stdErr = submodel.stdErr();
    double[] pValues = submodel.pValues(residualDegreesOfFreedom);
    System.out.println(Arrays.toString(zValues));
    assertNotNull(zValues);
    assertTrue(zValues.length > 0);
    assertNotNull(stdErr);
    assertTrue(stdErr.length > 0);
    assertNotNull(pValues);
    assertTrue(pValues.length > 0);
  }

  /**
   * Assert z-values, standard error and p-values for the given model.
   *
   * @param model        trained model
   * @param manualStdErr manually computed standard error
   * @param reg
   * @param tot1         delta for z-values
   * @param tot2         delta for standard errors
   * @param tot3         delta for p-values
   */
  public static void assertStandardErr(GLMModel model, double[] manualStdErr, double reg, double tot1, double tot2,
                                       double tot3) {
    double[] zValues = model._output.zValues();
    double[] stdErr = model._output.stdErr();
    double[] pValues = model._output.pValues();
    double[] zValuesManual = genZValues(model._output.beta(), manualStdErr, reg);
    double[] pValuesManual = genPValues(zValuesManual, model._output.dispersionEstimated(),
            model._output._training_metrics.residual_degrees_of_freedom());
    // check equality ignoring NaN values as they are caused by zero betta values
    for (int i = 0; i < stdErr.length; i++) {
      if (!Double.isNaN(stdErr[i])) {
        assertEquals(zValuesManual[i], zValues[i], tot1);
        assertEquals(pValuesManual[i], pValues[i], tot2);
        assertEquals(manualStdErr[i], stdErr[i], tot3);
      }
    }
  }

  /**
   * Assert z-values, standard error and p-values for the given submodel.
   *
   * @param submodel                 trained submodel
   * @param residualDegreesOfFreedom residual degrees of freedom for the model
   * @param manualStdErr             manually computed standard error
   * @param reg
   * @param tot1                     delta for z-values
   * @param tot2                     delta for standard errors
   * @param tot3                     delta for p-values
   */
  public static void assertStandardErrForSubmodels(GLMModel.Submodel submodel, long residualDegreesOfFreedom,
                                                   double[] manualStdErr, double reg, double tot1, double tot2, double tot3) {
    double[] zValues = submodel.zValues();
    double[] stdErr = submodel.stdErr();
    double[] pValues = submodel.pValues(residualDegreesOfFreedom);
    double[] zValuesManual = genZValues(submodel.beta, manualStdErr, reg);
    double[] pValuesManual = genPValues(zValuesManual, submodel.dispersionEstimated, residualDegreesOfFreedom);
    // check equality ignoring NaN values as they are caused by zero betta values
    for (int i = 0; i < stdErr.length; i++) {
      if (!Double.isNaN(stdErr[i])) {
        assertEquals(zValuesManual[i], zValues[i], tot1);
        assertEquals(pValuesManual[i], pValues[i], tot2);
        assertEquals(manualStdErr[i], stdErr[i], tot3);
      }
    }
  }

  /**
   * Manually generate p-values from given z-values.
   *
   * @param zValues                  z-values
   * @param dispersionEstimated      flag for selecting the distribution
   * @param residualDegreesOfFreedom degrees of freedom if the dispersionEstimated is true
   * @return list of p-values
   */
  public static double[] genPValues(double[] zValues, boolean dispersionEstimated, long residualDegreesOfFreedom) {
    RealDistribution gaussian = dispersionEstimated
            ? new TDistribution(residualDegreesOfFreedom)
            : new NormalDistribution();
    double[] pValues = new double[zValues.length];
    for (int index = 0; index < zValues.length; index++)
      pValues[index] = 2 * gaussian.cumulativeProbability(-Math.abs(zValues[index]));
    return pValues;
  }
}
