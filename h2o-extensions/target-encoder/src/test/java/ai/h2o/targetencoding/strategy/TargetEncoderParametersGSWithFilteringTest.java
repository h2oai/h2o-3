package ai.h2o.targetencoding.strategy;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.Grid;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.grid.filter.*;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import water.Job;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TargetEncoderParametersGSWithFilteringTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void randomGridSearchWithFunctionsToFilterOutUnnecessaryGridItems() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();


    FilterFunction filterFunction1 = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>(permutation -> !permutation._blending);

    FilterFunction filterFunction2 = new StrictFilterFunction<TargetEncoderModel.TargetEncoderParameters>(gridItem -> !(gridItem._k == 3.0 && gridItem._f == 1.0));
    
    PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter =
            new AnyMatchPermutationFilter<>(filterFunction1, filterFunction2);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria);

    FilteredWalker filteredWalker = new FilteredWalker(walker, defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> filteredIterator = filteredWalker.iterator();

    List<TargetEncoderModel.TargetEncoderParameters> evaluatedGridItems = new ArrayList<>();
    while (filteredIterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = filteredIterator.nextModelParameters(null);
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        evaluatedGridItems.add(targetEncoderParameters);
      }
    }
    System.out.println("\nAll grid items after applying filtering functions:");
    evaluatedGridItems.forEach(System.out::println);

    // Expected number of the grid items is a result of filtering out permutations with two filtering functions 
    // and taking into account that there are intersections of these functions 
    //
    //       total    filtered by filterFunction1 except first match          filtered by filterFunction2       overlap
    //  25 = 54     - (27 - 1)                                                - 6                               + 3 

    assertEquals(25, evaluatedGridItems.size());
  }

  @Ignore("Should be fixed with PUBDEV-7204")
  public void randomGridSearchWithFilteringFunctionsWhenMaxModelsIsDefined() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    hyperSpaceSearchCriteria.set_max_models(10);

    FilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>(gridItem -> !gridItem._blending);

    PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter = new AnyMatchPermutationFilter<>(blendingFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria);

    FilteredWalker filteredWalker = new FilteredWalker(walker, defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> filteredIterator = filteredWalker.iterator();

    List<TargetEncoderModel.TargetEncoderParameters> evaluatedGridItems = new ArrayList<>();
    while (filteredIterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = filteredIterator.nextModelParameters(null);
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        evaluatedGridItems.add(targetEncoderParameters);
      }
    }
    System.out.println("\nAll grid items after applying filtering functions:");
    evaluatedGridItems.forEach(System.out::println);

    assertEquals(10, evaluatedGridItems.size());
  }

  @Ignore("Should be fixed with PUBDEV-7204")
  public void max_model_is_honored_when_one_model_has_failed() {
    Scope.enter();
    try {
      final Frame trainingFrame = parse_test_file("smalldata/iris/iris_train.csv");
      Scope.track(trainingFrame);

      HashMap<String, Object[]> hpGrid = new HashMap<>();
      hpGrid.put("_blending", new Boolean[]{true, false});
      hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
      hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
      hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

      TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
      parameters._train = trainingFrame._key;
      parameters._response_column = "species";

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      int maxModels = 53;
      int numberOfFailedModels = 1;
      hyperSpaceSearchCriteria.set_max_models(maxModels);

      FilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>((gridItem) -> !gridItem._blending);

      PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter = new AnyMatchPermutationFilter<>(blendingFilterFunction);

      HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
              new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                      hpGrid,
                      simpleParametersBuilderFactory,
                      hyperSpaceSearchCriteria);

      FilteredWalker filteredWalker = new FilteredWalker(walker, defaultPermutationFilter);

      Job<Grid> gs = GridSearch.startGridSearch(null, filteredWalker, 1);

      Scope.track_generic(gs);
      final Grid grid = gs.get();
      Scope.track_generic(grid);

      assertEquals(maxModels + numberOfFailedModels, grid.getModelCount());
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void rgs_reset_is_working() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0});
    hpGrid.put("_f", new Double[]{3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

    FilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>((gridItem) -> !gridItem._blending);

    PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter =
            new AnyMatchPermutationFilter<>(blendingFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria);

    FilteredWalker filteredWalker = new FilteredWalker(walker, defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> filteredIterator = filteredWalker.iterator();

    List<TargetEncoderModel.TargetEncoderParameters> returnedGridItems = new ArrayList<>();
    boolean wasResetOnce = false;
    while (filteredIterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = filteredIterator.nextModelParameters(null);

      if(!wasResetOnce && returnedGridItems.size() == 1) {
        filteredIterator.reset();
        wasResetOnce = true;
      } else {
        if (targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
          returnedGridItems.add(targetEncoderParameters);
        }
      }
    }

    // Without resetting iterator would return only one grid item for evaluation. With one reset - we get two returned grid items correspondingly.
    assertEquals(2, returnedGridItems.size());
  }
  
}
