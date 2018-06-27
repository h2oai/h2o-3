package water;

import org.junit.Assert;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.fvec.*;

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
  // for constant chunks
  private double[] rawDoubleConstants;
  private double[] rawIntegerConstants;
  private double[] rawLongConstants;
  private double[] rawFloatConstants;
  private double[] rawNAConstants;
  // for sparse chunks
  private double[] rawDoubleSparse;
  private double[] rawIntegerSparse;
  private double[] rawLongSparse;
  private double[] rawFloatSparse;
  private double[] rawNASparse;

  @Benchmark
  public void writeIntegers() {
    Chunk chunks = new NewChunk(rawInt).compress();
    Assert.assertTrue(chunks instanceof C2SChunk);
  }

  @Benchmark
  public void writeFloats() {
    Chunk chunks = new NewChunk(rawFloat).compress();
    Assert.assertTrue(chunks instanceof C8DChunk);
  }

  @Benchmark
  public void writeDoubles() {
    Chunk chunks = new NewChunk(rawDouble).compress();
    Assert.assertTrue(chunks instanceof C8DChunk);
  }

  @Benchmark
  public void writeLongs() {
    Chunk chunks = new NewChunk(rawLong).compress();
    Assert.assertTrue(chunks instanceof C2SChunk);
  }

  @Benchmark
  public void writeIntegersConstants() {
    Chunk chunks = new NewChunk(rawIntegerConstants).compress();
    Assert.assertTrue(chunks instanceof C0LChunk);
  }

  @Benchmark
  public void writeFloatsConstants() {
    Chunk chunks = new NewChunk(rawFloatConstants).compress();
    Assert.assertTrue(chunks instanceof C0DChunk);
  }

  @Benchmark
  public void writeDoublesConstants() {
    Chunk chunks = new NewChunk(rawDoubleConstants).compress();
    Assert.assertTrue(chunks instanceof C0DChunk);
  }

  @Benchmark
  public void writeLongsConstants() {
    Chunk chunks = new NewChunk(rawLongConstants).compress();
    Assert.assertTrue(chunks instanceof C0LChunk);
  }

  @Benchmark
  public void writeNaNConstants() {
    Chunk chunks = new NewChunk(rawNAConstants).compress();
    Assert.assertTrue(chunks instanceof C0DChunk);
  }

  @Benchmark
  public void writeIntegersSparse() {
    Chunk chunks = new NewChunk(rawIntegerSparse).compress();
    Assert.assertTrue(chunks instanceof CXIChunk);
  }

  @Benchmark
  public void writeFloatsSparse() {
    Chunk chunks = new NewChunk(rawFloatConstants).compress();
    Assert.assertTrue(chunks instanceof CXFChunk);
  }

  @Benchmark
  public void writeDoublesSparse() {
    Chunk chunks = new NewChunk(rawDoubleSparse).compress();
    Assert.assertTrue(chunks instanceof CXFChunk);
  }

  @Benchmark
  public void writeLongsSparse() {
    Chunk chunks = new NewChunk(rawLongSparse).compress();
    Assert.assertTrue(chunks instanceof CXIChunk);
  }

  @Setup
  public void setup() {
    rawFloat = new double[rows]; // generate data
    rawInt = new double[rows];
    rawLong = new double[rows];
    rawDouble = new double[rows];
    rawDoubleConstants = new double[rows];
    rawIntegerConstants = new double[rows];
    rawLongConstants = new double[rows];
    rawFloatConstants = new double[rows];
    rawNAConstants = new double[rows];
    // for sparse chunks
    rawDoubleSparse = new double[rows];
    rawIntegerSparse = new double[rows];
    rawLongSparse = new double[rows];
    rawFloatSparse = new double[rows];
    Long lConstants = (long) Integer.MAX_VALUE+100;
    for (int row = 0; row < rows; ++row) {
      rawFloat[row] = 1.1+row%100;
      rawInt[row] = row % 1000;
      rawLong[row] = Integer.MAX_VALUE+row;
      rawDouble[row] = Math.PI+row;
      rawDoubleConstants[row] = Math.PI;
      rawFloatConstants[row] = 1.1;
      rawIntegerConstants[row] = 1000;
      rawLongConstants[row] = lConstants;
      rawNAConstants[row] = Double.NaN;
    }

    rawDoubleSparse[17] = Math.PI;
    rawIntegerSparse[17] = 1;
    rawLongSparse[17] = lConstants;
    rawFloatSparse[17] = 1.1;
  }


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(ChunkBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();

    new Runner(opt).run();
  }
}