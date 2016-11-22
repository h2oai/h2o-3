package water.parser;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.fvec.AppendableVec;

import java.util.concurrent.TimeUnit;

/**
 * Collection of benchmarks testing performance of FVecParseWriter.add?(..) functions acting on missing columns
 */
@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class FVecParseWriterMissingBench {

  @Param({"1000", "10000"})
  private int cols;
  @Param({"20", "100000"})
  private int rows;

  private static final BufferedString STR = new BufferedString("test-str");

  private FVecParseWriter _writer;

  @Benchmark // baseline, no missing columns added
  public void newLine() {
    _writer._errs = new ParseWriter.ParseErr[0];
    for (int r = 0; r < rows; r++) {
      _writer._col = 0; // force the writer to process the line
      _writer.newLine();
    }
    checkErrors(0);
  }

  @Benchmark
  public void addNumCols() {
    _writer._errs = new ParseWriter.ParseErr[0];
    for (int r = 0; r < rows; r++) {
      for (int col = 0; col < cols; col++)
        _writer.addNumCol(col, Math.E);
      _writer._col = 0; // force the writer to process the line
      _writer.newLine();
    }
    checkErrors(20);
  }

  @Benchmark
  public void addNaNCols() {
    _writer._errs = new ParseWriter.ParseErr[0];
    for (int r = 0; r < rows; r++) {
      for (int col = 0; col < cols; col++)
        _writer.addNumCol(col, Double.NaN);
      _writer._col = 0; // force the writer to process the line
      _writer.newLine();
    }
    checkErrors(20);
  }

  @Benchmark
  public void addEncNumCols() {
    _writer._errs = new ParseWriter.ParseErr[0];
    for (int r = 0; r < rows; r++) {
      for (int col = 0; col < cols; col++)
        _writer.addNumCol(col, 54321, 42);
      _writer._col = 0; // force the writer to process the line
      _writer.newLine();
    }
    checkErrors(20);
  }

  @Benchmark
  public void addStrCols() {
    _writer._errs = new ParseWriter.ParseErr[0];
    for (int r = 0; r < rows; r++) {
      for (int col = 0; col < cols; col++)
        _writer.addStrCol(col, STR);
      _writer._col = 0; // force the writer to process the line
      _writer.newLine();
    }
    checkErrors(20);
  }

  @Benchmark
  public void addInvalidCols() {
    _writer._errs = new ParseWriter.ParseErr[0];
    for (int r = 0; r < rows; r++) {
      for (int col = 0; col < cols; col++)
        _writer.addInvalidCol(col);
      _writer._col = 0; // force the writer to process the line
      _writer.newLine();
    }
    checkErrors(20);
  }

  private void checkErrors(int num) {
    if (_writer._errs.length != num) throw new IllegalStateException("Expected " + num + " errors");
  }

  @Setup
  public void setup() {
    _writer = new FVecParseWriter(null, -1, null, null, -1, new AppendableVec[0]);
    _writer._col = 0;
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(FVecParseWriterMissingBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
            .build();

    new Runner(opt).run();
  }

}
