package hex.grid;

import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBMModel;
import org.junit.Before;
import org.junit.Test;
import water.Job;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.HashMap;

import static org.junit.Assert.*;

public class GridTest extends TestUtil {

  @Before
  public void setUp() throws Exception {
    TestUtil.stall_till_cloudsize(1);
  }

  @Test
  public void testFailedParamsCleanup() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{5000000}); // Invalid hyperparameter, causes model training to fail
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
      Scope.track_generic(gs);
      final Grid errGrid = gs.get();
      Scope.track_generic(errGrid);

      assertEquals(0, errGrid.getModelCount());

      final Grid.SearchFailure failures = errGrid.getFailures();
      assertEquals(1, failures.getFailureCount());
      assertEquals(1, failures.getFailedParameters().length);
      assertEquals(1, failures.getFailedRawParameters().length);
      assertEquals(1, failures.getFailureDetails().length);
      assertEquals(1, failures.getFailureStackTraces().length);

      // Check if the error is related to the specified invalid hyperparameter
      final String expectedErr = "Details: ERRR on field: _min_rows: The dataset size is too small to split for min_rows=5000000.0: must have at least 1.0E7 (weighted) rows";
      assertTrue(failures.getFailureStackTraces()[0].contains(expectedErr));


      //Set the parameter to an acceptable value
      hyperParms.put("_min_rows", new Integer[]{10});
      gs = GridSearch.startGridSearch(errGrid._key, params, hyperParms); // It is important to target the previously created grid
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      // There should be no errors, one resulting model in the grid with previously supplied parameters
      assertEquals(1, grid.getModelCount());
      assertTrue(grid.getModels()[0] instanceof GBMModel);
      assertEquals(10, ((GBMModel) grid.getModels()[0])._parms._min_rows, 0);

      final Grid.SearchFailure secondRunFailures = grid.getFailures();
      assertEquals(0, secondRunFailures.getFailureCount());
      assertEquals(0, secondRunFailures.getFailedParameters().length);
      assertEquals(0, secondRunFailures.getFailedRawParameters().length);
      assertEquals(0, secondRunFailures.getFailureDetails().length);
      assertEquals(0, secondRunFailures.getFailureStackTraces().length);

    } finally {
      Scope.exit();
    }
  }


  /**
   * Failed parameters related to an existing model should be retained after repeated launch of grid search
   */
  @Test
  public void testFailedParamsRetention() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10}); // Invalid hyperparameter, causes model training to fail
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
      Scope.track_generic(gs);
      final Grid errGrid = gs.get();
      Scope.track_generic(errGrid);

      assertEquals(1, errGrid.getModelCount());

      Grid.SearchFailure failures = errGrid.getFailures();
      assertEquals(0, failures.getFailureCount());

      errGrid.appendFailedModelParameters(errGrid.getModels()[0]._key, params, new RuntimeException("Test exception"));
      
      failures = errGrid.getFailures();
      assertEquals(1, failures.getFailureCount());

      // Train a second model with modified parameters to forcefully produce a new model
      hyperParms.put("_learn_rate", new Double[]{0.5});
      gs = GridSearch.startGridSearch(errGrid._key, params, hyperParms);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      // There should be no errors, one resulting model in the grid with previously supplied parameters
      assertEquals(2, grid.getModelCount());
      assertTrue(grid.getModels()[0] instanceof GBMModel);
      assertTrue(grid.getModels()[1] instanceof GBMModel);

      final Grid.SearchFailure secondRunFailures = grid.getFailures();
      assertEquals(1, secondRunFailures.getFailureCount());

      final String expectedErr = "Test exception";
      assertTrue(failures.getFailureStackTraces()[0].contains(expectedErr));

    } finally {
      Scope.exit();
    }
  }

}
