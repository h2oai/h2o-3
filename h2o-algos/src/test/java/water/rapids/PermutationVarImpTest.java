package water.rapids;

import hex.glm.GLM;
import hex.glm.GLMModel;
import hex.tree.drf.DRF;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBM;
import hex.tree.gbm.GBMModel;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.FVecFactory;
import water.fvec.Frame;
import water.fvec.TestFrameBuilder;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.RandomUtils;
import water.util.TwoDimTable;

import java.util.*;
import java.util.stream.Collectors;

import static hex.genmodel.utils.DistributionFamily.gaussian;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PermutationVarImpTest extends TestUtil {

    @BeforeClass
    public static void stall() {
        stall_till_cloudsize(1);
    }

    /**
     * Test setting the metric which Permutation Variable Importance will be calculated.
     * GLM binomial model
     */
    @Test
    public void testMetricGLMBinomial() {
        GLMModel model = null;
        Frame fr = parseTestFile("smalldata/glm_test/glm_test2.csv");
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

            GLM glm = new GLM(params);
            model = glm.trainModel().get();
            score = model.score(fr);

            PermutationVarImp pvi = new PermutationVarImp(model, fr);

            TwoDimTable pviTableRmse = pvi.getPermutationVarImp("rmse");
            TwoDimTable pviTableMse = pvi.getPermutationVarImp("mse");
            TwoDimTable pviTableR2 = pvi.getPermutationVarImp("r2");

            // Since model is binomial we can also calculate auc and logloss
            TwoDimTable pviTableAuc = pvi.getPermutationVarImp("auc");
            TwoDimTable pviTableLogloss = pvi.getPermutationVarImp("logloss");

            // the first column contains the relative (Permutation Variable) importance
            for (int row = 0; row < pviTableAuc.getRowDim(); row++) {
                String[] colTypes = pviTableRmse.getColTypes();
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

    /**
     * Test the TwoDimTable of Permutation Variable Importance
     * GBM gaussian model
     */
    @Test
    public void testRegression() {
        GBMModel gbm = null;
        Frame fr = null, fr2 = null;
        try {
            Scope.enter();
            fr = parseTestFile("./smalldata/junit/cars.csv");
            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();

            parms._train = fr._key;
            parms._distribution = gaussian;
            parms._response_column = "economy (mpg)";
            parms._ntrees = 5;
            parms._max_depth = 4;
            parms._min_rows = 1;
            parms._nbins = 50;
            parms._learn_rate = .2f;
            parms._score_each_iteration = true;

            GBM job = new GBM(parms);
            gbm = job.trainModel().get();

            // Done building model; produce a score column with predictions
            fr2 = gbm.score(fr);
            PermutationVarImp pvi = new PermutationVarImp(gbm, fr);
            TwoDimTable pviTable = pvi.getPermutationVarImp("auto");

            String[] colTypes = pviTable.getColTypes();

            Assert.assertTrue(colTypes[0].equals("double"));
            Assert.assertTrue(colTypes[1].equals("double"));
            Assert.assertTrue(colTypes[2].equals("double"));
        } finally {
            if (fr != null) fr.remove();
            if (fr2 != null) fr2.remove();
            if (gbm != null) gbm.remove();
            Scope.exit();
        }
    }

    /**
     * Testing Permutation Variable Importance values on a classification GBM model
     */
    @Test
    public void testClassification() {
        Frame fr = null;
        try {
            Scope.enter();
            final String response = "CAPSULE";
            final String testFile = "./smalldata/logreg/prostate.csv";
            fr = parseTestFile(testFile)
                    .toCategoricalCol("RACE")
                    .toCategoricalCol("GLEASON")
                    .toCategoricalCol(response);
            fr.remove("ID").remove();
            fr.vec("RACE").setDomain(ArrayUtils.append(fr.vec("RACE").domain(), "3"));
            Scope.track(fr);
            DKV.put(fr);

            GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
            parms._train = fr._key;
            parms._response_column = response;
            parms._ntrees = 5;

            GBM job = new GBM(parms);
            GBMModel gbm = job.trainModel().get();
            Scope.track_generic(gbm);

            // Done building model; produce a score column with predictions
            Frame scored = Scope.track(gbm.score(fr));

            PermutationVarImp fi = new PermutationVarImp(gbm, fr);
            TwoDimTable pvi = fi.getPermutationVarImp("auto");

            String[] colTypes = pvi.getColTypes();

            Assert.assertTrue(colTypes[0].equals("double"));
            Assert.assertTrue(colTypes[1].equals("double"));
            Assert.assertTrue(colTypes[2].equals("double"));
        } finally {
            if (fr != null) fr.remove();
            Scope.exit();
        }
    }

    /**
     * Testing Permutation Variable importance on GLM model
     */
    @Test
    public void testPermVarImOutput() {
        GLMModel model = null;

        Key parsed = Key.make("prostate_parsed");
        Key modelKey = Key.make("prostate_model");

        Frame fr = parseTestFile(parsed, "smalldata/logreg/prostate.csv");
        Key betaConsKey = Key.make("beta_constraints");

        FVecFactory.makeByteVec(betaConsKey, "names, lower_bounds, upper_bounds\n AGE, -.5, .5\n RACE, -.5, .5\n DCAPS, -.4, .4\n DPROS, -.5, .5 \nPSA, -.5, .5\n VOL, -.5, .5\nGLEASON, -.5, .5");
        Frame betaConstraints = ParseDataset.parse(Key.make("beta_constraints.hex"), betaConsKey);

        try {
            // H2O differs on intercept and race, same residual deviance though
            GLMModel.GLMParameters params = new GLMModel.GLMParameters();
            params._standardize = true;
            params._family = GLMModel.GLMParameters.Family.binomial;
            params._beta_constraints = betaConstraints._key;
            params._response_column = "CAPSULE";
            params._ignored_columns = new String[]{"ID"};
            params._train = fr._key;
            params._objective_epsilon = 0;
            params._alpha = new double[]{1};
            params._lambda = new double[]{0.001607};
            params._obj_reg = 1.0 / 380;
            GLM glm = new GLM(params, modelKey);
            model = glm.trainModel().get();

            PermutationVarImp PermVarImp = new PermutationVarImp(model, fr);
            TwoDimTable table = PermVarImp.getPermutationVarImp("auto");

            String ts = table.toString();
            assertTrue(ts.length() > 0);

            String[] colTypes = table.getColTypes();

            Assert.assertTrue(colTypes[0].equals("double"));
            Assert.assertTrue(colTypes[1].equals("double"));
            Assert.assertTrue(colTypes[2].equals("double"));
        } finally {
            fr.delete();
            betaConstraints.delete();
            if (model != null) model.delete();
        }
    }

    /**
     * Sorting a map by value.
     */
    public static class MapSort {
        public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
            List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
            list.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));

            Map<K, V> result = new LinkedHashMap<>();
            for (Map.Entry<K, V> entry : list) {
                result.put(entry.getKey(), entry.getValue());
            }

            return result;
        }
    }

    /**
     * Test Permutation Variable Importance together with GLM model and
     * check whether the PVI values ordered are similar to the
     * coefficients from the GLM. Allows +-3 elements to be in different oreder
     */
    @Test
    public void testPermVarImpGLM() {
        Scope.enter();
        Key parsed = Key.make("cars_parsed");
        Frame fr = null;
        GLMModel model = null;
        try {
            fr = parseTestFile(parsed, "smalldata/junit/cars.csv");
            GLMModel.GLMParameters params = new GLMModel.GLMParameters(GLMModel.GLMParameters.Family.poisson, GLMModel.GLMParameters.Family.poisson.defaultLink, new double[]{0}, new double[]{0}, 0, 0);
            params._response_column = "power (hp)";
            params._ignored_columns = new String[]{"name"};
            params._train = parsed;
            params._lambda = new double[]{0};
            params._alpha = new double[]{0};
            params._missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.Skip;

            model = new GLM(params).trainModel().get();

            // calculate permutation feature importance
            PermutationVarImp permVarImp = new PermutationVarImp(model, fr);
            TwoDimTable permVarImpTable = permVarImp.getPermutationVarImp("r2");

            assert model._parms._standardize;

            // Variable -> Relative Importance 
            Map<String, Double> perVarImp = new HashMap<>();
            for (int i = 0; i < permVarImpTable.getRowDim(); i++)
                perVarImp.put((String) permVarImpTable.getRowHeaders()[i], (double) permVarImpTable.get(i, 0));
            Map<String, Double> coefficients = model
                    .coefficients(true)
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> Math.abs(e.getValue())));

            // Sort the maps
            Map<String, Double> sCoefficients = MapSort.sortByValue(coefficients);
            Map<String, Double> sPvi = MapSort.sortByValue(perVarImp);

            // Instead of comparing values compare positions (rank)
            String[] coeff = new String[sCoefficients.size() - 1];
            String[] pvi = new String[perVarImp.size()];

            int id = 0;
            for (String name : sCoefficients.keySet()) {
                if (name.equals("Intercept")) continue; // ignore intercept
                coeff[id++] = name;
            }
            id = 0;
            for (String name : sPvi.keySet()) {
                pvi[id++] = name;
            }
            // same leangth of variables
            assertEquals(coeff.length, pvi.length);

            int size = perVarImp.size();

            // Compares the rank where the feature is for +- 2 positions

            // *      ...       *        ...        *     (variable)
            // |\\    ...   / / | \ \    ...      //|     (compared)
            // * **   ...  *  * * *  *   ...    * * *     (variable)
            for (int i = 0; i < size; i++) {
                if (i == 0) {
                    assertTrue(pvi[i].equals(coeff[i]) ||
                            pvi[i].equals(coeff[i + 1]) ||
                            pvi[i].equals(coeff[i + 2]));

                } else if (i == 1) {
                    assertTrue(pvi[i].equals(coeff[i]) ||
                            pvi[i].equals(coeff[i - 1]) ||
                            pvi[i].equals(coeff[i + 1]) ||
                            pvi[i].equals(coeff[i + 2]));

                } else if (i >= 2 && i < size - 2) {
                    assertTrue(pvi[i].equals(coeff[i]) ||
                            pvi[i].equals(coeff[i - 1]) ||
                            pvi[i].equals(coeff[i - 2]) ||
                            pvi[i].equals(coeff[i + 1]) ||
                            pvi[i].equals(coeff[i + 2]));

                } else if (i == size - 2) {
                    assertTrue(pvi[i].equals(coeff[i]) ||
                            pvi[i].equals(coeff[i - 1]) ||
                            pvi[i].equals(coeff[i - 2]) ||
                            pvi[i].equals(coeff[i + 1]));
                    // since these have the least importance I allow 3 "wrong" positions
                } else if (i == size - 1) {
                    assertTrue(pvi[i].equals(coeff[i]) ||
                            pvi[i].equals(coeff[i - 1]) ||
                            pvi[i].equals(coeff[i - 2]) ||
                            pvi[i].equals(coeff[i - 3]));
                }
            }
        } finally {
            if (fr != null) fr.delete();
            if (model != null) model.delete();
            Scope.exit();
        }
    }


    @Test
    public void testPermVarImpWeights() {
        try {
            Scope.enter();
            Random r = RandomUtils.getRNG(42);
            double[] resp = new double[10_000];
            double[] feat1 = new double[resp.length];
            double[] feat2 = new double[resp.length];
            double[] weights = new double[resp.length];
            for (int i = 0; i < resp.length; i++) {
                if (i < 100) {
                    weights[i] = 1;
                    feat1[i] = r.nextDouble();
                    feat2[i] = r.nextDouble();
                } else {
                    weights[i] = 0;
                    feat1[i] = r.nextDouble(); // keep the same distribution in 0-weighted rows as in 1-weighted 
                    feat2[i] = 10;             // skew the distribution for 0-weighted rows in column 2
                }
                resp[i] = feat1[i] + feat2[i];
            }
            Frame fr = new TestFrameBuilder()
                    .withVecTypes(Vec.T_NUM, Vec.T_NUM, Vec.T_NUM, Vec.T_NUM)
                    .withColNames("c1", "c2", "weight", "response")
                    .withDataForCol(0, feat1)
                    .withDataForCol(1, feat2)
                    .withDataForCol(2, weights)
                    .withDataForCol(3, resp)
                    .build();

            DRFModel.DRFParameters p = new DRFModel.DRFParameters();
            p._train = fr._key;
            p._response_column = "response";
            p._weights_column = "weight";
            p._mtries = 1;
            p._seed = 0xCAFE;
            DRFModel m = new DRF(p).trainModel().get();
            Scope.track_generic(m);

            PermutationVarImp permVarImp = new PermutationVarImp(m, fr);
            Map<String, Double> estimatedVarImp = permVarImp.calculatePermutationVarImp(
                    "MSE", fr.vec(p._weights_column).nzCnt(), m._output.features(), 42);

            Log.info("Actual varimp: " + m._output._variable_importances);
            Log.info("Estimated varimp: " + estimatedVarImp);
            double viC1 = estimatedVarImp.get("c1");
            double viC2 = estimatedVarImp.get("c2");

            Assert.assertArrayEquals(new double[]{0.5, 0.5}, new double[]{viC1/(viC1+viC2), viC2/(viC1+viC2)}, 0.1);
        } finally {
            Scope.exit();
        }
    }

}
