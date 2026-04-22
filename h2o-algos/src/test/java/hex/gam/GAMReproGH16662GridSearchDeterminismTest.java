package hex.gam;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsBinomial;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.DKV;
import water.Job;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;

import static hex.glm.GLMModel.GLMParameters.Family.binomial;
import static org.junit.Assert.*;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class GAMReproGH16662GridSearchDeterminismTest extends TestUtil {

    @Test
    public void testGAMThinPlateDeterminism() {
        Scope.enter();
        try {
            Frame train = Scope.track(parseTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv"));
            train.replace(train.find("response"), train.vec("response").toCategoricalVec()).remove();
            DKV.put(train);

            GAMModel gam1 = buildGAMWithTP(train);
            Scope.track_generic(gam1);
            GAMModel gam2 = buildGAMWithTP(train);
            Scope.track_generic(gam2);

            double logloss1 = ((ModelMetricsBinomial) gam1._output._training_metrics)._logloss;
            double logloss2 = ((ModelMetricsBinomial) gam2._output._training_metrics)._logloss;

            assertEquals("GAM with TP splines should produce nearly identical logloss across runs",
                    logloss1, logloss2, 1e-15);

            double[] beta1 = gam1._output._model_beta;
            double[] beta2 = gam2._output._model_beta;
            assertEquals("Coefficient count should match", beta1.length, beta2.length);
            for (int i = 0; i < beta1.length; i++) {
                assertEquals("Coefficient " + gam1._output._coefficient_names[i] +
                                " should be nearly identical across runs",
                        beta1[i], beta2[i], 1e-15);
            }
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testCartesianGridSearchSubspacesDeterminism() {
        Scope.enter();
        try {
            Frame train = Scope.track(parseTestFile("smalldata/gam_test/synthetic_20Cols_binomial_20KRows.csv"));
            train.replace(train.find("response"), train.vec("response").toCategoricalVec()).remove();
            String[] catCols = {"C3", "C7", "C8", "C10"};
            for (String col : catCols) {
                train.replace(train.find(col), train.vec(col).toCategoricalVec()).remove();
            }
            DKV.put(train);

            GAMModel.GAMParameters params = new GAMModel.GAMParameters();
            params._train = train._key;
            params._response_column = "response";
            params._family = binomial;
            params._seed = 1;
            params._keep_gam_cols = true;

            Map<String, Object[]> hyperParams = buildHyperParams();

            HyperSpaceSearchCriteria.CartesianSearchCriteria searchCriteria =
                    new HyperSpaceSearchCriteria.CartesianSearchCriteria();

            Job<Grid> job1 = GridSearch.startGridSearch(null, params, hyperParams,
                    new GridSearch.SimpleParametersBuilderFactory<>(), searchCriteria, 1);
            Scope.track_generic(job1);
            Grid grid1 = job1.get();
            Scope.track_generic(grid1);

            Map<String, Object[]> hyperParams2 = buildHyperParams();
            Job<Grid> job2 = GridSearch.startGridSearch(null, params, hyperParams2,
                    new GridSearch.SimpleParametersBuilderFactory<>(), searchCriteria, 1);
            Scope.track_generic(job2);
            Grid grid2 = job2.get();
            Scope.track_generic(grid2);

            List<Key<Model>> sorted1 = ModelMetrics.sortModelsByMetric(
                    "logloss", false, Arrays.asList(grid1.getModelKeys()));
            List<Key<Model>> sorted2 = ModelMetrics.sortModelsByMetric(
                    "logloss", false, Arrays.asList(grid2.getModelKeys()));

            assertEquals("Both grids should have the same number of models",
                    sorted1.size(), sorted2.size());
            assertTrue("Grid should have trained at least one model", sorted1.size() > 0);

            for (int i = 0; i < sorted1.size(); i++) {
                GAMModel m1 = DKV.getGet(sorted1.get(i));
                GAMModel m2 = DKV.getGet(sorted2.get(i));
                assertNotNull("Model " + i + " from grid 1 should exist", m1);
                assertNotNull("Model " + i + " from grid 2 should exist", m2);

                double logloss1 = ((ModelMetricsBinomial) m1._output._training_metrics)._logloss;
                double logloss2 = ((ModelMetricsBinomial) m2._output._training_metrics)._logloss;
                assertEquals("Model at rank " + i + " should have identical logloss",
                        logloss1, logloss2, 0.0);

                assertArrayEquals("Model at rank " + i + " should have the same coefficient names",
                        m1._output._coefficient_names, m2._output._coefficient_names);

                double[] beta1 = m1._output._model_beta;
                double[] beta2 = m2._output._model_beta;
                for (int j = 0; j < beta1.length; j++) {
                    assertEquals("Coefficient " + m1._output._coefficient_names[j] +
                                    " at rank " + i + " should be identical",
                            beta1[j], beta2[j], 0.0);
                }
            }
        } finally {
            Scope.exit();
        }
    }

    private GAMModel buildGAMWithTP(Frame train) {
        GAMModel.GAMParameters params = new GAMModel.GAMParameters();
        params._train = train._key;
        params._response_column = "response";
        params._family = binomial;
        params._seed = 1;
        params._keep_gam_cols = true;
        params._gam_columns = new String[][]{{"c_0"}, {"c_1", "c_2"}, {"c_3", "c_4", "c_5"}};
        params._bs = new int[]{1, 1, 1};
        params._scale = new double[]{0.001, 0.001, 0.001};
        params._lambda = new double[]{1.0};
        params._num_knots = new int[]{10, 10, 12};
        return new GAM(params).trainModel().get();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object[]> buildHyperParams() {
        Map<String, Object[]> hyperParams = new HashMap<>();
        hyperParams.put("_lambda", new Object[]{new double[]{1.0}, new double[]{2.0}});
        hyperParams.put("subspaces", new Map[]{
                new HashMap<String, Object[]>() {{
                    put("_scale", new Object[]{
                            new double[]{0.001},
                            new double[]{0.0002}
                    });
                    put("_bs", new Object[]{
                            new int[]{1},
                            new int[]{0}
                    });
                    put("_gam_columns", new Object[]{
                            new String[][]{{"c_0"}},
                            new String[][]{{"c_1"}}
                    });
                }},
                new HashMap<String, Object[]>() {{
                    put("_scale", new Object[]{
                            new double[]{0.001, 0.001, 0.001},
                            new double[]{0.0002, 0.0002, 0.0002}
                    });
                    put("_bs", new Object[]{
                            new int[]{3, 1, 1},
                            new int[]{0, 1, 1}
                    });
                    put("_gam_columns", new Object[]{
                            new String[][]{{"c_0"}, {"c_1", "c_2"}, {"c_3", "c_4", "c_5"}},
                            new String[][]{{"c_1"}, {"c_2", "c_3"}, {"c_4", "c_5", "c_6"}}
                    });
                }}
        });
        return hyperParams;
    }

}
