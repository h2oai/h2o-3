package hex.glm;

import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import static hex.glm.GLMModel.GLMParameters.DispersionMode.ml;
import static hex.glm.GLMModel.GLMParameters.DispersionMode.pearson;
import static hex.glm.GLMModel.GLMParameters.Family.gamma;
import static hex.glm.GLMModel.GLMParameters;

public class GLMdispersionFactorTest extends TestUtil {
    @Test
    public void testGammaDispersionFactorEstimation() {
        // test for dispersion factor = 0.5, 9
        assertCorrectDispersionFactor("smalldata/glm_test/gamma_dispersion_0p5_10KRows.csv",
                0.5, 0.51185, gamma);
        assertCorrectDispersionFactor("smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv", 
                9, 9.3, gamma);

    }

    public static void assertCorrectDispersionFactor(String filename, double trueDispersionFactor, double rDispersion,
                                                     GLMModel.GLMParameters.Family family) {
        Scope.enter();
        try {
            final Frame train = parseAndTrackTestFile(filename);
            GLMParameters params = new GLMParameters(family);
            params._response_column = "newResponse";
            params._train = train._key;
            params._compute_p_values = true;
            params._dispersion_factor_mode = ml;
            params._family = family;
            params._lambda = new double[]{0.0};
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            
            params._dispersion_factor_mode = pearson;
            GLMModel glmPearson = new GLM(params).trainModel().get();
            Scope.track_generic(glmPearson);
            assert Math.abs(glmML._output.dispersion()-trueDispersionFactor)<=Math.abs(rDispersion-trueDispersionFactor):
                    "True dispersion: " + trueDispersionFactor + " H2O dispersion: " + glmML._output.dispersion() +
                            " and R dispersion " + rDispersion + ".  H2O performance is worse than R.";
            assert Math.abs(glmML._output.dispersion()-trueDispersionFactor)<=Math.abs(glmPearson._output.dispersion()-trueDispersionFactor):
                    "True dispersion: " + trueDispersionFactor + " H2O ml dispersion: " + glmML._output.dispersion() +
                            " and H2O pearson dispersion " + glmPearson._output.dispersion() + ".  H2O ml performance is worse than H2O pearson.";            
        } finally {
            Scope.exit();
        }
    }
}
