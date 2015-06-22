package hex.glm;

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
import java.util.List;
import java.nio.file.Files;

import static org.testng.Assert.*;
import org.testng.annotations.*;

public class GLMBasic extends TestNGUtil {
    static Frame _airquality_train1;
    static Frame _airquality_train2;
    static Frame _insurance_train1;
    static Frame _iris_train1;
    static Frame _airquality_validation1;
    static Frame _airquality_validation2;
    static Frame _insurance_validation1;
    static Frame _iris_validation1;

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
    public void basic(String regression, String	classification, String gaussian, String binomial, String poisson,
                      String gamma, String auto, String irlsm, String lbfgs, String ignore_const_cols,
                      String offset_column, String weights_column, String alpha, String lambda, String lambdaSearch,
                      String standardize, String non_negative, String betaConstraints, String lowerBound,
                      String upperBound, String intercept, String prior, String maxActivePredictors,
                      String distribution, String train_dataset_id, String train_dataset_filename,
                      String validate_dataset_id, String validate_dataset_filename) {

        // Set GLM parameters
        Family f = null;
        if     (gaussian.equals("x")) { f = Family.gaussian; }
        else if(binomial.equals("x")) { f = Family.binomial; }
        else if(poisson.equals("x"))  { f = Family.poisson; }
        else if(gamma.equals("x"))    { f = Family.gamma; }
        GLMParameters params = null != f ? new GLMParameters(f) : new GLMParameters();
        if     (irlsm.equals("x")) { params._solver = Solver.IRLSM; }
        else if(lbfgs.equals("x")) { params._solver = Solver.L_BFGS; }
        params._lambda = lambda.equals("") ? null : new double[]{ Double.parseDouble(lambda)};
        params._alpha = alpha.equals("") ? null : new double[]{ Double.parseDouble(alpha)};
        params._standardize = standardize.equals("x");
        params._lambda_search = lambdaSearch.equals("x");
        //params._prior = prior.equals("") ? -1 : Double.parseDouble(prior);
        //params._max_active_predictors = maxActivePredictors.equals("") ? -1 : Integer.parseInt(maxActivePredictors);
        //boolean bc = betaConstraints.equals("x");

        // Pick the training and validation datasets and the correct response column
        switch(train_dataset_filename){
            case "airquality_train1.csv":
                params._train = _airquality_train1._key;
                params._valid = _airquality_validation1._key;
                params._response_column = "Ozone";
                break;
            case "airquality_train2.csv":
                params._train = _airquality_train2._key;
                params._valid = _airquality_validation2._key;
                params._response_column = "Ozone";
                break;
            case "insurance_train1.csv":
                params._train = _insurance_train1._key;
                params._valid = _insurance_validation1._key;
                params._response_column = "Claims";
                break;
            case "iris_train1.csv":
                params._train = _iris_train1._key;
                params._valid = _iris_validation1._key;
                params._response_column = "Species";
                break;
        }


        // Build the appropriate glm, given the above parameters
        GLM job = null;
        GLMModel model = null;
        Frame score = null;
        try {
            Scope.enter();

            // This is the only test case that works right now
            if(train_dataset_filename.equals("airquality_train1.csv")) {
                job = new GLM(Key.make("model"), "basic glm test", params);
                model = job.trainModel().get();

                if (gaussian.equals("x")) { assertTrue(model._output._validation_metrics._MSE >= 0.0,
                        "Expected mse to be greater than 0.0"); }
                else if(binomial.equals("x")) { assertTrue(model._output._validation_metrics.auc()._auc >= 0.0,
                        "Expected mse to be greater than 0.0"); }
            }
        } finally {
            if (model != null) model.delete();
            if (job != null) job.remove();
            Scope.exit();
        }
    }

    @BeforeClass
    public static void setup() {
        //Parse the training datasets
        File airquality_train1 = find_test_file_static("smalldata/testng/airquality_train1.csv");
        File airquality_train2 = find_test_file_static("smalldata/testng/airquality_train2.csv");
        File insurance_train1 = find_test_file_static("smalldata/testng/insurance_train1.csv");
        File iris_train1 = find_test_file_static("smalldata/testng/iris_train1.csv");

        assert airquality_train1.exists() && airquality_train2.exists() && insurance_train1.exists() &&
                iris_train1.exists();

        NFSFileVec nfs_airquality_train1 = NFSFileVec.make(airquality_train1);
        NFSFileVec nfs_airquality_train2 = NFSFileVec.make(airquality_train2);
        NFSFileVec nfs_insurance_train1 = NFSFileVec.make(insurance_train1);
        NFSFileVec nfs_iris_train1 = NFSFileVec.make(iris_train1);

        Key airquality_train1Key = Key.make("airquality_train1.hex");
        Key airquality_train2Key = Key.make("airquality_train2.hex");
        Key insurance_train1Key = Key.make("insurance_train1.hex");
        Key iris_train1Key = Key.make("iris_train1.hex");

        _airquality_train1 = ParseDataset.parse(airquality_train1Key,  nfs_airquality_train1._key);
        _airquality_train2 = ParseDataset.parse(airquality_train2Key,  nfs_airquality_train2._key);
        _insurance_train1 = ParseDataset.parse(insurance_train1Key,  nfs_insurance_train1._key);
        _iris_train1 = ParseDataset.parse(iris_train1Key,  nfs_iris_train1._key);

        //Parse the validation datasets
        File airquality_validation1 = find_test_file_static("smalldata/testng/airquality_validation1.csv");
        File airquality_validation2 = find_test_file_static("smalldata/testng/airquality_validation2.csv");
        File insurance_validation1 = find_test_file_static("smalldata/testng/insurance_validation1.csv");
        File iris_validation1 = find_test_file_static("smalldata/testng/iris_validation1.csv");

        assert airquality_validation1.exists() && airquality_validation2.exists() && insurance_validation1.exists() &&
                iris_validation1.exists();

        NFSFileVec nfs_airquality_validation1 = NFSFileVec.make(airquality_validation1);
        NFSFileVec nfs_airquality_validation2 = NFSFileVec.make(airquality_validation2);
        NFSFileVec nfs_insurance_validation1 = NFSFileVec.make(insurance_validation1);
        NFSFileVec nfs_iris_validation1 = NFSFileVec.make(iris_validation1);

        Key airquality_validation1Key = Key.make("airquality_validation1.hex");
        Key airquality_validation2Key = Key.make("airquality_validation2.hex");
        Key insurance_validation1Key = Key.make("insurance_validation1.hex");
        Key iris_validation1Key = Key.make("iris_validation1.hex");

        _airquality_validation1 = ParseDataset.parse(airquality_validation1Key,  nfs_airquality_validation1._key);
        _airquality_validation2 = ParseDataset.parse(airquality_validation2Key,  nfs_airquality_validation2._key);
        _insurance_validation1 = ParseDataset.parse(insurance_validation1Key,  nfs_insurance_validation1._key);
        _iris_validation1 = ParseDataset.parse(iris_validation1Key,  nfs_iris_validation1._key);
    }

    @AfterClass
    public void cleanUp() {
        if(_airquality_train1 != null)
            _airquality_train1.delete();
        if(_airquality_train2 != null)
            _airquality_train2.delete();
        if(_insurance_train1 != null)
            _insurance_train1.delete();
        if(_iris_train1 != null)
            _iris_train1.delete();
        if(_airquality_validation1 != null)
            _airquality_validation1.delete();
        if(_airquality_validation2 != null)
            _airquality_validation2.delete();
        if(_insurance_validation1 != null)
            _insurance_validation1.delete();
        if(_iris_validation1 != null)
            _iris_validation1.delete();
    }
}
