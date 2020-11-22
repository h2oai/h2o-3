package hex.varimp;

import hex.glm.GLM;
import hex.glm.GLMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.rapids.PermutationVarImp;
import water.util.TwoDimTable;

import static org.junit.Assert.assertEquals;

public class PermutationVarImpMetricPassTest extends TestUtil {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }

    /**
     * Test setting the metric which Permutation Variable Importance will be calculated.
     * GLM binomial model
     */
    @Test
    public void testMetricBinomial() {
        GLMModel model = null;
        Frame fr = parse_test_file("smalldata/glm_test/glm_test2.csv");
        Frame score = null;
        try {
            Scope.enter();
            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.binomial);
            params._response_column = "response";
            params._ignored_columns = new String[]{"ID"};
            params._train = fr._key;
            params._lambda = new double[]{0};
            params._standardize = false;
            params._max_iterations = 20;

            GLM glm = new GLM( params);
            model = glm.trainModel().get();
            score = model.score(fr);

            PermutationVarImp pvi = new PermutationVarImp(model, fr);

            // when no metric specified "MSE
            TwoDimTable pviTable = pvi.getPermutationVarImp();
            assertEquals("mse", pvi._varImpMetric._metric);

            TwoDimTable pviTableRmse = pvi.getPermutationVarImp("rmse");
            assertEquals("rmse", pvi._varImpMetric._metric);

            TwoDimTable pviTableMse = pvi.getPermutationVarImp("mse");
            TwoDimTable pviTableR2 = pvi.getPermutationVarImp("r2");

            // Since model is binomial we can also calculate auc and logloss
            TwoDimTable pviTableAuc = pvi.getPermutationVarImp("auc");
            TwoDimTable pviTableLogloss = pvi.getPermutationVarImp("logloss");

            // the first column contains the relative (Permutation Variable) importance
            for (int row = 0 ; row < pviTableAuc.getRowDim() ; row++){
                String [] colTypes = pviTableRmse.getColTypes();
                Assert.assertTrue(colTypes[0].equals("double"));
                Assert.assertTrue(colTypes[1].equals("double"));
                Assert.assertTrue(colTypes[2].equals("double"));

                colTypes = pviTableMse.getColTypes();
                Assert.assertTrue(colTypes[0].equals("double"));

                colTypes = pviTableR2.getColTypes();
                Assert.assertTrue(colTypes[0].equals("double"));

                colTypes = pviTableAuc.getColTypes();
                Assert.assertTrue(colTypes[0].equals("double"));

                colTypes = pviTableLogloss.getColTypes();
                Assert.assertTrue(colTypes[0].equals("double"));
            }
        } finally {
            fr.remove();
            if (model != null) model.delete();
            if (score != null) score.delete();
            Scope.exit();
        }
    }
}
