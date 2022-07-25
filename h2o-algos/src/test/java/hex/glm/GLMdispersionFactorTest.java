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
import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.Family.*;

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
    
    @Test
    public void testGammaNullModel() {
        assertCorrectNullModel("smalldata/glm_test/gamma_dispersion_0p5_10KRows.csv",
                0.5, gamma, "resp", 0.012);

        // uncomment this section after this is enabled for tweedie and negativebinomial families.
/*        assertCorrectNullModel("smalldata/glm_test/gamma_dispersion_0p5_10KRows.csv",
                0.5, tweedie, "resp", 0.012);

        assertCorrectNullModel("smalldata/glm_test/gamma_dispersion_0p5_10KRows.csv",
                0.5, negativebinomial, "resp", 0.012);*/
    }

    public static void assertCorrectNullModel(String filename, double trueDispersionFactor,
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
            params._build_null_model = true;
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            double[] mlCoeff = glmML.beta();
            assert mlCoeff.length==1 : "GLM model should only contain one element which is just the intercept.";
            assert glmML.coefficients().keySet().contains("Intercept"); // the one coefficient is for intercept

            params._dispersion_factor_method = pearson;
            GLMModel glmPearson = new GLM(params).trainModel().get();
            Scope.track_generic(glmPearson);
            double[] pearsonCoeff = glmPearson.beta();
            assert pearsonCoeff.length==1 : "GLM model should only contain one element which is just the intercept.";
            assert glmPearson.coefficients().keySet().contains("Intercept");
            TestUtil.equalTwoArrays(mlCoeff, pearsonCoeff, 1e-6);
        } finally {
            Scope.exit();
        }
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
