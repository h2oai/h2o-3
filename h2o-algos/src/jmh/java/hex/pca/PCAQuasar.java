package hex.pca;

import hex.DataInfo;
import hex.svd.SVDImplementation;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.util.FrameUtils;

import java.util.concurrent.TimeUnit;

import static hex.pca.JMHConfiguration.logLevel;
import static hex.pca.PCAModel.PCAParameters;
import static hex.pca.PCAModel.PCAParameters.Method.GramSVD;
import static water.TestUtil.parse_test_file;
import static water.TestUtil.stall_till_cloudsize;

/**
 * PCA benchmark micro-benchmark based on hex.pca.PCATest.testImputeMissing() using dataset of Quasar data
 */
@Fork(1)
@Threads(1)
@State(Scope.Thread)
@Warmup(iterations = JMHConfiguration.WARM_UP_ITERATIONS)
@Measurement(iterations = JMHConfiguration.MEASUREMENT_ITERATIONS)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PCAQuasar {

  @Param({"JAMA", "MTJ", "EVD_MTJ_DENSEMATRIX", "EVD_MTJ_SYMM"})
  private SVDImplementation svdImplementation;

  private PCAParameters paramsQuasar;
  private PCAModel pcaModel;
  private Frame trainingFrame;

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(PCAQuasar.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }

  private boolean tryToTrain() {
    try {
      pcaModel = new PCA(paramsQuasar).trainModel().get();
    } catch (Exception exception) {
      return false;
    }
    return true;
  }

  private boolean tryToScore() {
    try {
      pcaModel.score(trainingFrame);
    } catch (Exception e) {
      return false;
    }
    return true;
  }

  @Setup(Level.Iteration)
  public void setup() {
    water.util.Log.setLogLevel(logLevel);
    stall_till_cloudsize(1);

    trainingFrame = null;
    double missing_fraction = 0.75;
    long seed = 12345;

    try {
      /* NOTE get the data this way
       * 1) ./gradlew syncSmalldata
       * 2) unzip SDSS_quasar.txt.zip
       */
      trainingFrame = parse_test_file(Key.make("quasar.hex"), "smalldata/pca_test/SDSS_quasar.txt");
      // Add missing values to the training data
      Frame frame = new Frame(Key.<Frame>make(), trainingFrame.names(), trainingFrame.vecs());
      DKV.put(frame._key, frame); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
      FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frame._key, seed, missing_fraction);
      j.execImpl().get(); // MissingInserter is non-blocking, must block here explicitly
      DKV.remove(frame._key); // Delete the frame header (not the data)

      paramsQuasar = new PCAParameters();
      paramsQuasar._train = trainingFrame._key;
      paramsQuasar._k = 4;
      paramsQuasar._transform = DataInfo.TransformType.NONE;
      paramsQuasar._pca_method = GramSVD;
      paramsQuasar.setSvdImplementation(svdImplementation);
      paramsQuasar._impute_missing = true;   // Don't skip rows with NA entries, but impute using mean of column
      paramsQuasar._seed = seed;
    } catch (RuntimeException e) {
      if (trainingFrame != null) {
        trainingFrame.delete();
      }
      e.printStackTrace();
      throw e;
    }
  }

  @Benchmark
  public boolean measureQuasarTraining() throws Exception {
    if (!tryToTrain()) {
      throw new Exception("Model for PCAQuasar failed to be trained!");
    }
    return true;
  }

  @Benchmark
  public boolean measureQuasarScoring() throws Exception {
    if (!tryToScore()) {
      throw new Exception("Model for PCAQuasar failed to be scored!");
    }
    return true;
  }

  @TearDown(Level.Iteration)
  public void tearDown() {
    if (pcaModel != null) {
      pcaModel.remove();
    }
    if (trainingFrame != null) {
      trainingFrame.delete();
    }
  }
}
