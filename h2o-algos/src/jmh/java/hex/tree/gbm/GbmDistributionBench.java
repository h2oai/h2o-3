package hex.tree.gbm;

import hex.genmodel.utils.DistributionFamily;
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
import water.Key;
import water.fvec.Frame;

import java.util.concurrent.TimeUnit;

import static water.TestUtil.parse_test_file;

@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GbmDistributionBench {
    
    private GBM job;
    @Param({"gaussian", "poisson", "laplace", "poisson"})
    private DistributionFamily distribution;

    @Setup
    public void setup() {
        Frame fr = parse_test_file(Key.make("gdata"), "smalldata/gbm_test/Mfgdata_gaussian_GBM_testing.csv");
        GBMModel.GBMParameters parms = new GBMModel.GBMParameters();
        parms._train = fr._key;
        parms._distribution = distribution;
        parms._response_column = "age";
        parms._ntrees = 5;
        parms._max_depth = 4;
        parms._min_rows = 1;
        parms._nbins = 50;
        parms._learn_rate = .2f;
        parms._score_each_iteration = true;
        parms._seed = 42;
        job = new GBM(parms);
    }
    
    @Benchmark
    public void trainGbmModel(){
        GBMModel gbm = job.trainModel().get();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(GbmDistributionBench.class.getSimpleName())
                .addProfiler(StackProfiler.class)
                .build();
        new Runner(opt).run();
    }
}
