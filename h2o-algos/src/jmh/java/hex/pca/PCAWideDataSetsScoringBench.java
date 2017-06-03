package hex.pca;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

import static water.TestUtil.stall_till_cloudsize;

/**
 * PCA benchmark
 */
@Fork(1)
@Threads(1)
@State(Scope.Thread)
@Warmup(iterations = 3)
@Measurement(iterations = 10)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PCAWideDataSetsScoringBench {
	
	private PCAWideDataSetsBenchModel pcaWideDataSetsBench;
	@Param({"1", "2", "3", "4", "5", "6"})
	private int dataSetCase;
	
	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
			.include(PCAWideDataSetsScoringBench.class.getSimpleName())
			.build();
		
		new Runner(opt).run();
	}
	
	@Setup(Level.Invocation)
	public void setup() {
		stall_till_cloudsize(1);
		
		pcaWideDataSetsBench = new PCAWideDataSetsBenchModel(dataSetCase);
		// train model to prepare for score()
		pcaWideDataSetsBench.train();
	}
	
	@Benchmark
	public boolean measureWideDataSetsBenchScoringCase() throws Exception {
		if (!pcaWideDataSetsBench.score()) {
			throw new Exception("Model for PCAWideDataSetsBench failed to be scored!");
		}
		return true;
	}
	
	@TearDown(Level.Invocation)
	public void tearDown() {
		pcaWideDataSetsBench.tearDown();
	}
	
}
