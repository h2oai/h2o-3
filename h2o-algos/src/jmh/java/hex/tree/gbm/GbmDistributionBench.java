package hex.tree.gbm;

import hex.genmodel.utils.DistributionFamily;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.StackProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.Key;
import water.TestUtil;
import water.fvec.Frame;

import java.util.concurrent.TimeUnit;

import static water.TestUtil.parseTestFile;
import static water.TestUtil.stall_till_cloudsize;

@State(Scope.Thread)
@Fork(value = 1, jvmArgsAppend = "-Xmx12g")
@Warmup(iterations = 1)
@Measurement(iterations = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class GbmDistributionBench {
    
    private GBMModel.GBMParameters params;
    private Frame fr;
    private GBM job;
    
    @Param({"gaussian", "poisson", "laplace", "poisson"})
    private DistributionFamily distribution;
    

    @Setup
    public void setup() {
        water.util.Log.setLogLevel("ERR");
        stall_till_cloudsize(1);
        fr = parseTestFile(Key.make("gdata"), "smalldata/logreg/prostate_train.csv");
        params = new GBMModel.GBMParameters();
        params._train = fr._key;
        params._distribution = distribution;
        params._response_column = "CAPSULE";
        params._ntrees = 5;
        params._max_depth = 3;
        params._nbins = 50;
        params._learn_rate = .2f;
        params._score_each_iteration = true;
        params._seed = 42;
    }
    
    @Benchmark
    public void trainGbmModel(){
        job = new GBM(params);
        job.trainModel().get();
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(GbmDistributionBench.class.getSimpleName())
                .addProfiler(StackProfiler.class)
                .build();
        new Runner(opt).run();
    }

    @TearDown(Level.Invocation)
    public void tearDown() {
        job = null;
    }
}
