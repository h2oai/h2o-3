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
            List<String> lines = Files.readAllLines(Paths.get("/Users/ece/0xdata/h2o-dev/h2o-testng/src/test/" +
                    "resources/glmCases.csv"), Charset.defaultCharset());
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

        // Get GLM parameters
        Family f = null;
        if     (gaussian.equals("x")) { f = Family.gaussian; }
        else if(binomial.equals("x")) { f = Family.binomial; }
        else if(poissan.equals("x"))  { f = Family.poisson; }
        else if(gamma.equals("x"))    { f = Family.gamma; }

        Solver s = Solver.L_BFGS;
        if     (irlsm.equals("x")) { s = Solver.IRLSM; }

        double[] a = alpha.equals("") ? null : new double[]{ Double.parseDouble(alpha)};
        double[] l = lambda.equals("") ? null : new double[]{ Double.parseDouble(lambda)};
        boolean ls = lambdaSearch.equals("x");
        boolean std = standardize.equals("x");
        boolean bc = betaConstraints.equals("x");
        boolean uafl = useAllFactorLevels.equals("x");
        //double p = prior.equals("") ? -1 : Double.parseDouble(prior);
        //int m = maxActivePredictors.equals("") ? -1 : Integer.parseInt(maxActivePredictors);

        GLM job = null;
        GLMModel model = null;
        Frame score = null;
        try {
            Scope.enter();
            GLMParameters params = null != f ? new GLMParameters(f) : new GLMParameters();
            params._response_column = "Ozone";
            params._train = _airquality._key;
            params._lambda = l;
            params._alpha = a;
            params._standardize = std;
            params._lambda_search = ls;
            params._use_all_factor_levels = uafl;
            //params._prior = p;
            //params._max_active_predictors = m;
            params._solver = s;

            if(gaussian.equals("x") && dataset.equals("airquality.csv")) {
                job = new GLM(Key.make("model"), "basic glm test", params);
                model = job.trainModel().get();

                //HashMap<String, Double> coefs = model.coefficients();
                //GLMTest.nullDeviance(model);
                //GLMTest.residualDeviance(model);
                //GLMTest.nullDOF(model);
                //GLMTest.resDOF(model);
                //GLMTest.aic(model);
                model.delete();

                // test scoring
                if (dataset.equals("airquality.csv")) {
                    score = model.score(_airquality);
                } else if (dataset.equals("insurance.csv")) {
                    score = model.score(_insurance);
                }

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
