package hex.glm;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.DispersionMethod.*;
import static hex.glm.GLMModel.GLMParameters.Family.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMdispersionFactorTest extends TestUtil {

    @Test
    public void testDevianceSEGaussian() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/glm_test/gaussian_4col_10KRows_train.csv");
            GLMParameters parms = new GLMParameters();
            parms._train = train._key;
            parms._lambda = new double[]{0};
            parms._response_column = train.name(4);
            parms._dispersion_parameter_method = deviance;
            parms._family = GLMParameters.Family.gaussian;
            parms._compute_p_values = true;
            GLMModel modelDev = new GLM(parms).trainModel().get();
            Scope.track_generic(modelDev);
            parms._dispersion_parameter_method = pearson;
            parms._useDispersion1 = false;
            GLMModel modelP = new GLM(parms).trainModel().get();
            Scope.track_generic(modelP);
            // for gaussian, deviance and pearson should produce the same standard error
            double[] pearsonSE = modelP._output.stdErr();
            double[] devianceSE = modelDev._output.stdErr();
            Assert.assertTrue(TestUtil.equalTwoArrays(pearsonSE, devianceSE, 1e-6));
            Assert.assertTrue(Math.abs(modelP._output.dispersion()-modelDev._output.dispersion())<1e-6);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testDevianceSEBinomial() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/glm_test/binomial_20_cols_10KRows.csv");
            List<String> colNames = Arrays.stream(train.names()).collect(Collectors.toList());
            String[] catCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10", "C21"};
            Arrays.stream(catCols).forEach(x -> {
                int colInd = colNames.indexOf(x);
                train.replace(colInd, train.vec(x).toCategoricalVec()).remove();
            });
            DKV.put(train);
            GLMParameters parms = new GLMParameters();
            parms._train = train._key;
            parms._lambda = new double[]{0};
            parms._response_column = train.name(20);
            parms._dispersion_parameter_method = deviance;
            parms._family = GLMParameters.Family.binomial;
            parms._compute_p_values = true;
            GLMModel modelDev = new GLM(parms).trainModel().get();
            Scope.track_generic(modelDev);
            parms._dispersion_parameter_method = pearson;
            parms._useDispersion1 = false;
            GLMModel modelP = new GLM(parms).trainModel().get();
            Scope.track_generic(modelP);
            // should generate the same standard error since our dataset is binomial
            double[] pearsonSE = modelP._output.stdErr();
            double[] devianceSE = modelDev._output.stdErr();
            Assert.assertTrue(TestUtil.equalTwoArrays(pearsonSE, devianceSE, 1e-6));
            Assert.assertTrue(Math.abs(modelP._output.dispersion()-modelDev._output.dispersion())< 1e-6);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testDevianceSEGamma() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/glm_test/gamma_dispersion_0p5_10KRows.csv");
            GLMParameters parms = new GLMParameters();
            parms._train = train._key;
            parms._lambda = new double[]{0};
            parms._response_column = "resp";
            parms._dispersion_parameter_method = deviance;
            parms._family = GLMParameters.Family.gamma;
            parms._compute_p_values = true;
            GLMModel modelDev = new GLM(parms).trainModel().get();
            Scope.track_generic(modelDev);
            parms._dispersion_parameter_method = pearson;
            parms._useDispersion1 = false;
            GLMModel modelP = new GLM(parms).trainModel().get();
            Scope.track_generic(modelP);
            double trueDispersion = 0.5;
            // compare how close the dispersion parameter estimate is.
            Assert.assertTrue(Math.floor(modelP._output.dispersion()*10)/10.0==trueDispersion);
            Assert.assertTrue(Math.floor(modelDev._output.dispersion()*10)/10==trueDispersion);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testDevianceSETweedie() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/glm_test/tweedie_p1p2_phi2_5Cols_10KRows.csv");
            GLMParameters parms = new GLMParameters();
            parms._train = train._key;
            parms._lambda = new double[]{0};
            parms._seed = 12345;
            parms._response_column = "resp";
            parms._dispersion_parameter_method = deviance;
            parms._tweedie_variance_power = 1.2;
            parms._family = GLMParameters.Family.tweedie;
            parms._compute_p_values = true;
            GLMModel modelDev = new GLM(parms).trainModel().get();
            Scope.track_generic(modelDev);
            parms._dispersion_parameter_method = pearson;
            parms._useDispersion1 = false;
            GLMModel modelP = new GLM(parms).trainModel().get();
            Scope.track_generic(modelP);
            double trueDispersion = 2;
            // Deviance is not as accurate as pearson
            Assert.assertTrue(Math.floor(modelP._output.dispersion())==trueDispersion);
            Assert.assertTrue(Math.floor(modelDev._output.dispersion())==trueDispersion);
        } finally {
            Scope.exit();
        }
    }
    
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
            params._dispersion_parameter_method = ml;
            params._family = family;
            params._lambda = new double[]{0.0};
            params._build_null_model = true;
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            double[] mlCoeff = glmML.beta();
            assert mlCoeff.length==1 : "GLM model should only contain one element which is just the intercept.";
            assert glmML.coefficients().keySet().contains("Intercept"); // the one coefficient is for intercept

            params._dispersion_parameter_method = pearson;
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

    public static void assertCorrectFixedDispersionFactor(String filename, double trueDispersionFactor,
                                                     GLMModel.GLMParameters.Family family, String response, double eps) {
        Scope.enter();
        try {
            final Frame train = parseAndTrackTestFile(filename);
            GLMParameters params = new GLMParameters(family);
            params._response_column = response;
            params._train = train._key;
            params._compute_p_values = true;
            params._dispersion_parameter_method = ml;
            params._fix_dispersion_parameter = true;
            params._init_dispersion_parameter = trueDispersionFactor;
            params._family = family;
            params._lambda = new double[]{0.0};
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);

            params._dispersion_parameter_method = pearson;
            GLMModel glmPearson = new GLM(params).trainModel().get();
            Scope.track_generic(glmPearson);
            assert Math.abs(glmML._output.dispersion()-trueDispersionFactor)<eps:
                    "Fixed dispersion: " + trueDispersionFactor + " H2O dispersion: " + glmML._output.dispersion() +
                            ".  H2O dispersion parameter should equal to the fixed dispersion";
            assert Math.abs(glmPearson._output.dispersion()-trueDispersionFactor)<eps:
                    "Fixed dispersion: " + trueDispersionFactor + " H2O dispersion: " + glmML._output.dispersion() +
                            ".  H2O dispersion parameter should equal to the fixed dispersion";
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
            params._dispersion_parameter_method = ml;
            params._family = family;
            params._lambda = new double[]{0.0};
            GLMModel glmML = new GLM(params).trainModel().get();
            Scope.track_generic(glmML);
            
            params._dispersion_parameter_method = pearson;
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
