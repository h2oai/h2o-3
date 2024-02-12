package hex.grid;

import hex.ScoreKeeper.StoppingMetric;
import hex.grid.HyperSpaceSearchCriteria.StoppingCriteria;
import hex.tree.gbm.GBMModel;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.Job;
import water.Key;
import water.Scope;
import water.fvec.Frame;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static water.TestUtil.parseTestFile;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SequentialWalkerTest {
    
    @Test
    public void test_SequentialWalker() {
        try {
            Scope.enter();
            final Frame train = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(train);
            
            GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
            gbmParameters._train = train._key;
            gbmParameters._response_column = "IsDepDelayed";
            gbmParameters._seed = 123;
            
            Object[][] hyperParams = new Object[][] {
                    new Object[] {"_learn_rate", "_score_tree_interval"},
                    new Object[] {       0.5   ,                     2 },
                    new Object[] {       0.2   ,                     4 },
                    new Object[] {       0.1   ,                     8 },
            };

            Job<Grid> job= GridSearch.startGridSearch(
                    Key.make("seq_test_grid"),
                    new SequentialWalker<>(
                            gbmParameters,
                            hyperParams,
                            new SimpleParametersBuilderFactory<>(),
                            new HyperSpaceSearchCriteria.SequentialSearchCriteria()
                    ),
                    GridSearch.SEQUENTIAL_MODEL_BUILDING
            );
            Scope.track_generic(job);
            final Grid grid = job.get();
            Scope.track_generic(grid);

            assertEquals(3, grid.getModelCount());
            GBMModel[] gbms = Arrays.stream(grid.getModels())
                    .map(m -> (GBMModel)m)
                    .sorted(Comparator.comparing(m -> m._key.toString())) // keys are suffixed with an increment
                    .toArray(GBMModel[]::new);
            assertEquals(0.5, gbms[0]._parms._learn_rate, 0);
            assertEquals(0.2, gbms[1]._parms._learn_rate, 0);
            assertEquals(0.1, gbms[2]._parms._learn_rate, 0);
            assertEquals(2, gbms[0]._parms._score_tree_interval, 0);
            assertEquals(4, gbms[1]._parms._score_tree_interval, 0);
            assertEquals(8, gbms[2]._parms._score_tree_interval, 0);
            
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void test_SequentialWalker_getHyperParams() {
        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        Object[][] hyperParams = new Object[][] {
            new Object[] {"_learn_rate", "_score_tree_interval"},
            new Object[] {       0.5   ,                     2 },
            new Object[] {       0.2   ,                     4 },
            new Object[] {       0.1   ,                     8 }
        };
        SequentialWalker walker = new SequentialWalker<>(
            gbmParameters,
            hyperParams,
            new SimpleParametersBuilderFactory<>(),
            new HyperSpaceSearchCriteria.SequentialSearchCriteria()
        );
        Map<String, Object[]> exp = new HashMap<>();
        assertEquals(new HashSet<>(Arrays.asList("_learn_rate", "_score_tree_interval")), walker.getHyperParams().keySet());
        assertArrayEquals(new Object[] { 0.5, 0.2, 0.1 }, (Object[]) walker.getHyperParams().get("_learn_rate"));
        assertArrayEquals(new Object[] { 2, 4, 8}, (Object[]) walker.getHyperParams().get("_score_tree_interval"));
    }
    
    @Test
    public void test_SequentialWalker_supports_early_stopping() {
        try {
            Scope.enter();
            final Frame train = parseTestFile("./smalldata/testng/airlines_train.csv");
            Scope.track(train);

            GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
            gbmParameters._train = train._key;
            gbmParameters._response_column = "IsDepDelayed";
            gbmParameters._seed = 123;

            Object[][] hyperParams = new Object[][] {
                    new Object[] {"_learn_rate", "_score_tree_interval"},
                    new Object[] {       0.5   ,                     2 },
                    new Object[] {       0.2   ,                     4 },
                    new Object[] {       0.1   ,                     8 },
                    new Object[] {       0.05  ,                    16 },
                    new Object[] {       0.02  ,                    32 },
                    new Object[] {       0.01  ,                    64 },
                    new Object[] {       0.005 ,                   128 },
                    new Object[] {       0.002 ,                   256 },
                    new Object[] {       0.001 ,                   512 },
            };

            Job<Grid> job= GridSearch.startGridSearch(
                    Key.make("seq_test_grid"),
                    new SequentialWalker<>(
                            gbmParameters,
                            hyperParams,
                            new SimpleParametersBuilderFactory<>(),
                            new HyperSpaceSearchCriteria.SequentialSearchCriteria(StoppingCriteria.create()
                                    .stoppingRounds(1)
                                    .stoppingMetric(StoppingMetric.AUC)
                                    .stoppingTolerance(0.1)
                                    .build())
                    ),
                    GridSearch.SEQUENTIAL_MODEL_BUILDING
            );
            Scope.track_generic(job);
            final Grid grid = job.get();
            Scope.track_generic(grid);

            assertTrue(grid.getModelCount() < 9);
            assertEquals(3, grid.getModelCount()); //for those parameters at least

        } finally {
            Scope.exit();
        }

    }
}
