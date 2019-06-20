package hex.psvm;

import hex.genmodel.algos.psvm.KernelParameters;
import hex.genmodel.algos.psvm.KernelType;
import hex.genmodel.algos.psvm.ScorerFactory;
import hex.genmodel.algos.psvm.SupportVectorScorer;
import hex.pca.JMHConfiguration;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.AutoBuffer;
import water.fvec.C8DVolatileChunkHelper;
import water.fvec.Chunk;

import java.io.ByteArrayOutputStream;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Fork(1)
@Threads(1)
@State(Scope.Thread)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Timeout(time = JMHConfiguration.TIMEOUT_MINUTES, timeUnit = TimeUnit.MINUTES)
public class SupportVectorScorerBench {

  @Param({"50", "500"})
  private int _num_svs;

  @Param({"50"})
  private int _nums;

  @Param({"100"})
  private int _rows;

  public SupportVectorScorer _scorer;
  public BulkSupportVectorScorer _bulkScorerRaw;
  public BulkSupportVectorScorer _bulkScorerParsed;

  public double[][] _data;
  public Chunk[] _chunks;

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(SupportVectorScorerBench.class.getSimpleName())
            .build();

    new Runner(opt).run();
  }

  @Setup(Level.Iteration)
  public void setup() {
    water.util.Log.setLogLevel("ERRR");

    Random r = new Random(8008);
    
    KernelParameters p = new KernelParameters();
    p._gamma = 0.1;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    AutoBuffer ab = new AutoBuffer(baos, false);
    SupportVector sv = new SupportVector();
    for (int i = 0; i < _num_svs; i++) {
      double[] row = new double[_nums];
      for (int j = 0; j < row.length; j++) {
        row[j] = r.nextDouble();
      }
      sv.fill(r.nextDouble(), row, new int[0]);
      sv.compress(ab);
    }
    ab.close();
    
    // get rid of the marker byte
    byte[] ab_compressed_svs = baos.toByteArray();
    byte[] compressed_svs = new byte[ab_compressed_svs.length - 1];
    System.arraycopy(ab_compressed_svs, 1, compressed_svs, 0, compressed_svs.length); 
    
    int estimatedSize = sv.estimateSize() * _num_svs;
    if (compressed_svs.length != estimatedSize) {
      throw new IllegalStateException(compressed_svs.length - 1+  " != " + sv.estimateSize());
    }
    
    _scorer = ScorerFactory.makeScorer(KernelType.gaussian, p, compressed_svs);
    _bulkScorerRaw = BulkScorerFactory.makeScorer(KernelType.gaussian, p, compressed_svs, _num_svs, true);
    _bulkScorerParsed = BulkScorerFactory.makeScorer(KernelType.gaussian, p, compressed_svs, _num_svs, false);

    _data = new double[_rows][];
    for (int i = 0; i < _rows; i++) {
      double[] row = new double[_nums];
      for (int j = 0; j < row.length; j++) {
        row[j] = r.nextDouble();
      }
      _data[i] = row;
    }

    _chunks = new Chunk[_nums];
    for (int i = 0; i < _nums; i++) {
      double[] colData = new double[_rows];
      for (int j = 0; j < _rows; j++) {
        colData[j] = _data[j][i];
      }
      _chunks[i] = C8DVolatileChunkHelper.makeVolatileChunk(colData);
    }
  }

  @Benchmark
  public double perRowScoring() {
    double result = 0;
    for (int i = 0; i < _rows; i++) {
      result += _scorer.score0(_data[i]);
    }
    return result;
  }

  @Benchmark
  public double bulkScoringRaw() {
    double result = 0;
    for (double d : _bulkScorerRaw.bulkScore0(_chunks)) {
      result += d;
    }
    return result;
  }

  @Benchmark
  public double bulkScoringParsed() {
    double result = 0;
    for (double d : _bulkScorerParsed.bulkScore0(_chunks)) {
      result += d;
    }
    return result;
  }

  @TearDown(Level.Iteration)
  public void tearDown() {
    _scorer = null;
  }

}
