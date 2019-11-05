package ai.h2o.targetencoding.strategy;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.grid.filter.KeepOnlyFirstMatchFilterFunction;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.TestUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class RGSWithFilteringTest extends TestUtil {

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


    KeepOnlyFirstMatchFilterFunction blendingFilterFunction = new KeepOnlyFirstMatchFilterFunction((gridItem) -> {
      Object mainHP = gridItem.get("_blending");
      return mainHP instanceof Boolean && !(Boolean) mainHP; // TODO handle cases when type is not that expected
    });

    Function<Map<String, Object>, Boolean> strictFilterFunction = gridItem -> {
      return !((double) gridItem.get("_k") == 3.0 && (double) gridItem.get("_f") == 1.0);
    };

    ArrayList<Function<Map<String, Object>, Boolean>> filterFunctions = new ArrayList<>();
    filterFunctions.add(strictFilterFunction);
    filterFunctions.add(blendingFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters>(parameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    filterFunctions);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();

    ArrayList<TargetEncoderModel.TargetEncoderParameters> filteredGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
      if( targetEncoderParameters != null) { // we might have had next element but it can be filtered out by ffiltering unctions
        filteredGridItems.add(targetEncoderParameters);
      }
    }
    System.out.println("\nAll grid items after applying filtering functions:");
    filteredGridItems.forEach(System.out::println);

    assertEquals(54 - (27 - 1) + 3 - 6, filteredGridItems.size());
  }
  
  @Test
  public void autoMLGBM() {
    Map<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_ntrees", new Integer[]{10000});
    hpGrid.put("_max_depth", new Integer[]{3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}); 
    hpGrid.put("_min_rows", new Integer[]{1, 5, 10, 15, 30, 100}); 
    hpGrid.put("_learn_rate", new Double[]{0.001, 0.005, 0.008, 0.01, 0.05, 0.08, 0.1, 0.5, 0.8}); 
    hpGrid.put("_sample_rate", new Double[]{0.50, 0.60, 0.70, 0.80, 0.90, 1.00}); 
    hpGrid.put("_col_sample_rate", new Double[]{ 0.4, 0.7, 1.0}); 
    hpGrid.put("_col_sample_rate_per_tree", new Double[]{ 0.4, 0.7, 1.0}); 
    hpGrid.put("_min_split_improvement", new Double[]{1e-4, 1e-5}); 

    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();

    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

    Function<Map<String, Object>, Boolean> strictFilterFunction = gridItem -> {
      return !((int) gridItem.get("_max_depth") >= 15.0 && (int) gridItem.get("_min_rows") >= 50);
    };

    ArrayList<Function<Map<String, Object>, Boolean>> filterFunctions = new ArrayList<>();
    filterFunctions.add(strictFilterFunction);

    HyperSpaceWalker.RandomDiscreteValueWalker<GBMModel.GBMParameters> walker =
            new HyperSpaceWalker.RandomDiscreteValueWalker<>(gbmParameters,
                    hpGrid,
                    simpleParametersBuilderFactory,
                    hyperSpaceSearchCriteria,
                    filterFunctions);

    HyperSpaceWalker.HyperSpaceIterator<GBMModel.GBMParameters> iterator = walker.iterator();

    ArrayList<GBMModel.GBMParameters> filteredGridItems = new ArrayList<>();
    while (iterator.hasNext(null)) {
      GBMModel.GBMParameters gbmParams = iterator.nextModelParameters(null);
      if( gbmParams != null) { // we might have had next element ( iterator.hasNext) = true) but it could be filtered out by filtering functions
        filteredGridItems.add(gbmParams);
      }
    }

    assertEquals(87480 - 2916, filteredGridItems.size());
  }


}
