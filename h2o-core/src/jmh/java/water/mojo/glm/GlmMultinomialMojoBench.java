package water.mojo.glm;

import hex.genmodel.algos.glm.GlmMultinomialMojoModel;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.util.FileUtils;

import java.io.*;
import java.util.concurrent.TimeUnit;

import static water.mojo.glm.GlmMojoBenchHelper.*;
import static water.util.FileUtils.*;

/**
 * GLM MOJO scoring benchmark (multinomial)
 */
@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GlmMultinomialMojoBench {

  @Param({"1000", "10000"})
  private int rows;

  private GlmMultinomialMojoModel mojo;
  private double[][] data;
  private double[][] preds;

  @Benchmark
  public double[][] score0_nRows() {
    for (int i = 0; i < data.length; i++)
      preds[i] = mojo.score0(data[i], preds[i]);
    return preds;
  }

  @Setup
  public void setup() throws IOException {
    File f = getFile("smalldata/flow_examples/mnist/test.csv.gz");

    mojo = (GlmMultinomialMojoModel) loadMojo("mnist");

    int cols = 784;
    data = new double[rows][];
    preds = new double[rows][];
    for (int i = 0; i < rows; i++) {
      data[i] = new double[cols];
      preds[i] = new double[11];
    }
    readData(f, cols, "C1", data, mojo);
  }


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(GlmMultinomialMojoBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();

    new Runner(opt).run();
  }

}
