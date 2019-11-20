package hex.grid;

import hex.grid.filter.DefaultPermutationFilter;
import hex.grid.filter.PermutationFilter;
import hex.grid.filter.PermutationFilterFunction;
import hex.grid.filter.StrictFilterFunction;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import java.util.*;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

/**
 * Note: exact type of {@code Model.Parameters} is transparent to filtering functionality 
 * so we can test on any of the existing ones.
 */
public class GridSearchWithFilteringTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }
  
  @Test
  public void randomGridSearchWithFunctionsToFilterOutUnnecessaryGridItems_GBMParametersGrid() {
    Map<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_ntrees", new Integer[]{10000});
    hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}); 
    hpGrid.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100}); 
    hpGrid.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8}); 
    hpGrid.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00}); 
    hpGrid.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0}); 
    hpGrid.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0}); 
    hpGrid.put("_min_split_improvement", new Double[]{1e-4, 1e-5});

    int totalNumberOfPermutationsInHPGrid = 87480;

    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

    PermutationFilterFunction strictFilterFunction = new StrictFilterFunction<GBMModel.GBMParameters>(permutation -> {
      return !(permutation._max_depth >= 15.0 && permutation._min_rows >= 50);
    }
    );
    int expectedNumberOfFilteredOutPermutations = 2916;

    List<PermutationFilterFunction> filterFunctions = singletonList(strictFilterFunction);

    PermutationFilter<GBMModel.GBMParameters> defaultPermutationFilter = new DefaultPermutationFilter(filterFunctions);

    HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<GBMModel.GBMParameters> iterator = walker.iterator();

    List<GBMModel.GBMParameters> filteredGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      GBMModel.GBMParameters gbmParams = iterator.nextModelParameters(null);
      if( gbmParams != null) { // we might have had next element ( iterator.hasNext) = true) but it could be filtered out by filtering functions
        filteredGridItems.add(gbmParams);
      }
    }

    assertEquals(totalNumberOfPermutationsInHPGrid - expectedNumberOfFilteredOutPermutations, filteredGridItems.size());
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

    PermutationFilterFunction strictFilterFunction = new StrictFilterFunction<GBMModel.GBMParameters>(gridItem -> {
      return !(gridItem._max_depth >= 15.0 && gridItem._min_rows >= 30);
    }
    );
    
    int expectedNumberOfFilteredOutPermutations = 6;

    List<PermutationFilterFunction> filterFunctions = singletonList(strictFilterFunction);

    PermutationFilter<GBMModel.GBMParameters> defaultPermutationFilter = new DefaultPermutationFilter(filterFunctions);

    HyperSpaceWalker.CartesianWalker<GBMModel.GBMParameters> walker =
            new HyperSpaceWalker.CartesianWalker<>(gbmParameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<GBMModel.GBMParameters> iterator = walker.iterator();

    List<GBMModel.GBMParameters> filteredGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      GBMModel.GBMParameters gbmParams = iterator.nextModelParameters(null);
      if( gbmParams != null) { // we might have had next element ( iterator.hasNext) = true) but it could be filtered out by filtering functions
        filteredGridItems.add(gbmParams);
      }
    }

    assertEquals(totalNumberOfPermutationsInHPGrid - expectedNumberOfFilteredOutPermutations, filteredGridItems.size());
  }
}
