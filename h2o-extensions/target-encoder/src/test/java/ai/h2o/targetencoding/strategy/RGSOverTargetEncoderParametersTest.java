package ai.h2o.targetencoding.strategy;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.grid.filter.*;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import java.util.*;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

public class RGSOverTargetEncoderParametersTest extends TestUtil {

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


    PermutationFilterFunction filterFunction1 = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>(permutation -> !permutation._blending);

    PermutationFilterFunction filterFunction2 = new StrictFilterFunction<TargetEncoderModel.TargetEncoderParameters>(gridItem -> !(gridItem._k == 3.0 && gridItem._f == 1.0));
    
    List<PermutationFilterFunction> filterFunctions = Arrays.asList(filterFunction1, filterFunction2);

    PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter = new DefaultPermutationFilter(filterFunctions);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    List<TargetEncoderModel.TargetEncoderParameters> evaluatedGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
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

  @Test
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

    PermutationFilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>(gridItem -> !gridItem._blending);

    List<PermutationFilterFunction> filterFunctions = singletonList(blendingFilterFunction);

    PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter = new DefaultPermutationFilter(filterFunctions);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    List<TargetEncoderModel.TargetEncoderParameters> evaluatedGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        evaluatedGridItems.add(targetEncoderParameters);
      }
    }
    System.out.println("\nAll grid items after applying filtering functions:");
    evaluatedGridItems.forEach(System.out::println);

    assertEquals(10, evaluatedGridItems.size());
  }

  @Test
  public void max_model_is_honored_when_one_model_has_failed() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    int maxModels = 10;
    int numberOfFailedModels = 1;
    hyperSpaceSearchCriteria.set_max_models(maxModels);

    PermutationFilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>((gridItem) -> !gridItem._blending);

    List<PermutationFilterFunction> filterFunctions = singletonList(blendingFilterFunction);

    PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter = new DefaultPermutationFilter(filterFunctions);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    List<TargetEncoderModel.TargetEncoderParameters> returnedGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
      
      // For the sake of test let's pretend that 3rd parameters lead to model's failure. 
      // By notifying `iterator` we will be able to reach `max_model` number of models
      if(returnedGridItems.size() == 3) {
        iterator.modelFailed(null);
      }
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        returnedGridItems.add(targetEncoderParameters);
      }
    }

    assertEquals(maxModels + numberOfFailedModels, returnedGridItems.size());
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

    PermutationFilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction<TargetEncoderModel.TargetEncoderParameters>((gridItem) -> !gridItem._blending);

    List<PermutationFilterFunction> filterFunctions = singletonList(blendingFilterFunction);

    PermutationFilter<TargetEncoderModel.TargetEncoderParameters> defaultPermutationFilter = new DefaultPermutationFilter(filterFunctions);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    defaultPermutationFilter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    List<TargetEncoderModel.TargetEncoderParameters> returnedGridItems = new ArrayList<>();
    boolean wasResetOnce = false;
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);

      if(!wasResetOnce && returnedGridItems.size() == 1) {
        iterator.reset();
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
