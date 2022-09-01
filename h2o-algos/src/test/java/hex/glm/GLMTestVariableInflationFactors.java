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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hex.glm.GLMModel.GLMParameters.Family.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GLMTestVariableInflationFactors extends TestUtil {
    @Test
    public void testVIFactorGaussian() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/prostate/prostate_complete.csv.zip");
            assertCorrectVIF(train, "GLEASON", 1e-6, gaussian);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testVIFactorBinomial() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile("smalldata/prostate/prostate_complete.csv.zip");
            List<String> predNames = Stream.of(train.names()).collect(Collectors.toList());
            String resp = "CAPSULE";
            int indexOfResp = predNames.indexOf(resp);
            train.replace(indexOfResp, train.vec(indexOfResp).toCategoricalVec()).remove();
            DKV.put(train);
            assertCorrectVIF(train, "CAPSULE", 1e-6, binomial);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testVIFactorPoisson() {
        Scope.enter();
        try {
            Frame df = parseAndTrackTestFile("smalldata/prostate/prostate_complete.csv.zip");
            assertCorrectVIF(df, "GLEASON", 1e-6, poisson);
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testVIFactorMultinomial() {
        Scope.enter();
        try {
            Frame df = parseAndTrackTestFile("smalldata/glm_test/rollup_stat_test.csv");
            df.replace(df.numCols() - 1, df.vec("RACE").toCategoricalVec()).remove();
            DKV.put(df);
            assertCorrectVIF(df, "RACE", 1e-6, multinomial);
        } finally {
            Scope.exit();
        }
    }
    
    @Test
    public void testNumNEnumPredictors() {
        Scope.enter();
        try {
            Frame train = parseAndTrackTestFile(("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"));
            List<String> colNames = Stream.of(train.names()).collect(Collectors.toList());
            String[] enumCols = new String[]{"C1", "C2", "C3", "C4", "C5", "C11"};
            Stream.of(enumCols).forEach(x -> train.replace(colNames.indexOf(x), train.vec(x).toCategoricalVec()).remove());
            DKV.put(train);
            assertCorrectVIF(train, "C11", 1e-6, multinomial);
        } finally {
            Scope.exit();
        }
    }

    public void assertCorrectVIF(Frame train, String response, double tot, GLMModel.GLMParameters.Family family) {
        GLMModel.GLMParameters params = new GLMModel.GLMParameters();
        params._response_column = response;
        params._train = train._key;
        params._family = family;
        params._generate_variable_inflation_factors = true;
        GLMModel model = new GLM(params).trainModel().get();
        Scope.track_generic(model);
        double[] varInfFactors = model._output.variableInflationFactors();
        String[] vifNames = model._output.getVIFPredictorNames();
        for (int index = 0; index < vifNames.length; index++) {
                GLMModel.GLMParameters tParms = new GLMModel.GLMParameters(gaussian);
                tParms._lambda = new double[]{0};
                tParms._alpha = new double[]{0};
                tParms._compute_p_values = true;
                tParms._response_column = vifNames[index];
                tParms._train = train._key;
                tParms._ignored_columns = new String[]{response};
                GLMModel tModel = new GLM(tParms).trainModel().get();
                Scope.track_generic(tModel);
                double vif = 1.0 / (1.0 - tModel.r2());
                Assert.assertTrue(Math.abs(vif - varInfFactors[index]) < tot);
        }
        // make sure VIF is calculated for the correct number of predictors
        List<String> names = Stream.of(model.names()).collect(Collectors.toList());
        names.remove(names.indexOf(response)); 
        int numNumericalCols = names.stream().filter(x -> train.vec(x).isNumeric()).collect(Collectors.toList()).size();
        Assert.assertTrue(numNumericalCols == varInfFactors.length);
    }
}
