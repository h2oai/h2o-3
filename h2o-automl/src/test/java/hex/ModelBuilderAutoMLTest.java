package hex;

import ai.h2o.automl.Algo;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import org.junit.BeforeClass;
import org.junit.Test;
import water.*;
import water.fvec.Frame;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class ModelBuilderAutoMLTest extends TestUtil {

  @BeforeClass()
  public static void setup() {
    stall_till_cloudsize(1);
  }

  @Test
  public void makeCopyFoCVScenario() {
    long builderSeed = new Random().nextLong();
    String responseColumnName = "survived";
    int numberOfRuns = 5;

    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr, responseColumnName);

      Scope.track(fr);

      SplitFrame sf = new SplitFrame(fr, new double[]{0.7, 0.3}, null);
      sf.exec().get();
      Key<Frame>[] ksplits = sf._destination_frames;
      final Frame train = ksplits[0].get();
      final Frame test = ksplits[1].get();
      Scope.track(train, test);

      ModelBuilder modelBuilder = modelBuilderForGBMFixture(train, null, responseColumnName, builderSeed);
      // We should init builder before cloning it
      modelBuilder.init(false);

      double referenceResult = scoreOnTest(modelBuilder, test);
      for (int runIdx = 0; runIdx < numberOfRuns; runIdx++) {
        ModelBuilder clonedModelBuilder = modelBuilder.makeCopy();
        Scope.track(clonedModelBuilder._parms.train()); // because we don't use valid frame in CV case -> we need to track only train
        clonedModelBuilder.init(false);

        double evaluationResult = scoreOnTest(clonedModelBuilder, test);
        assertEquals("Original ModelBuilder's score was not equal to its clones scores( " + "runIdx #" + runIdx + ")" , referenceResult, evaluationResult, 1e-5);
      }
    } finally {
      Scope.exit();
    }
  }

  @Test
  public void makeCopyFoValidationSplitScenario() {
    long builderSeed = new Random().nextLong();
    String responseColumnName = "survived";

    int numberOfRuns = 5;

    Scope.enter();
    try {
      Frame fr = parse_test_file("./smalldata/gbm_test/titanic.csv");
      asFactor(fr, responseColumnName);

      Scope.track(fr);

      SplitFrame sf = new SplitFrame(fr, new double[]{0.6, 0.2, 0.2}, null);
      sf.exec().get();
      Key<Frame>[] ksplits = sf._destination_frames;
      final Frame train = ksplits[0].get();
      final Frame valid = ksplits[1].get();
      final Frame test = ksplits[2].get();
      Scope.track(train, valid, test);

      ModelBuilder modelBuilder = modelBuilderForGBMFixture(train, valid, responseColumnName, builderSeed);
      // We should init builder before cloning it
      modelBuilder.init(false);

      double referenceResult = scoreOnTest(modelBuilder, test);
      for (int runIdx = 0; runIdx < numberOfRuns; runIdx++) {
        ModelBuilder clonedModelBuilder = modelBuilder.makeCopy();
        Scope.track(clonedModelBuilder._parms.train(), clonedModelBuilder._parms.valid()); 
        clonedModelBuilder.init(false);

        double evaluationResult = scoreOnTest(clonedModelBuilder, test);
        assertEquals("Original ModelBuilder's score was not equal to its clones scores( " + "runIdx #" + runIdx + ")" , referenceResult, evaluationResult, 1e-5);
      }

    } finally {
      Scope.exit();
    }
  }

  private double scoreOnTest(ModelBuilder modelBuilder, Frame testEncodedFrame) {
    Model retrievedModel;
    Keyed model = modelBuilder.trainModelOnH2ONode().get();
    retrievedModel = DKV.getGet(model._key);
    retrievedModel.score(testEncodedFrame).delete();

    hex.ModelMetricsBinomial mmb = hex.ModelMetricsBinomial.getFromDKV(retrievedModel, testEncodedFrame);
    if (retrievedModel != null) retrievedModel.delete();
    return mmb.auc();
  }

  public static ModelBuilder modelBuilderForGBMFixture(Frame trainingFrame, Frame validFrame, String responseColumnName, long builderSeed) {
    Algo algo = Algo.GBM;
    String algoUrlName = algo.name().toLowerCase();
    String algoName = ModelBuilder.algoName(algoUrlName);
    Key<Model> testModelKey = Key.make("testModelKey");

    Job<Model> job = new Job<>(testModelKey, ModelBuilder.javaName(algoUrlName), algoName);
    ModelBuilder builder = ModelBuilder.make(algoUrlName, job, testModelKey);

    // Model Parameters
    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;

    builder._parms = gbmParameters;
    builder._parms._seed = builderSeed;
    builder._parms._response_column = responseColumnName;
    builder._parms._train = trainingFrame._key;
    
    if(validFrame != null) {
      builder._parms._valid = validFrame._key;
      builder._parms._nfolds = 0;
    } else {
      builder._parms._nfolds = 5;
      builder._parms._keep_cross_validation_models = true;
      builder._parms._keep_cross_validation_predictions = true;
    }
    return builder;
  }
  
}
