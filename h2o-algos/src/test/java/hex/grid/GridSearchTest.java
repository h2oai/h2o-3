package hex.grid;

import hex.Model;
import hex.genmodel.utils.DistributionFamily;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.*;
import water.fvec.Frame;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

public class GridSearchTest extends TestUtil {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @BeforeClass
  public static void setUp() {
    stall_till_cloudsize(1);
  }

  @Test
  public void testMaxModelEarlyStopping() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_max_depth", new Integer[]{2 ,3});
        put("_min_rows", new Integer[]{5, 10, 15});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      hyperSpaceSearchCriteria.set_max_models(3);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 1);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      assertEquals(3, gs.get().getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testNoMaxModelBasedEarlyStoppingWillHappenWhenSetToZero() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_max_depth", new Integer[]{2 ,3});
        put("_min_rows", new Integer[]{5, 10, 15});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      hyperSpaceSearchCriteria.set_max_models(0); // just to make it more explicit as actually default values is 0

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 1);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      assertEquals(6, gs.get().getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testMaxModelIsSatisfiedWhenSomeModelsHaveFailed() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      final Integer min_rows_value_that_cause_error = 5000000;

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_max_depth", new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        put("_min_rows", new Integer[]{10, min_rows_value_that_cause_error});
      }};

      // From total of 20 combinations only 10 are expected to be valid.
      int numberOfValidCombinations = 10;

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      hyperSpaceSearchCriteria.set_max_models(numberOfValidCombinations);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 1);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      // There is a chance to get expected number of models without hitting failing combination with `min_rows` = `min_rows_value_that_cause_error`
      // but it is quite a small one and false positive tests would not hurt
      assertEquals(numberOfValidCombinations, gs.get().getModelCount());

      // Test case 2.
      // Let's test that we actually failing with `min_rows` = `min_rows_value_that_cause_error`
      int max_models_one_more_than_number_of_valid_ones = numberOfValidCombinations + 1;
      hyperSpaceSearchCriteria.set_max_models(max_models_one_more_than_number_of_valid_ones);

      gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 1);

      final Grid grid2 = gs.get();
      Scope.track_generic(grid2);
      // We expect not to get more than number of valid combinations
      assertEquals(numberOfValidCombinations, gs.get().getModelCount());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_parallel_grid_search_MaxModelIsSatisfiedWhenSomeModelsHaveFailed() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      final Integer min_rows_value_that_cause_error = 5000000;

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_max_depth", new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        put("_min_rows", new Integer[]{10, min_rows_value_that_cause_error});
      }};

      // From total of 20 combinations only 10 are expected to be valid.
      int numberOfValidCombinations = 10;

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      hyperSpaceSearchCriteria.set_max_models(numberOfValidCombinations);
      hyperSpaceSearchCriteria.set_stopping_rounds(10000);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 4);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);


      Arrays.asList(grid.getModels()).forEach(m -> {
        GBMModel.GBMParameters parms = (GBMModel.GBMParameters) m._parms;
        System.out.println(parms._max_depth + " : " + parms._min_rows);
      });

      Arrays.asList(grid.getFailures().getFailedParameters()).forEach(m -> {
        GBMModel.GBMParameters parms = (GBMModel.GBMParameters) m;
        System.out.println("Failed: " + parms._max_depth + " : " + parms._min_rows);
      });

      // There is a chance to get expected number of models without hitting failing combination with `min_rows` = `min_rows_value_that_cause_error`
      // but it is quite a small one and false positive tests would not hurt
      assertEquals(numberOfValidCombinations, grid.getModelCount());

      // Test case 2.
      // Let's test that we actually failing with `min_rows` = `min_rows_value_that_cause_error`
      int max_models_one_more_than_number_of_valid_ones = numberOfValidCombinations + 1;
      hyperSpaceSearchCriteria.set_max_models(max_models_one_more_than_number_of_valid_ones);

      gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 6);

      final Grid grid2 = gs.get();
      Scope.track_generic(grid2);

      Arrays.asList(grid2.getModels()).forEach(m -> {
        GBMModel.GBMParameters parms = (GBMModel.GBMParameters) m._parms;
        System.out.println(parms._max_depth + " : " + parms._min_rows);
      });

      Arrays.asList(grid2.getFailures().getFailedParameters()).forEach(m -> {
        GBMModel.GBMParameters parms = (GBMModel.GBMParameters) m;
        System.out.println("Failed: " + parms._max_depth + " : " + parms._min_rows);
      });
      // We expect not to get more than number of valid combinations
      assertEquals(10, grid2.getModelCount());
      assertEquals(20, grid2.getModelCount() + grid2.getFailures().getFailureCount());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParallelModelTimeConstraint() {
    try {
      Scope.enter();

      final Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10, 500};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria searchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      Job<Grid> gridSearch = GridSearch.startGridSearch(null, params,
              hyperParms,
              new GridSearch.SimpleParametersBuilderFactory(),
              searchCriteria, 2);

      searchCriteria.set_max_runtime_secs(1d);

      Scope.track_generic(gridSearch);
      final Grid grid = gridSearch.get();
      Scope.track_generic(grid);

     assertNotEquals(ntreesArr.length * maxDepthArr.length, grid.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParallelUserStopRequest() {
    try {
      Scope.enter();

      final Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10, 500};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4, 50};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      Job<Grid> gridSearch = GridSearch.startGridSearch(null, params,
              hyperParms,
              new GridSearch.SimpleParametersBuilderFactory(),
              new HyperSpaceSearchCriteria.CartesianSearchCriteria(), 2);
      Scope.track_generic(gridSearch);
      gridSearch.stop();
      final Grid grid = gridSearch.get();
      Scope.track_generic(grid);
      
      for(Model m : grid.getModels()){
        Scope.track_generic(m);
      }
    
      assertNotEquals(ntreesArr.length * maxDepthArr.length, grid.getModelCount());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testParallelGridSearch() {
    try {
      Scope.enter();

      final Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms,
              5);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      assertEquals(ntreesArr.length * maxDepthArr.length, grid.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testAdaptiveParallelGridSearch() {
    try {
      Scope.enter();

      final Frame trainingFrame = parse_test_file("./smalldata/testng/airlines_train.csv");
      Scope.track(trainingFrame);

      final Integer[] ntreesArr = new Integer[]{5, 50, 7, 8, 9, 10};
      final Integer[] maxDepthArr = new Integer[]{2, 3, 4};
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", ntreesArr);
        put("_max_depth", maxDepthArr);
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "IsDepDelayed";
      params._seed = 42;

      final Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms,
              GridSearch.getAdaptiveParallelism());
      Scope.track_generic(gs);
      final Grid secondGrid = gs.get();
      Scope.track_generic(secondGrid);
      assertEquals(ntreesArr.length * maxDepthArr.length, secondGrid.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void testFaileH2OdParamsCleanup() {
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

  @Test
  public void gridSearchExportCheckpointsDir() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";
      params._export_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms);
      Scope.track_generic(gs);
      final Grid originalGrid = gs.get();
      Scope.track_generic(originalGrid);

      final File serializedGridFile = new File(params._export_checkpoints_dir, originalGrid._key.toString());
      assertTrue(serializedGridFile.exists());
      assertTrue(serializedGridFile.isFile());
      
      final Grid grid = loadGridFromFile(serializedGridFile);
      assertArrayEquals(originalGrid.getModelKeys(), grid.getModelKeys());
      Scope.track_generic(grid);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void gridSearchManualExport() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      final String exportDir = temporaryFolder.newFolder().getAbsolutePath();

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, 1);
      Scope.track_generic(gs);
      final Grid originalGrid = gs.get();
      Scope.track_generic(originalGrid);
      
      final String originalGridPath = exportDir + "/" + originalGrid._key.toString();
      originalGrid.exportBinary(originalGridPath);
      assertTrue(Files.exists(Paths.get(originalGridPath)));
      
      originalGrid.exportModelsBinary(exportDir);
      
      for(Model model : originalGrid.getModels()){
        assertTrue(Files.exists(Paths.get(exportDir, model._key.toString())));  
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void gridSearchExportCheckpointsDirParallel() throws IOException {
    try {
      Scope.enter();

      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5, 10, 50});
        put("_max_depth", new Integer[]{2,3});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";
      params._export_checkpoints_dir = temporaryFolder.newFolder().getAbsolutePath();

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, 2);
      Scope.track_generic(gs);
      final Grid originalGrid = gs.get();
      Scope.track_generic(originalGrid);

      final File serializedGridFile = new File(params._export_checkpoints_dir, originalGrid._key.toString());
      assertTrue(serializedGridFile.exists());
      assertTrue(serializedGridFile.isFile());

      final Grid grid = loadGridFromFile(serializedGridFile);
      assertArrayEquals(originalGrid.getModelKeys(), grid.getModelKeys());
      Scope.track_generic(grid);
      
      
    } finally {
      Scope.exit();
    }
  }

  private static Grid loadGridFromFile(final File file) throws IOException {
    try (final FileInputStream fileInputStream = new FileInputStream(file)) {
      final AutoBuffer autoBuffer = new AutoBuffer(fileInputStream);
      final Freezable possibleGrid = autoBuffer.get();
      assertTrue(possibleGrid instanceof Grid);
      return (Grid) possibleGrid;
      
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
        put("_min_rows", new Integer[]{10});
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

  @Test
  public void testGetModelKeys() {
    Grid<?> grid = new Grid<>(null, null, null, null);
    grid.putModel(3, Key.make("2"));
    grid.putModel(2, Key.make("1"));
    grid.putModel(1, Key.make("3"));

    assertArrayEquals(
            new Key[]{Key.make("1"), Key.make("2"), Key.make("3")},
            grid.getModelKeys()
    );
  }

  @Test
  public void testParallelCartesian() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, 2);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(5, grid1.getModelCount());

      // Train a grid with new hyper parameters
      hyperParms.put("_learn_rate", new Double[]{0.5});
      gs = GridSearch.startGridSearch(grid1._key, params, hyperParms, 2);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      int expectedNumberOfModelsFromTwoGrids = 10;
      assertEquals(expectedNumberOfModelsFromTwoGrids, grid.getModelCount());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_parallel_random_search_with_max_models_being_less_than_parallelism() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      int custom_max_model = 2;
      hyperSpaceSearchCriteria.set_max_models(custom_max_model);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 4);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(custom_max_model, grid1.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_parallel_random_search_with_max_models_being_greater_than_parallelism() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      int custom_max_model = 3;
      hyperSpaceSearchCriteria.set_max_models(custom_max_model);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 2);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(custom_max_model, grid1.getModelCount());
    } finally {
      Scope.exit();
    }
  }


  // Multirun tests
  @Test
  public void testParallelRandomSearchWithDefaultMaxModels_multirun() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 6);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(5, grid1.getModelCount());

      // Train a grid with new hyper parameters
      hyperParms.put("_learn_rate", new Double[]{0.5});
      gs = GridSearch.startGridSearch(grid1._key, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 6);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      int expectedNumberOfModelsFromTwoGrids = 10;
      assertEquals(expectedNumberOfModelsFromTwoGrids, grid.getModelCount());
    } finally {
      Scope.exit();
    }
  }


  @Test
  public void test_parallel_random_search_with_max_models_greater_than_parallelism_multirun() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      int custom_max_model = 3;
      hyperSpaceSearchCriteria.set_max_models(custom_max_model);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 2);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(custom_max_model, grid1.getModelCount());

      // Train a grid with new hyper parameters
      hyperParms.put("_learn_rate", new Double[]{0.5});
      gs = GridSearch.startGridSearch(grid1._key, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 2);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      int expectedNumberOfModelsFromTwoGrids = 2 * custom_max_model;
      assertEquals(expectedNumberOfModelsFromTwoGrids, grid.getModelCount());

    } finally {
      Scope.exit();
    }
  }

  @Test
  public void test_parallel_random_search_with_max_models_less_than_parallelism_multirun() {
    try {
      Scope.enter();
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      // Setup random hyperparameter search space
      HashMap<String, Object[]> hyperParms = new HashMap<String, Object[]>() {{
        put("_distribution", new DistributionFamily[]{DistributionFamily.multinomial});
        put("_ntrees", new Integer[]{5});
        put("_max_depth", new Integer[]{2});
        put("_min_rows", new Integer[]{10,11,12,13,14});
        put("_learn_rate", new Double[]{.7});
      }};

      GBMModel.GBMParameters params = new GBMModel.GBMParameters();
      params._train = trainingFrame._key;
      params._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      int custom_max_model = 3;
      hyperSpaceSearchCriteria.set_max_models(custom_max_model);

      Job<Grid> gs = GridSearch.startGridSearch(null, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 4);
      Scope.track_generic(gs);
      final Grid grid1 = gs.get();
      Scope.track_generic(grid1);

      assertEquals(custom_max_model, grid1.getModelCount());

      // Train a grid with new hyper parameters
      hyperParms.put("_learn_rate", new Double[]{0.5});
      gs = GridSearch.startGridSearch(grid1._key, params, hyperParms, simpleParametersBuilderFactory, hyperSpaceSearchCriteria, 4);
      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      int expectedNumberOfModelsFromTwoGrids = 2*custom_max_model;
      assertEquals(expectedNumberOfModelsFromTwoGrids, grid.getModelCount());

    } finally {
      Scope.exit();
    }
  }

}
