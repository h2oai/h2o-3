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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * PojoUtils benchmark
 */
@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class NewRGSBench {


  @Benchmark
  public void traverseWholeGridNew() {
    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("_blending", new Boolean[]{true, false});
    hpGrid.put("_noise_level", new Double[]{0.0, 0.01, 0.1, 0.2, 0.3, 0.4});
    hpGrid.put("_k", new Double[]{1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0});
    hpGrid.put("_f", new Double[]{5.0, 10.0, 20.0, 30.0, 50.0, 100.0});

    String[] hpNames = hpGrid.keySet().toArray(new String[0]);

    ArrayList<HashMap<String, Object>> allElements = new ArrayList<>();

    hpGrid.forEach((hpKey, hpValues) -> {
      if(allElements.isEmpty()) {
        Arrays.stream(hpValues).forEach(hpValue -> {
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
    
    ArrayList<Object[]> objects = allElements.stream().map(item -> Arrays.stream(hpNames).map(item::get).toArray()).collect(Collectors.toCollection(ArrayList::new));

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    // Get clone of parameters
    TargetEncoderModel.TargetEncoderParameters commonModelParams = (TargetEncoderModel.TargetEncoderParameters) parameters.clone();
    // Fill model parameters
    GridSearch.SimpleParametersBuilderFactory simpleParametersBuilderFactory = new GridSearch.SimpleParametersBuilderFactory();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, hpGrid, simpleParametersBuilderFactory, hyperSpaceSearchCriteria);

    objects.stream().forEach(hps -> {
      TargetEncoderModel.TargetEncoderParameters targetEncoderParameters = walker.getModelParams(commonModelParams, hps);
    });
  }

  @Setup
  public void setup() {
    
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(NewRGSBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .addProfiler(GCProfiler.class)
            .build();

    new Runner(opt).run();
  }
}
