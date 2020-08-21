package water.jdbc;

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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;

import water.fvec.NewChunk;
import water.parser.BufferedString;

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
public class SQLManagerBench {

  @Param({"100", "10000"})
  private int rows;
  @Param({"true", "false"})
  private boolean useRef;
  
  private NewChunk nc;
  private Double[] doubles;
  private String[] strings;

  @Setup
  public void setup() {
    nc = new NewChunk(new double[0]) {
      @Override
      public void addNum(double d) {
        // do nothing
      }

      @Override
      public void addStr(Object str) {
        // do nothing
      }
    };
    doubles = new Double[rows];
    strings = new String[rows];
    for (int i = 0; i < rows; i++) {
      doubles[i] = i / (double) rows;
      strings[i] = doubles[i].toString();
    }
  }

  @Benchmark
  public double writeItem_double() {
    Double sum = 0.0;
    for (Double d : doubles) {
      writeItem(d, nc);
      sum += d;
    }
    return sum;
  }

  @Benchmark
  public String writeItem_string() {
    String result = null;
    for (String s : strings) {
      writeItem(s, nc);
      result = s;
    }
    return result;
  }

  @Benchmark
  public double writeItem_mix() {
    Double sum = 0.0;
    for (int i = 0; i < doubles.length; i++) {
      writeItem(doubles[i], nc);
      writeItem(strings[i], nc);
      sum += doubles[i];
    }
    return sum;
  }

  private void writeItem(Object res, NewChunk nc) {
    if (useRef) {
      writeItem_ref(res, nc);
    } else {
      SQLManager.SqlTableToH2OFrame.writeItem(res, nc);
    }
  }
  
  // old, "reference" implementation
  private static void writeItem_ref(Object res, NewChunk nc) {
    if (res == null)
      nc.addNA();
    else {
      switch (res.getClass().getSimpleName()) {
        case "Double":
          nc.addNum((double) res);
          break;
        case "Integer":
          nc.addNum((long) (int) res, 0);
          break;
        case "Long":
          nc.addNum((long) res, 0);
          break;
        case "Float":
          nc.addNum((double) (float) res);
          break;
        case "Short":
          nc.addNum((long) (short) res, 0);
          break;
        case "Byte":
          nc.addNum((long) (byte) res, 0);
          break;
        case "BigDecimal":
          nc.addNum(((BigDecimal) res).doubleValue());
          break;
        case "Boolean":
          nc.addNum(((boolean) res ? 1 : 0), 0);
          break;
        case "String":
          nc.addStr(new BufferedString((String) res));
          break;
        case "Date":
          nc.addNum(((Date) res).getTime(), 0);
          break;
        case "Time":
          nc.addNum(((Time) res).getTime(), 0);
          break;
        case "Timestamp":
          nc.addNum(((Timestamp) res).getTime(), 0);
          break;
        default:
          nc.addNA();
      }
    }
  }

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(SQLManagerBench.class.getSimpleName())
            .addProfiler(StackProfiler.class)
//                    .addProfiler(GCProfiler.class)
            .build();

    new Runner(opt).run();
  }
}
