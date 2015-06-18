package hex.glm;

import hex.ModelMetricsBinomialGLM;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Solver;

import water.Key;
import water.Scope;
import water.TestNGUtil;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.parser.ParseDataset;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.nio.file.Files;

import static org.testng.Assert.*;
import org.testng.annotations.*;

public class GLMBasic extends TestNGUtil {
    static Frame _airquality;
    static Frame _insurance;

    @DataProvider(name = "glmCases")
    public static Object[][] glmCases() {
        Object[][] data = null;
        try {
            List<String> lines = Files.readAllLines(find_test_file_static("h2o-testng/src/test/resources/" +
                    "glmCases.csv").toPath(), Charset.defaultCharset());
            data = new Object[lines.size()][18];
            int r = 0;
            for(String line : lines){
                String[] variables = line.trim().split(",");
                for(int c = 0; c < 18; c++){
                    try{ data[r][c] = variables[c]; } catch(IndexOutOfBoundsException e) { data[r][c] = "";}
                }
                r++;
            }
        } catch(Exception ignore) {}

        return data;
    }

    @Test(dataProvider = "glmCases")
    public void basic(String gaussian, String binomial ,String poissan, String gamma, String auto,
                                String irlsm, String lbfgs, String alpha, String lambda, String lambdaSearch,
                                String standardize, String betaConstraints, String lowerBound, String upperBound,
                                String useAllFactorLevels, String prior, String maxActivePredictors, String dataset) {

        // Set GLM parameters
        Family f = null;
        if     (gaussian.equals("x")) { f = Family.gaussian; }
        else if(binomial.equals("x")) { f = Family.binomial; }
        else if(poissan.equals("x"))  { f = Family.poisson; }
        else if(gamma.equals("x"))    { f = Family.gamma; }
        GLMParameters params = null != f ? new GLMParameters(f) : new GLMParameters();
        if     (irlsm.equals("x")) { params._solver = Solver.IRLSM; }
        else if(lbfgs.equals("x")) { params._solver = Solver.L_BFGS; }
        params._lambda = lambda.equals("") ? null : new double[]{ Double.parseDouble(lambda)};
        params._alpha = alpha.equals("") ? null : new double[]{ Double.parseDouble(alpha)};
        params._standardize = standardize.equals("x");
        params._lambda_search = lambdaSearch.equals("x");
        params._use_all_factor_levels = useAllFactorLevels.equals("x");
        //params._prior = prior.equals("") ? -1 : Double.parseDouble(prior);
        //params._max_active_predictors = maxActivePredictors.equals("") ? -1 : Integer.parseInt(maxActivePredictors);
        //boolean bc = betaConstraints.equals("x");
        switch(dataset){
            case "airquality.csv":
                params._train = _airquality._key;
                break;
            case "insurance.csv":
                params._train = _insurance._key;
                break;
        }
        params._response_column = "Ozone";

        // Build the appropriate glm, given the above parameters
        GLM job = null;
        GLMModel model = null;
        Frame score = null;
        try {
            Scope.enter();

            if(gaussian.equals("x") && dataset.equals("airquality.csv")) {
                job = new GLM(Key.make("model"), "basic glm test", params);
                model = job.trainModel().get();

                //HashMap<String, Double> coefs = model.coefficients();
                //GLMTest.nullDeviance(model);
                //GLMTest.residualDeviance(model);
                //GLMTest.nullDOF(model);
                //GLMTest.resDOF(model);
                //GLMTest.aic(model);

                // Score the model
                score = model.score(_airquality);

                //hex.ModelMetricsBinomial mm = hex.ModelMetricsBinomial.getFromDKV(model, _airquality);
                hex.ModelMetricsRegression mm = hex.ModelMetricsRegression.getFromDKV(model, _airquality);
                //hex.AUC2 adata = mm._auc;
                double mse = mm._MSE;
                System.out.println(mse);
                assertTrue(mse >= 0.0,"Expected mse to be greater than 0.0");
                //assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
                //assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
                //assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
                //Frame score1 = model.score(_airquality);
                //score1.remove();
                //mm = hex.ModelMetricsBinomial.getFromDKV(model, _airquality);
                //assertEquals(model._output._training_metrics.auc()._auc, adata._auc, 1e-8);
                //assertEquals(model._output._training_metrics._MSE, mm._MSE, 1e-8);
                //assertEquals(((ModelMetricsBinomialGLM) model._output._training_metrics)._resDev, ((ModelMetricsBinomialGLM) mm)._resDev, 1e-8);
            }
        } finally {
            if (model != null) model.delete();
            if (score != null) score.delete();
            if (job != null) job.remove();
            Scope.exit();
        }
    }

    @BeforeClass
    public static void setup() {
        File airquality = find_test_file_static("smalldata/glm_test/airquality.csv");
        assert airquality.exists();
        NFSFileVec nfs_airquality = NFSFileVec.make(airquality);
        Key airqualityKey = Key.make("airquality.hex");
        _airquality = ParseDataset.parse(airqualityKey,  nfs_airquality._key);

        File insurance = find_test_file_static("smalldata/glm_test/insurance.csv");
        assert insurance.exists();
        NFSFileVec nfs_insurance = NFSFileVec.make(insurance);
        Key insuranceKey = Key.make("insurance.hex");
        _insurance = ParseDataset.parse(insuranceKey,  nfs_insurance._key);
    }

    @AfterClass
    public void cleanUp() {
        if(_airquality != null)
            _airquality.delete();

        if(_insurance != null)
            _insurance.delete();
    }
}
