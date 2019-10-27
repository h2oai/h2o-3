package hex;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.grid.GridSearch;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.schemas.TargetEncoderV3;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.api.GridSearchHandler;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * PojoUtils benchmark
 */
@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 2)
@Measurement(iterations = 3)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class OldRGSBench {

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
    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, hpGrid, simpleParametersBuilderFactory, hyperSpaceSearchCriteria);

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
            .include(OldRGSBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();

    new Runner(opt).run();
  }
}
