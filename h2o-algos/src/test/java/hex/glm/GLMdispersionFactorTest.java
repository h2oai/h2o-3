package hex.glm;

import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import static hex.glm.GLMModel.GLMParameters.DispersionMode.ML;
import static hex.glm.GLMModel.GLMParameters.Family.gamma;
import static hex.glm.GLMModel.GLMParameters.Link.log;
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
            params._dispersion_factor_mode = ML;
            params._family = family;
            params._lambda = new double[]{0.0};
            GLMModel glm = new GLM(params).trainModel().get();
            Scope.track_generic(glm);
            assert Math.abs(glm._output.dispersion()-trueDispersionFactor)<Math.abs(rDispersion-trueDispersionFactor):
                    "True dispersion: " + trueDispersionFactor + " H2O dispersion: " + glm._output.dispersion() +
                            " and R dispersion " + rDispersion + ".  H2O performance is worse than R.";
            
        } finally {
            Scope.exit();
        }
    }
}
