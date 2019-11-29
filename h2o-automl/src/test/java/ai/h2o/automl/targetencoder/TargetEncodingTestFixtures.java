package ai.h2o.automl.targetencoder;


import ai.h2o.automl.Algo;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.Model;
import hex.ModelBuilder;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.schemas.TargetEncoderV3;
import hex.tree.SharedTreeModel;
import hex.tree.gbm.GBMModel;
import water.Job;
import water.Key;
import water.api.GridSearchHandler;
import water.fvec.Frame;

import java.util.HashMap;

public class TargetEncodingTestFixtures {

  // TODO or we can instantiate grid once and just reset its iterator every time
  private static TargetEncoderModel.TargetEncoderParameters getRandomParameters(long seed) {

    HashMap<String, Object[]> hpGrid = new HashMap<>();
    hpGrid.put("data_leakage_handling", new TargetEncoder.DataLeakageHandlingStrategy[]{TargetEncoder.DataLeakageHandlingStrategy.KFold, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut});
    hpGrid.put("blending", new Boolean[]{true, false});
    hpGrid.put("noise_level", new Double[]{0.0, 0.01, 0.1});
    hpGrid.put("k", new Double[]{1.0, 2.0, 3.0});
    hpGrid.put("f", new Double[]{5.0, 10.0, 20.0});

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearchHandler.DefaultModelParametersBuilderFactory<TargetEncoderModel.TargetEncoderParameters, TargetEncoderV3.TargetEncoderParametersV3> modelParametersBuilderFactory = new GridSearchHandler.DefaultModelParametersBuilderFactory<>();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    hyperSpaceSearchCriteria.set_seed(seed);
    hyperSpaceSearchCriteria.set_max_models(1);

    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, hpGrid, modelParametersBuilderFactory, hyperSpaceSearchCriteria);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = walker.iterator();
    return iterator.nextModelParameters(null);
  }

  public static TargetEncoderModel.TargetEncoderParameters randomTEParams(long seed) {
   return getRandomParameters(seed);
  }

  public static TargetEncoderModel.TargetEncoderParameters randomTEParams(TargetEncoder.DataLeakageHandlingStrategy leakageHandlingStrategy, long seed) {
    TargetEncoderModel.TargetEncoderParameters randomParameters = getRandomParameters(seed);
    randomParameters._data_leakage_handling = leakageHandlingStrategy;
    return randomParameters;
  }

  public static TargetEncoderModel.TargetEncoderParameters randomTEParams() {
    return randomTEParams(-1);
  }

  // TODO get rid of this as we can do ModelBuilder.make(params)
  public static ModelBuilder modelBuilderGBMWithCVFixture(Frame fr, String responseColumnName, long builderSeed) {
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

    builder._parms._train = fr._key;
    builder._parms._response_column = responseColumnName;
    builder._parms._nfolds = 5;
    builder._parms._keep_cross_validation_models = true;
    builder._parms._keep_cross_validation_predictions = true;
    return builder;
  }

  public static ModelBuilder modelBuilderGBMWithValidFrameFixture(Frame train, Frame valid, String responseColumnName, long builderSeed) {
    Algo algo = Algo.GBM;
    String algoUrlName = algo.name().toLowerCase();
    String algoName = ModelBuilder.algoName(algoUrlName);
    Key<Model> testModelKey = Key.make("testModelKey_" + Key.make().toString());

    Job<Model> job = new Job<>(testModelKey, ModelBuilder.javaName(algoUrlName), algoName);
    ModelBuilder builder = ModelBuilder.make(algoUrlName, job, testModelKey);

    // Model Parameters
    GBMModel.GBMParameters gbmParameters = new GBMModel.GBMParameters();
    gbmParameters._score_tree_interval = 5;
    gbmParameters._histogram_type = SharedTreeModel.SharedTreeParameters.HistogramType.AUTO;

    builder._parms = gbmParameters;
    builder._parms._seed = builderSeed;

    builder._parms._train = train._key;
    if (valid != null) builder._parms._valid = valid._key;
    builder._parms._response_column = responseColumnName;

    return builder;
  }

}