/*package hex.pca;

import hex.DataInfo;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import water.DKV;
import water.Key;
import water.fvec.Frame;
import water.util.FrameUtils;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import static hex.pca.PCAModel.PCAParameters;
import static hex.pca.PCAModel.PCAParameters.Method.GramSVD;
import static water.TestUtil.parse_test_file;
import static water.TestUtil.stall_till_cloudsize;

*//**
 * PCA benchmark
 * Note: Include necessary CSV files to h2o-3/h2o-algos/src/jmh/resources
 * TODO: create benchmarks from the remaining tests in {@link hex.pca.PCATest} and {@link hex.pca.PCAWideDataSetsTests}
 *//*
@State(Scope.Benchmark)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PCABenchSuite {

  private MLBench pcaImputeMissingBench;
  private PCAWideDataSetsBench pcaWideDataSetsBench;
  @Param({"1", "2", "3", "4", "5", "6"})
  private int dataSetCase;

  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
        .include(PCABenchSuite.class.getSimpleName())
        .build();

    new Runner(opt).run();
  }

  @Setup
  public void setup() {
    stall_till_cloudsize(1);

    pcaImputeMissingBench = new PCAImputeMissingBench();
    pcaWideDataSetsBench = new PCAWideDataSetsBench(dataSetCase);
  }

  @Benchmark
  public boolean measureImputeMissingTraining() throws Exception {
    if (!pcaImputeMissingBench.train()) {
      throw new Exception("Model for PCAImputeMissing failed to be trained!");
    }
    return true;
  }

  @Benchmark
  public boolean measureImputeMissingScoring() throws Exception {
    if (!pcaImputeMissingBench.score()) {
      throw new Exception("Model for PCAImputeMissing failed to be scored!");
    }
    return true;
  }

  @Benchmark
  public boolean measureWideDataSetsBenchTrainingCase() throws Exception {
    if (!pcaWideDataSetsBench.train()) {
      throw new Exception("Model for PCAWideDataSetsBench failed to be trained!");
    }
    return true;
  }

  @Benchmark
  public boolean measureWideDataSetsBenchScoringCase() throws Exception {
    if (!pcaWideDataSetsBench.score()) {
      throw new Exception("Model for PCAWideDataSetsBench failed to be scored!");
    }
    return true;
  }

  @TearDown
  public void tearDown() {
    pcaImputeMissingBench.tearDown();
    pcaWideDataSetsBench.tearDown();
  }

  *//**
   * interface for benchmarks of machine learning algorithms
   *//*
  interface MLBench {

    void setup();

    boolean train();

    boolean score();

    void tearDown();

  }

  *//**
   * micro-benchmark based on hex.pca.PCATest.testImputeMissing()
   *//*
  public class PCAImputeMissingBench implements MLBench {

    private PCAParameters paramsImputeMissing;
    private PCAModel pcaModel;
    private Frame trainingFrame;

    public PCAImputeMissingBench() {
      setup();
    }

    @Override
    public void setup() {
      trainingFrame = null;
      double missing_fraction = 0.75;
      long seed = 12345;

      try {
        trainingFrame = parse_test_file(Key.make("arrests.hex"), "smalldata/pca_test/USArrests.csv");
        // Add missing values to the training data
        Frame frtmp = new Frame(Key.<Frame>make(), trainingFrame.names(), trainingFrame.vecs());
        DKV.put(frtmp._key, frtmp); // Need to put the frame (to be modified) into DKV for MissingInserter to pick up
        FrameUtils.MissingInserter j = new FrameUtils.MissingInserter(frtmp._key, seed, missing_fraction);
        j.execImpl().get(); // MissingInserter is non-blocking, must block here explicitly
        DKV.remove(frtmp._key); // Delete the frame header (not the data)

        paramsImputeMissing = new PCAParameters();
        paramsImputeMissing._train = trainingFrame._key;
        paramsImputeMissing._k = 4;
        paramsImputeMissing._transform = DataInfo.TransformType.NONE;
        paramsImputeMissing._pca_method = GramSVD;
        paramsImputeMissing._impute_missing = true;   // Don't skip rows with NA entries, but impute using mean of column
        paramsImputeMissing._seed = seed;
      } catch (RuntimeException e) {
        if (trainingFrame != null) {
          trainingFrame.delete();
        }
        throw e;
      }
    }

    @Override
    public boolean train() {
      try {
        pcaModel = new PCA(paramsImputeMissing).trainModel().get();
      } catch (Exception exception) {
        return false;
      }
      return true;
    }

    @Override
    public boolean score() {
      try {
        pcaModel.score(trainingFrame);
      } catch (Exception e) {
        return false;
      }
      return true;
    }

    public void tearDown() {
    }

  }

  *//**
   * micro-benchmark based on hex.pca.PCAWideDataSetsTest
   *
   * This benchmark will measure the PCA method GramSVD with wide datasets.  It will first build a model
   * using GramSVD under normal setting (_wideDataset is set to false).  Next, it builds a GramSVD model with
   * _wideDataSet set to true.
   *//*
  public class PCAWideDataSetsBench implements MLBench {
    private static final int numberOfModels = 6;
    private Frame trainingFrame = null;
    private PCA pca = null;
    private int dataSetCase;
    private PCAModel pcaModel;
    private Frame pcaScore;

    PCAWideDataSetsBench(int dataSetCase) {
      setDataSetCase(dataSetCase);
      setup();
    }

    void setDataSetCase(int customDataSetCase) {
      if (customDataSetCase <= 0 || customDataSetCase > numberOfModels) {
        throw new IllegalArgumentException("Illegal data set case!");
      } else {
        this.dataSetCase = customDataSetCase;
      }
    }

    @Override
    public void setup() {
      final String _smallDataSet = "smalldata/pca_test/decathlon.csv";
      final String _prostateDataSet = "smalldata/prostate/prostate_cat.csv";
      final DataInfo.TransformType[] _transformTypes = {DataInfo.TransformType.NONE,
          DataInfo.TransformType.STANDARDIZE, DataInfo.TransformType.DEMEAN, DataInfo.TransformType.DESCALE};
      Random _rand = new Random();

      *//*
       *  Six cases are measured:
       * case 1. we test with a small dataset with all numerical data columns and make sure it works.
       * case 2. we add NA rows to the	small dataset with all numerical data columns.
       * case 3. test with the same small dataset while preserving the categorical columns;
       * case 4. test with the same small dataset with categorical columns and add NA rows;
       * case 5. test with prostate dataset;
       * case 6. test with prostate dataset with NA rows added.
       *//*
      switch (dataSetCase) {
        case 1:
          pca = preparePCAModel(_smallDataSet, false, true,
              _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 1
          break;
        case 2:
          pca = preparePCAModel(_smallDataSet, true, true,
              _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 2
          break;
        case 3:
          pca = preparePCAModel(_smallDataSet, false, false,
              _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 3
          break;
        case 4:
          pca = preparePCAModel(_smallDataSet, true, false,
              _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 4
          break;
        case 5:
          pca = preparePCAModel(_prostateDataSet, false, false,
              _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 5
          break;
        case 6:
          pca = preparePCAModel(_prostateDataSet, true, false,
              _transformTypes[_rand.nextInt(_transformTypes.length)]);  // case 6
          break;
      }

      // train model to prepare for score()
      pcaModel = pca.trainModel().get();
      water.Scope.track_generic(pcaModel);
    }

    private PCA preparePCAModel(String datafile, boolean addNAs, boolean removeColumns,
                                DataInfo.TransformType transformType) {
      water.Scope.enter();
      trainingFrame = parse_test_file(Key.make(datafile), datafile);
      water.Scope.track(trainingFrame);
      if (removeColumns) {
        trainingFrame.remove(12).remove();    // remove categorical columns
        trainingFrame.remove(11).remove();
        trainingFrame.remove(10).remove();
      }
      if (addNAs) {
        trainingFrame.vec(0).setNA(0);          // set NAs
        trainingFrame.vec(3).setNA(10);
        trainingFrame.vec(5).setNA(20);
      }
      DKV.put(trainingFrame);

      PCAParameters parameters = new PCAParameters();
      parameters._train = trainingFrame._key;
      parameters._k = 3;
      parameters._transform = transformType;
      parameters._use_all_factor_levels = true;
      parameters._pca_method = GramSVD;
      parameters._impute_missing = false;
      parameters._seed = 12345;

      PCA pcaParametersWide = new PCA(parameters);
      pcaParametersWide.setWideDataset(true);  // force to treat dataset as wide even though it is not.
      return pcaParametersWide;
    }

    @Override
    public void tearDown() {
      water.Scope.exit();
    }

    @Override
    public boolean train() {
      try {
        pca.trainModel().get();
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
      return true;
    }

    @Override
    public boolean score() {
      try {
        pcaScore = pcaModel.score(trainingFrame);
        water.Scope.track(pcaScore);
      } catch (Exception e) {
        e.printStackTrace();
        return false;
      }
      return true;
    }

  }
}*/
