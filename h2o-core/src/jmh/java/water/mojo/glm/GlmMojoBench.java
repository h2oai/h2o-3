package water.mojo.glm;

import hex.genmodel.algos.glm.GlmMojoModel;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import static water.util.FileUtils.*;

import java.io.*;
import java.util.concurrent.TimeUnit;

import static water.mojo.glm.GlmMojoBenchHelper.*;

/**
 * GLM MOJO scoring benchmark
 */
@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GlmMojoBench {

  @Param({"1000", "10000"})
  private int rows;

  private GlmMojoModel mojo;
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
    File f = getFile("smalldata/airlines/allyears2k.zip");

    mojo = (GlmMojoModel) loadMojo("airlines");

    int cols = 31;
    data = new double[rows][];
    preds = new double[rows][];
    for (int i = 0; i < rows; i++) {
      data[i] = new double[cols];
      preds[i] = new double[3];
    }
    int[] mapping = new int[] {
      3, 6, -1, 5, -1, -1, -1, -1, 4, 0, -1, -1, -1, -1, -1, -1, 2, 1, 7, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1
    };
    readData(f, mapping, null, data, mojo);
  }


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(GlmMultinomialMojoBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();

    new Runner(opt).run();
  }

}
