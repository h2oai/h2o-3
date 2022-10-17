package hex.glm;


import hex.SplitFrame;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import java.util.Arrays;
import java.util.stream.Collectors;

import static hex.glm.GLMBasicTestBinomial.genZValues;
import static hex.glm.GLMBasicTestBinomial.manualGenSE;
import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static org.junit.Assert.assertArrayEquals;

/***
 * Test the implementation of p-value computation in GLM.
 */
public class GLMPValuesTest extends TestUtil {

  /**
   * Test p-values, z-values and standard error calculations for binomial family with regularization.
   */
  @Test
  public void testBinomialWithRegularization() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      SplitFrame sf = new SplitFrame(bigFrame, new double[]{0.01, 0.99}, null);
      sf.exec().get();
      Key[] splits = sf._destination_frames;
      Frame trainFrame = Scope.track((Frame) splits[0].get());
      Scope.track((Frame) splits[1].get());
      double[] startVal = new double[]{0.07453275580005079, 0.064848157889351, 0.06002544346079828, 0.08681890882639597,
              0.08383870319271398, 0.08867949974556715, 0.007576417746370567, 0.05373550607913393, 0.0005879217569412454,
              0.08942772492726005, 0.03461378283678047};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = binomial;
      parms._train = trainFrame._key;
      parms._response_column = "response";
      parms._startval = startVal;
      parms._ignored_columns = ignoreCols;
      parms._max_iterations = 1;
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._alpha = new double[]{0.5, 0.5};
      parms._lambda_search = true;
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      // manually calculate standard error for final model and for both submodels
      double[] standardErr = manualGenSE(trainFrame, model._output.beta()); // redundant, calculate just for control
      double[] standardErrSub0 = manualGenSE(trainFrame, model._output._submodels[0].beta);
      double[] standardErrSub1 = manualGenSE(trainFrame, model._output._submodels[1].beta);
      // assert final model
      assertStandardErr(model, standardErr, 1, 1e-2, 1e-2, 1e-2);
      // assert first submodel
      assertStandardErrForSubmodels(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub0, 1, 1e-3, 1e-4, 1e-2);
      // assert second submodel
      assertStandardErrForSubmodels(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub1, 1, 1e-3, 1e-4, 1e-2);
      // 0.024351, 0.994726, 0.076867, 0.215071, 0.058557, 0.094051, 0.229239, 0.069782, 0.274645, 0.075530, 0.204998
    } finally {
      Scope.exit();
    }
  }

  /***
   * Test p-values, z-values and standard error calculations for binomial family with regularization.
   */
  @Ignore
  @Test
  public void testGaussianWithRegularization() {
    Scope.enter();
    try {
      Frame bigFrame = parseAndTrackTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv");
      String[] ignoreCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"};
      SplitFrame sf = new SplitFrame(bigFrame, new double[]{0.01, 0.99}, null);
      sf.exec().get();
      Key[] splits = sf._destination_frames;
      Frame trainFrame = Scope.track((Frame) splits[0].get());
      Scope.track((Frame) splits[1].get());
      double[] startVal = new double[]{0.07453275580005079, 0.064848157889351, 0.06002544346079828, 0.08681890882639597,
              0.08383870319271398, 0.08867949974556715, 0.007576417746370567, 0.05373550607913393, 0.0005879217569412454,
              0.08942772492726005, 0.03461378283678047};
      GLMModel.GLMParameters parms = new GLMModel.GLMParameters();
      parms._family = gaussian;
      parms._train = trainFrame._key;
      parms._response_column = "response";
      parms._startval = startVal;
      parms._ignored_columns = ignoreCols;
      parms._max_iterations = 1;
      parms._compute_p_values = true;
      parms._lambda = new double[]{1.0, 0.5}; // largest lambda at the beginning
      parms._alpha = new double[]{0.5, 0.5};
      parms._lambda_search = true;
      parms._standardize = false; // has to be false for calculating p-values
      parms._remove_collinear_columns = true; // has to be true for calculating p-values
      GLMModel model = new GLM(parms).trainModel().get();
      Scope.track_generic(model);
      // manually calculate standard error for final model and for both submodels
      double[] standardErr = manualGenSE(trainFrame, model._output.beta()); // redundant, calculate just for control
      double[] standardErrSub0 = manualGenSE(trainFrame, model._output._submodels[0].beta);
      double[] standardErrSub1 = manualGenSE(trainFrame, model._output._submodels[1].beta);
      // assert final model
      assertStandardErr(model, standardErr, 1, 1e-2, 1e-2, 1e-2);
      // assert first submodel
      assertStandardErrForSubmodels(model._output._submodels[0],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub0, 1, 1e-3, 1e-4, 1e-2);
      // assert second submodel
      assertStandardErrForSubmodels(model._output._submodels[1],
              model._output._training_metrics.residual_degrees_of_freedom(),
              standardErrSub1, 1, 1e-3, 1e-4, 1e-2);
      System.out.println(model._output._training_metrics.residual_degrees_of_freedom());
    } finally {
      Scope.exit();
    }
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
    System.out.println("pValuesManual: " + Arrays.stream(pValuesManual)
            .mapToObj(d -> String.format("%.6f", d)).collect(Collectors.joining(", ")));
    System.out.println("pValues:       " + Arrays.stream(pValues)
            .mapToObj(d -> String.format("%.6f", d)).collect(Collectors.joining(", ")));
    assertArrayEquals(zValuesManual, zValues, tot1);
    assertArrayEquals(manualStdErr, stdErr, tot2);
    assertArrayEquals(pValuesManual, pValues, tot3);
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
    System.out.println("pValuesManual: " +
            Arrays.stream(pValuesManual).mapToObj(d -> String.format("%.6f", d)).collect(Collectors.joining(", ")));
    System.out.println("pValues:       " +
            Arrays.stream(pValues).mapToObj(d -> String.format("%.6f", d)).collect(Collectors.joining(", ")));
    assertArrayEquals(zValuesManual, zValues, tot1);
    assertArrayEquals(manualStdErr, stdErr, tot2);
    assertArrayEquals(pValuesManual, pValues, tot3);
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


  /**
   * Setup before test
   */
  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
}
