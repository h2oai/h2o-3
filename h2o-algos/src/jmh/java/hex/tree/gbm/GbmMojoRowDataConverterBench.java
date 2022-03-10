package hex.tree.gbm;

import hex.genmodel.ICategoricalEncoding;
import hex.genmodel.ConverterFactoryProvidingModel;
import hex.genmodel.algos.tree.SharedTreeMojoModel;
import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.RowToRawDataConverter;
import hex.genmodel.easy.error.VoidErrorConsumer;
import hex.pca.JMHConfiguration;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.util.Map;
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
public class GbmMojoRowDataConverterBench {
  
  private RowToRawDataConverter _rowDataConverter;
  private RowData _row;
  private double[] _rawData;


  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
            .include(GbmMojoRowDataConverterBench.class.getSimpleName())
            .build();

    new Runner(opt).run();
  }
  
  @Setup(Level.Invocation)
  public void setup() throws IOException {
    SharedTreeMojoModel _mojo = (SharedTreeMojoModel) ClasspathReaderBackend.loadMojo("prostate");
    double[][] _data = ProstateData.ROWS;

    Random r = new Random(8008);
    int min = 0, max = _data[0].length;
    double[] rndDataRow = _data[r.nextInt(max - min) + min];
    
    _row = new RowData();
    _rawData = new double[rndDataRow.length];
    for (int j = 0; j < rndDataRow.length; j++) {
      _row.put(String.valueOf(j), String.valueOf(rndDataRow[j]));
    }

    EasyPredictModelWrapper.Config config = new EasyPredictModelWrapper.Config()
            .setModel(_mojo)
            .setConvertUnknownCategoricalLevelsToNa(true)
            .setConvertInvalidNumbersToNa(true);

    EasyPredictModelWrapper.ErrorConsumer errorConsumer = config.getErrorConsumer() == null ? new VoidErrorConsumer() : config.getErrorConsumer();

    ICategoricalEncoding categoricalEncoding = config.getUseExternalEncoding() ?
            ICategoricalEncoding.AUTO : _mojo.getCategoricalEncoding();
    Map<String, Integer> columnMapping = categoricalEncoding.createColumnMapping(_mojo);
    Map<Integer, CategoricalEncoder> domainMap = categoricalEncoding.createCategoricalEncoders(_mojo, columnMapping);

    if (_mojo instanceof ConverterFactoryProvidingModel) {
      _rowDataConverter = ((ConverterFactoryProvidingModel)  _mojo).makeConverterFactory(columnMapping, domainMap, errorConsumer, config);
    } else {
      _rowDataConverter = new RowToRawDataConverter(_mojo, columnMapping, domainMap, errorConsumer, config);
    }
  }

  @Benchmark
  public void measureConvert() throws Exception {
    _rowDataConverter.convert(_row, _rawData);
  }

  @TearDown(Level.Invocation)
  public void tearDown() {
    _rowDataConverter = null;
    _row = null;
    _rawData = null;
  }
}
