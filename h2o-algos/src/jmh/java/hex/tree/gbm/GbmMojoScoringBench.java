package hex.tree.gbm;

import hex.genmodel.algos.tree.SharedTreeMojoModel;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * GBM MOJO micro-benchmark
 */
@Fork(1)
@Threads(1)
@State(Scope.Thread)
@Warmup(iterations = 3)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GbmMojoScoringBench {

  @Param({"1000", "100000"})
  private int rows;

  private SharedTreeMojoModel _mojo;
  private double[][] _data;

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(GbmMojoScoringBench.class.getSimpleName())
            .build();

    new Runner(opt).run();
  }

  @Setup(Level.Invocation)
  public void setup() throws IOException {
    _mojo = (SharedTreeMojoModel) ClasspathReaderBackend.loadMojo("prostate");
    _data = ProstateData.ROWS;
  }

  @Benchmark
  public double measureGbmScore0() throws Exception {
    double sum = 0;
    double[] pred = new double[3];
    for (int i = 0; i < rows; i++) {
      double[] row = _data[i % _data.length];
      pred[0] = 0.0; pred[1] = 0.0; pred[2] = 0.0;
      sum += _mojo.score0(row, pred)[1];
    }
    return sum;
  }

  @TearDown(Level.Invocation)
  public void tearDown() {
    _mojo = null;
    _data = null;
  }


}
