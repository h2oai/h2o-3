package ai.h2o.targetencoding.strategy;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import org.junit.BeforeClass;
import org.junit.Test;
import water.Scope;
import water.TestUtil;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class TargetEncoderRGSTest extends TestUtil {

  @BeforeClass
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void traverseAllGridItemsWithRandomWalker() {

    Scope.enter();
    try {
      HashMap<String, Object[]> hpGrid = new HashMap<>();
      hpGrid.put("_blending", new Boolean[]{true, false});
      hpGrid.put("_noise_level", new Double[]{0.0, 0.01,  0.1});
      // TODO figure out how to work with hierarchical parameters BlendingParams(inflection_point, smoothing) or BlendingParams(k, f)
      hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
      hpGrid.put("_f", new Double[]{5.0, 10.0, 20.0});
      
      TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
      HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, hpGrid, simpleParametersBuilderFactory, hyperSpaceSearchCriteria);

      HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();
      int count = 0;
      while (iterator.hasNext(null)) {
        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
        System.out.println( targetEncoderParameters._blending + ":" +  targetEncoderParameters._noise_level + ":" +  targetEncoderParameters._k + ":" +  targetEncoderParameters._f);
        count++;
      }
      assertEquals("Unexpected number of grid items", 54, count);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void traverseAllGridItemsWithMaterialisedWalker() {

    Scope.enter();
    try {
      HashMap<String, Object[]> hpGrid = new HashMap<>();
      hpGrid.put("_blending", new Boolean[]{true, false});
      hpGrid.put("_noise_level", new Double[]{0.0, 0.01,  0.1});
      // TODO figure out how to work with hierarchical parameters BlendingParams(inflection_point, smoothing) or BlendingParams(k, f)
      hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
      hpGrid.put("_f", new Double[]{5.0, 10.0, 20.0});

      TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

      GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();
      HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

      // Shadowing case: 
      Function<HashMap<String, Object>, HashMap<String, Object>> blendingFilterFunction = gridItem -> {
        Object mainHP = gridItem.get("blending");
        if (mainHP instanceof Boolean && !(Boolean) mainHP) {
          gridItem.replace("k", -1.0);
          gridItem.replace("f", -1.0);
          return gridItem;
        } else {
          return gridItem;
        }
      };

      // Filtering case:
      Function<HashMap<String, Object>, HashMap<String, Object>> strictFilterFunction = gridItem -> {
        if ((double)gridItem.get("k") == 3.0 && (double)gridItem.get("f") == 1.0) {
          return null;
        } else {
          return gridItem;
        }
      };

      ArrayList<Function<HashMap<String, Object>, HashMap<String, Object>>> filterFunctions = new ArrayList<>();
      filterFunctions.add(blendingFilterFunction);
      filterFunctions.add(strictFilterFunction);
      
      HyperSpaceWalker.MaterializedRandomWalker<TargetEncoderModel.TargetEncoderParameters> walker = 
              new HyperSpaceWalker.MaterializedRandomWalker<>(parameters, 
                      hpGrid, 
                      simpleParametersBuilderFactory,
                      hyperSpaceSearchCriteria, 
                      filterFunctions);

      HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();
      int count = 0;
      while (iterator.hasNext(null)) {
        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
        System.out.println( targetEncoderParameters._blending + ":" +  targetEncoderParameters._noise_level + ":" +  targetEncoderParameters._k + ":" +  targetEncoderParameters._f);
        count++;
      }
      assertEquals("Unexpected number of grid items", 54, count);
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void orderOfFieldsSetForModelParametersIsCorrect() {
    //TODO
  }

  @Test
  public void randomGridSearchWithShufflingAndHierarchicalDependencies() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("blending", new Boolean[]{true, false});
    hpGrid.put("noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("f", new Double[]{1.0, 2.0, 3.0});

    ArrayList<HashMap<String, Object>> allElements = new ArrayList<>();
    
    hpGrid.forEach((hpKey, hpValues) -> {
      if(allElements.isEmpty()) {
        Arrays.stream(hpValues).forEach( hpValue -> {
          HashMap<String, Object> newGridItem = new HashMap<>();
          newGridItem.put(hpKey, hpValue);
          allElements.add(newGridItem);
        });
      } else {
        Stream<HashMap<String, Object>> expandedResult = allElements.stream().flatMap(existingItem -> {
          return Arrays.stream(hpValues).map(hpValue -> {
            HashMap<String, Object> clone = (HashMap<String, Object>) existingItem.clone();
            clone.put(hpKey, hpValue);
            return clone;
          });
        });
        ArrayList<HashMap<String, Object>> collect = expandedResult.collect(Collectors.toCollection(ArrayList::new));
        allElements.clear();
        allElements.addAll(collect);
      }
    });

    System.out.println("\nOrdered grid items:");
    allElements.forEach(System.out::println);
         
    System.out.println("\nShuffled grid items:");
    Collections.shuffle(allElements);
    
    allElements.forEach(System.out::println);

    // We need at least 2 types of functions: Shadowing(keep only one item from the group of effectively similar items), Filtering
    
    // Shadowing case: 
    Function<HashMap<String, Object>, HashMap<String, Object>> blendingFilterFunction = gridItem -> {
              Object mainHP = gridItem.get("blending");
              if (mainHP instanceof Boolean && !(Boolean) mainHP) {
                gridItem.replace("k", -1.0);
                gridItem.replace("f", -1.0);
                return gridItem;
              } else {
                return gridItem;
              }
            };
    
    // Filtering case:
    Function<HashMap<String, Object>, HashMap<String, Object>> strictFilterFunction = gridItem -> {
              if ((double)gridItem.get("k") == 3.0 && (double)gridItem.get("f") == 1.0) {
                return null;
              } else {
                return gridItem;
              }
            };

    ArrayList<HashMap<String, Object>> filteredGridItems = allElements.stream()
            .map(blendingFilterFunction::apply)
            .map(strictFilterFunction::apply)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toCollection(ArrayList::new));

    System.out.println("\nShuffled and filtered grid items:");
    filteredGridItems.forEach(System.out::println);

    assertEquals(54 - (24 - 3) - 6, filteredGridItems.size());

  }

  @Test
  public void traversingAllGridItemsWithNewRGS() {

    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("_f", new Double[]{1.0, 2.0, 3.0});

    ArrayList<HashMap<String, Object>> allElements = new ArrayList<>();

    hpGrid.forEach((hpKey, hpValues) -> {
      if(allElements.isEmpty()) {
        Arrays.stream(hpValues).forEach( hpValue -> {
          HashMap<String, Object> newGridItem = new HashMap<>();
          newGridItem.put(hpKey, hpValue);
          allElements.add(newGridItem);
        });
      } else {
        Stream<HashMap<String, Object>> expandedResult = allElements.stream().flatMap(existingItem -> {
          return Arrays.stream(hpValues).map(hpValue -> {
            HashMap<String, Object> clone = (HashMap<String, Object>) existingItem.clone();
            clone.put(hpKey, hpValue);
            return clone;
          });
        });
        ArrayList<HashMap<String, Object>> collect = expandedResult.collect(Collectors.toCollection(ArrayList::new));
        allElements.clear();
        allElements.addAll(collect);
      }
    });

    Collections.shuffle(allElements);
    ArrayList<Object[]> objects = allElements.stream().map(item -> item.values().toArray()).collect(Collectors.toCollection(ArrayList::new));

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();
    
    // Get clone of parameters
    TargetEncoderModel.TargetEncoderParameters commonModelParams = (TargetEncoderModel.TargetEncoderParameters) parameters.clone();
    // Fill model parameters
    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, hpGrid, simpleParametersBuilderFactory, hyperSpaceSearchCriteria);

    objects.stream().forEach(hps -> {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = walker.getModelParams(commonModelParams, hps);
      System.out.println( targetEncoderParameters._blending + ":" +  targetEncoderParameters._noise_level + ":" +  targetEncoderParameters._k + ":" +  targetEncoderParameters._f);

    });
    
  }


}
