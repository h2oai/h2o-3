package water.mojo.glm;

import hex.genmodel.CategoricalEncoding;
import hex.genmodel.ConverterFactoryProvidingModel;
import hex.genmodel.algos.glm.GlmMultinomialMojoModel;
import hex.genmodel.easy.CategoricalEncoder;
import hex.genmodel.easy.EasyPredictModelWrapper;
import hex.genmodel.easy.RowData;
import hex.genmodel.easy.RowToRawDataConverter;
import hex.genmodel.easy.error.VoidErrorConsumer;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static water.mojo.glm.GlmMojoBenchHelper.loadMojo;
import static water.mojo.glm.GlmMojoBenchHelper.readData;
import static water.util.FileUtils.getFile;

@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GlmMojoRowDataConverterBench {

    @Param({"1000", "10000"})
    private int rows;
    
    private RowToRawDataConverter _rowDataConverter;
    private RowData _row;
    private double[] _rawData;
    
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(GlmMojoRowDataConverterBench.class.getSimpleName())
                .addProfiler(StackProfiler.class)
                .build();

        new Runner(opt).run();
    }

    @Setup(Level.Invocation)
    public void setup() throws IOException {
        GlmMultinomialMojoModel _mojo = (GlmMultinomialMojoModel) loadMojo("mnist");
        
        File f = getFile("smalldata/flow_examples/mnist/test.csv.gz");
        int cols = 784;
        double[][] _data = new double[rows][];
        for (int i = 0; i < rows; i++) {
            _data[i] = new double[cols];
        }
        readData(f, cols, "C1", _data, _mojo);

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

        CategoricalEncoding categoricalEncoding = config.getUseExternalEncoding() ?
                CategoricalEncoding.AUTO : _mojo.getCategoricalEncoding();
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
