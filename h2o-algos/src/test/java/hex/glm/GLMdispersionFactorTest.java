package hex.glm;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import static hex.glm.GLMModel.GLMParameters.DispersionMethod.ml;
import static hex.glm.GLMModel.GLMParameters.DispersionMethod.pearson;
import static hex.glm.GLMModel.GLMParameters.Family.gamma;
import static hex.glm.GLMModel.GLMParameters;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMdispersionFactorTest extends TestUtil {
    @Test
    public void testGammaDispersionFactorEstimation() {
        // test for dispersion factor = 0.5, 9
        assertCorrectDispersionFactor("smalldata/glm_test/gamma_dispersion_0p5_10KRows.csv",
                0.5, gamma, "resp", 0.012);
        assertCorrectDispersionFactor("smalldata/glm_test/gamma_dispersion_factor_9_10kRows.csv", 
                9, gamma, "resp", 0.034);

    }

    public static void assertCorrectDispersionFactor(String filename, double trueDispersionFactor, 
                                                     GLMModel.GLMParameters.Family family, String response, double eps) {
        Scope.enter();
        try {
            final Frame train = parseAndTrackTestFile(filename);
            GLMParameters params = new GLMParameters(family);
            params._response_column = response;
            params._train = train._key;
            params._compute_p_values = true;
            params._dispersion_factor_method = ml;
            params._family = family;
            params._lambda = new double[]{0.0};
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            
            params._dispersion_factor_method = pearson;
            GLMModel glmPearson = new GLM(params).trainModel().get();
            Scope.track_generic(glmPearson);
            assert Math.abs(glmML._output.dispersion()-trueDispersionFactor)<eps:
                    "True dispersion: " + trueDispersionFactor + " H2O dispersion: " + glmML._output.dispersion() +
                             ".  H2O dispersion parameter estimation is too far from true value";
        } finally {
            Scope.exit();
        }
    }
}
