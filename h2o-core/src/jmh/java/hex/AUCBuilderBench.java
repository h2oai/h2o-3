package hex;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * AUCBuilder benchmark
 */
@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class AUCBuilderBench {

  @Param({"40", "400"})
  private int nBins;
  @Param({"1000", "10000"})
  private int nObs;

  private AUC2.AUCBuilder bldr;

  @Benchmark
  public double perRow() {
    for (int i = 0; i < nObs/2; i++) {
      bldr.perRow(i / (double) nBins, 1, 1.0);
      bldr.perRow((nBins - i) / (double) nBins, 1, 1.0);
    }
    return bldr._ths[0] + bldr._ths[nBins - 1];
  }

  @Setup
  public void setup() {
    bldr = new AUC2.AUCBuilder(nBins);
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(AUCBuilderBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();

    new Runner(opt).run();
  }
}
