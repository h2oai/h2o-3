package hex.grid;

import hex.Model;
import hex.grid.filter.*;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import water.Job;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.util.ArrayUtils;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Note: exact type of {@code Model.Parameters} is transparent to filtering functionality 
 * so we can test on any of the existing ones.
 */
@RunWith(Enclosed.class)
public class GridSearchWithFilteringTest{

  public static class GridSearchWithFilteringNonParametrizedTest extends TestUtil {
    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Test
    public void FilteredWalker_is_able_to_filter_out_range_of_parameters() {
      Map<String, Object[]> hpGrid = new HashMap<>();
      hpGrid.put("_ntrees", new Integer[]{10000});
      hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
      hpGrid.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100});
      hpGrid.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8});
      hpGrid.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00});
      hpGrid.put("_col_sample_rate", new Double[]{0.4, 0.7, 1.0});
      hpGrid.put("_col_sample_rate_per_tree", new Double[]{0.4, 0.7, 1.0});
      hpGrid.put("_min_split_improvement", new Double[]{1e-4, 1e-5});

      int totalNumberOfPermutationsInHPGrid = 87480;

      GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

      FilterFunction strictFilterFunction = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> {
        return !(permutation._max_depth >= 15.0 && permutation._min_rows >= 50);
      }
      );
      int expectedNumberOfFilteredOutPermutations = 2916;

      PermutationFilter<GBMModel.GBMParameters> defaultPermutationFilter = new AnyMatchPermutationFilter(strictFilterFunction);

      HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
              new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                      hpGrid,
                      simpleParametersBuilderFactory,
                      hyperSpaceSearchCriteria);

      FilteredWalker filteredWalker = new FilteredWalker(walker, defaultPermutationFilter);

      HyperSpaceWalker.HyperSpaceIterator<GBMModel.GBMParameters> filteredIterator = filteredWalker.iterator();

      List<GBMModel.GBMParameters> filteredGridItems = new ArrayList<>();
      while (filteredIterator.hasNext(null)) {
        GBMModel.GBMParameters gbmParams = filteredIterator.nextModelParameters(null);
        if (gbmParams != null) { // we might have had next element ( iterator.hasNext) = true) but it could be filtered out by filtering functions
          filteredGridItems.add(gbmParams);
        }
      }

      assertEquals(totalNumberOfPermutationsInHPGrid - expectedNumberOfFilteredOutPermutations, filteredGridItems.size());
    }

    @Test
    public void FilteredWalker_respects_max_models() {
      Scope.enter();
      try {
        final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
        Scope.track(trainingFrame);

        Map<String, Object[]> hpGrid = new HashMap<>();
        hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9});

        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        gbmParameters._train = trainingFrame._key;
        gbmParameters._response_column = "species";

        GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

        HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

        int max_models = 5;
        hyperSpaceSearchCriteria.set_max_models(max_models);

        FilterFunction filterFunctionA = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> {
          return !(permutation._max_depth == 6);
        }
        );

        PermutationFilter<GBMModel.GBMParameters> filterA = new AnyMatchPermutationFilter(filterFunctionA);

        HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
                new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                        hpGrid,
                        simpleParametersBuilderFactory,
                        hyperSpaceSearchCriteria);

        FilteredWalker filteredWalker = new FilteredWalker(walker, filterA);

        Job<Grid> gs = GridSearch.startGridSearch(null, filteredWalker, 1);

        Scope.track_generic(gs);
        final Grid grid = gs.get();
        Scope.track_generic(grid);

        assertEquals(max_models, gs.get().getModelCount());
        assertFalse(Arrays.stream(gs.get().getModels()).anyMatch(model -> ((GBMModel.GBMParameters) model._parms)._max_depth == 6));
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void FilteredWalker_stops_when_max_models_is_greater_than_number_of_valid_permutations() {
      Scope.enter();
      try {
        final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
        Scope.track(trainingFrame);

        Map<String, Object[]> hpGrid = new HashMap<>();
        hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9});

        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        gbmParameters._train = trainingFrame._key;
        gbmParameters._response_column = "species";

        GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

        HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

        int max_models = 5;
        hyperSpaceSearchCriteria.set_max_models(max_models);

        FilterFunction filterFunctionA = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> {
          return !(permutation._max_depth > 6);
        }
        );

        PermutationFilter<GBMModel.GBMParameters> filterA = new AnyMatchPermutationFilter(filterFunctionA);

        HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
                new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                        hpGrid,
                        simpleParametersBuilderFactory,
                        hyperSpaceSearchCriteria);

        FilteredWalker filteredWalker = new FilteredWalker(walker, filterA);

        Job<Grid> gs = GridSearch.startGridSearch(null, filteredWalker, 1);

        Scope.track_generic(gs);
        final Grid grid = gs.get();
        Scope.track_generic(grid);

        // Only 4 parameters are less or equal than 6
        int expectedNumberOfModels = 4;

        assertEquals(expectedNumberOfModels, gs.get().getModelCount());
        assertFalse(Arrays.stream(gs.get().getModels()).anyMatch(model -> ((GBMModel.GBMParameters) model._parms)._max_depth > 6));
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void FilteredWalker_with_nesting_applies_AND_rule() {
      Scope.enter();
      try {
        final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
        Scope.track(trainingFrame);

        Map<String, Object[]> hpGrid = new HashMap<>();
        hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9});

        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        gbmParameters._train = trainingFrame._key;
        gbmParameters._response_column = "species";

        GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

        HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

        FilterFunction filterFunctionA = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> !(permutation._max_depth == 6));

        PermutationFilter<GBMModel.GBMParameters> filterA = new AnyMatchPermutationFilter(filterFunctionA);

        HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
                new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                        hpGrid,
                        simpleParametersBuilderFactory,
                        hyperSpaceSearchCriteria);

        FilteredWalker innerFilteredWalker = new FilteredWalker(walker, filterA);

        FilterFunction filterFunctionB = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> permutation._max_depth == 6);

        PermutationFilter<GBMModel.GBMParameters> filterB = new AnyMatchPermutationFilter(filterFunctionB);

        FilteredWalker filteredWalker = new FilteredWalker(innerFilteredWalker, filterB);

        Job<Grid> gs = GridSearch.startGridSearch(null, filteredWalker, 1);

        Scope.track_generic(gs);
        final Grid grid = gs.get();
        Scope.track_generic(grid);

        // Opposite filters do not intersect, therefore 0 models will be returned
        int expectedNumberOfModels = 0;
        assertEquals(expectedNumberOfModels, gs.get().getModelCount());
      } finally {
        Scope.exit();
      }
    }

    @Test
    public void cartesianGridSearchWithFunctionsToFilterOutUnnecessaryGridItems_GBMParametersGrid() {
      Map<String, Object[]> hpGrid = new HashMap<>();
      hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17});
      hpGrid.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100});

      int totalNumberOfPermutationsInHPGrid = 90;

      GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

      HyperSpaceSearchCriteria.CartesianSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.CartesianSearchCriteria();

      FilterFunction strictFilterFunction = new StrictFilterFunction<GBMModel.GBMParameters>(gridItem -> {
        return !(gridItem._max_depth >= 15.0 && gridItem._min_rows >= 30);
      }
      );

      int expectedNumberOfFilteredOutPermutations = 6;

      PermutationFilter<GBMModel.GBMParameters> defaultPermutationFilter = new AnyMatchPermutationFilter(strictFilterFunction);

      HyperSpaceWalker.CartesianWalker<GBMModel.GBMParameters> walker =
              new HyperSpaceWalker.CartesianWalker<>(gbmParameters,
                      hpGrid,
                      simpleParametersBuilderFactory,
                      hyperSpaceSearchCriteria);

      FilteredWalker filteredWalker = new FilteredWalker(walker, defaultPermutationFilter);

      HyperSpaceWalker.HyperSpaceIterator<GBMModel.GBMParameters> filteredIterator = filteredWalker.iterator();

      List<GBMModel.GBMParameters> filteredGridItems = new ArrayList<>();
      while (filteredIterator.hasNext(null)) {
        GBMModel.GBMParameters gbmParams = filteredIterator.nextModelParameters(null);
        if (gbmParams != null) { // we might have had next element ( iterator.hasNext) = true) but it could be filtered out by filtering functions
          filteredGridItems.add(gbmParams);
        }
      }

      assertEquals(totalNumberOfPermutationsInHPGrid - expectedNumberOfFilteredOutPermutations, filteredGridItems.size());
    }
  }


  @RunWith(Parameterized.class)
  public static class GridSearchWithFilteringParametrizedTest extends TestUtil {
    @BeforeClass
    public static void setup() {
      stall_till_cloudsize(1);
    }

    @Parameterized.Parameters(name = "RGS parallelism = {0}")
    public static Object[] parallelism() {
      return new Integer[]{1, 2, 4};
    }

    @Parameterized.Parameter
    public int parallelism;

    @Test // case when `max_models` is greater than number of elements left after using first filter
    public void FilteredWalker_with_nesting_respects_max_models() {
      Scope.enter();
      try {
        final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
        Scope.track(trainingFrame);

        Map<String, Object[]> hpGrid = new HashMap<>();
        hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9});

        GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
        gbmParameters._train = trainingFrame._key;
        gbmParameters._response_column = "species";

        GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

        HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

        int max_models = 2;
        hyperSpaceSearchCriteria.set_max_models(max_models);

        FilterFunction filterFunctionA = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> {
          return !(permutation._max_depth == 6);
        }
        );

        PermutationFilter<GBMModel.GBMParameters> filterA = new AnyMatchPermutationFilter(filterFunctionA);

        HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
                new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                        hpGrid,
                        simpleParametersBuilderFactory,
                        hyperSpaceSearchCriteria);

        FilteredWalker innerFilteredWalker = new FilteredWalker(walker, filterA);

        FilterFunction filterFunctionB = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> {
          return !(permutation._max_depth == 7);
        }
        );

        PermutationFilter<GBMModel.GBMParameters> filterB = new AnyMatchPermutationFilter(filterFunctionB);

        FilteredWalker filteredWalker = new FilteredWalker(innerFilteredWalker, filterB);

        Job<Grid> gs = GridSearch.startGridSearch(null, filteredWalker, parallelism);

        Scope.track_generic(gs);
        final Grid grid = gs.get();
        Scope.track_generic(grid);

        assertEquals(max_models, gs.get().getModelCount());

        Model[] models = gs.get().getModels();
        int[] expectedToBeFilteredOutParams = {6, 7};
        assertTrue(!ArrayUtils.contains(expectedToBeFilteredOutParams, ((GBMModel.GBMParameters) models[0]._parms)._max_depth));
        assertTrue(!ArrayUtils.contains(expectedToBeFilteredOutParams, ((GBMModel.GBMParameters) models[1]._parms)._max_depth));
      } finally {
        Scope.exit();
      }
    }

  }
}
