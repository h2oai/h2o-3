package hex.pca;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * PCA benchmark micro-benchmark based on hex.pca.PCATest.testImputeMissing() using dataset of Quasar data
 */
@Fork(value = 1, jvmArgsAppend = { "-Xmx16g", "-Dai.h2o.name=karel_PCABench"})
@Threads(1)
@State(Scope.Thread)
@Warmup(iterations = JMHConfiguration.WARM_UP_ITERATIONS)
@Measurement(iterations = JMHConfiguration.MEASUREMENT_ITERATIONS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = JMHConfiguration.TIMEOUT_MINUTES, timeUnit = TimeUnit.MINUTES)
public class PCAJMHScoring extends PCAJMH {
  
  @Param({"JAMA", "MTJ_SVD_DENSEMATRIX", "EVD_MTJ_DENSEMATRIX", "EVD_MTJ_SYMM"})
  private PCAImplementation PCAImplementation;
  private boolean isTrained;
  
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(PCAJMHScoring.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }
  
  @Setup(Level.Iteration)
  public void setup() {
  	super.setup();
    paramsQuasar._pca_implementation = PCAImplementation;
    isTrained = tryToTrain();
  }
  
  @TearDown(Level.Iteration)
  public void tearDown() {
  	super.tearDown();
  }
  
  @Benchmark
  public boolean measureQuasarScoring() throws Exception {
  	if (!isTrained) {
      throw new Exception("Model for PCAJMHScoring failed to be trained!");
    }
    if (!tryToScore()) {
      throw new Exception("Model for PCAJMHScoring failed to be scored!");
    }
    return true;
  }
  
}
