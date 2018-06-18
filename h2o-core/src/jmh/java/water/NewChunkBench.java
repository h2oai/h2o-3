package water;

import org.junit.Assert;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.fvec.C0DChunk;
import water.fvec.C0LChunk;
import water.fvec.Chunk;
import water.fvec.NewChunk;

import java.util.concurrent.TimeUnit;

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

public class NewChunkBench {

  @Param({"10000", "1000000"})
  private int rows;
  private double[] rawDouble;
  private double[] rawFloat;
  private double[] rawLong;
  private double[] rawInt;

  @Benchmark
  public void writeIntegers() {
    Chunk chunks = new NewChunk(rawInt).compress();
    Assert.assertTrue(chunks instanceof C0LChunk);
  }

  @Benchmark
  public void writeFloats() {
    Chunk chunks = new NewChunk(rawFloat).compress();
    Assert.assertTrue(chunks instanceof C0DChunk);
  }

  @Benchmark
  public void writeDoubles() {
    Chunk chunks = new NewChunk(rawDouble).compress();
    Assert.assertTrue(chunks instanceof C0DChunk);
  }

  @Benchmark
  public void writeLongs() {
    Chunk chunks = new NewChunk(rawLong).compress();
    Assert.assertTrue(chunks instanceof C0LChunk);
  }

  @Setup
  public void setup() {
    rawFloat = new double[rows]; // generate data
    rawInt = new double[rows];
    rawLong = new double[rows];
    rawDouble = new double[rows];

    for (int row = 0; row < rows; ++row) {
      rawFloat[row] = 1.1+row%100;
      rawInt[row] = row % 1000;
      rawLong[row] = Integer.MAX_VALUE+row;
      rawDouble[row] = Math.PI+row;
    }
  }


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(ChunkBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();

    new Runner(opt).run();
  }
}