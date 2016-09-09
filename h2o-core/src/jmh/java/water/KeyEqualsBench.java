package water;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * Micro-performance of Key methods.
 */
@State(Scope.Thread)
@Fork(1)  // ? I do not need more threads since i do not update shared state?
@Warmup(iterations = 2)
@Measurement(iterations = 2)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class KeyEqualsBench {

  private Key k1 = Key.make();
  private Key k2 = Key.make();

  @Benchmark
  public boolean keyEquals() {
    return k1.equals(k2);
  }

  @Benchmark
  public int keyHash() {
    return k1.hashCode();
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(KeyEqualsBench.class.getSimpleName())
        .addProfiler(StackProfiler.class)
//                    .addProfiler(GCProfiler.class)
        .build();

    new Runner(opt).run();
  }
}
