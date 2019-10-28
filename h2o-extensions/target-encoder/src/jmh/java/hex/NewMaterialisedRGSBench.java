package hex;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * PojoUtils benchmark
 */
@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class NewMaterialisedRGSBench {

  /**
   * This benchmark is basically the same as NewRGSBench as it is testing same idea but integrated into BaseWalker concept.
   * HyperSpaceWalker.MaterializedRandomWalker was added along existing walkers to make it possible to compare performance.
   */
  @Benchmark
  public void traverseWholeGrid() throws InterruptedException{
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1, 0.2, 0.3, 0.4});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0});
    hpGrid.put("_f", new Double[]{5.0, 10.0, 20.0, 30.0, 50.0, 100.0});

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
      if ((double)gridItem.get("k") == 3.0 && (double)gridItem.get("f") == 5.0) {
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
                    hyperSpaceSearchCriteria);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();
    while (iterator.hasNext(null)) {
        TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = iterator.nextModelParameters(null);
    }
  }

  @Setup
  public void setup() {
    
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(NewMaterialisedRGSBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .addProfiler(GCProfiler.class)
            .build();

    new Runner(opt).run();
  }
}
