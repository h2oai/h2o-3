package water;

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

import water.fvec.Chunk;
import water.fvec.NewChunk;

/**
 * Chunk access patterns benchmark
 */
@State(Scope.Thread)
//@Fork(value = 1, jvmArgsAppend = "-XX:+PrintCompilation")
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class ChunkBench {

  @Param({"1000", "10000"})
  private int cols;
  @Param({"1000", "100000"})
  private int rows;
  private Chunk[] chunks;
  private double[][] raw;

  @Benchmark
  public double rawArrayRead() {
    double sum = 0;
    for (int col = 0; col < cols; ++col) {
      for (int row = 0; row < rows; ++row) {
        sum += raw[col][row];
      }
    }
    return sum;
  }

  @Benchmark
  public double rowsColsRead() {
    double sum = 0;
    for (int row = 0; row < rows; ++row) {
      for (int col = 0; col < cols; ++col) {
        sum += chunks[col].atd(row);
      }
    }
    return sum;
  }

  @Benchmark
  public double colsRowsRead() {
    double sum = 0;
    for (int col = 0; col < cols; ++col) {
      for (int row = 0; row < rows; ++row) {
        sum += chunks[col].atd(row);
      }
    }
    return sum;
  }

  @Benchmark
  public double colsRowsReadWithTypeDispatch() {
    double sum = 0;
    for (int col = 0; col < cols; ++col) {
      sum += walkChunk(rows, chunks[col]);
    }
    return sum;
  }

  @Benchmark
  public double colsRowsWithBulkRead() {
    double sum = 0;
    // Preallocate array for storing unpacked chunk data
    double [] vals = new double[chunks[0]._len];
    for (int col = 0; col < cols; ++col) {
      sum += walkChunkBulk(rows, chunks[col], vals);
    }
    return sum;
  }

  @Benchmark
  public double colsRowsReadWithFinalChunk() {
    double sum = 0;
    for (int col = 0; col < cols; ++col) {
      final Chunk c = chunks[col];
      for (int row = 0; row < rows; ++row) {
        sum += c.atd(row);
      }
    }
    return sum;
  }

  private static double walkChunk(int rows, final Chunk c) {
    double sum =0;
    for (int row = 0; row < rows; ++row) {
      sum += c.atd(row);
    }
    return sum;
  }

  private static double walkChunkBulk(int rows, final Chunk c, double [] vals) {
    double sum = 0;
    c.getDoubles(vals, 0, c._len);
    for (int i = 0; i < rows; ++i)
      sum += vals[i];
    return sum;
  }

  @Setup
  public void setup() {
    raw = new double[cols][rows];
    for (int col = 0; col < cols; ++col) {
      for (int row = 0; row < rows; ++row) {
        raw[col][row] = get(col, row);
      }
    }
    chunks = new Chunk[cols];
    for (int col = 0; col < cols; ++col) {
      chunks[col] = new NewChunk(raw[col]).compress();
    }
  }

  private static double get(int j, int i) {
    switch (j % 4) { // do 4 chunk types
      case 0:
        return i % 200; //C1NChunk - 1 byte integer
      case 1:
        return i % 500; //C2Chunk - 2 byte integer
      case 2:
        return  i*Integer.MAX_VALUE;
      case 3:
        return i == 17 ? 1 : 0; //CX0Chunk - sparse
      default:
        throw H2O.unimpl();
    }
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(ChunkBench.class.getSimpleName())
        .addProfiler(StackProfiler.class)
//                    .addProfiler(GCProfiler.class)
        .build();

    new Runner(opt).run();
  }
}
