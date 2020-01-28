package ai.h2o.automl.targetencoder.strategy;

import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.targetencoder.TargetEncodingHyperparamsEvaluator;
import ai.h2o.targetencoding.TargetEncoder;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.ModelBuilder;
import hex.grid.HyperSpaceSearchCriteria;
import hex.grid.HyperSpaceWalker;
import hex.schemas.TargetEncoderV3;
import water.api.GridSearchHandler;
import water.fvec.Frame;
import water.util.TwoDimTable;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 *  Random grid search for searching optimal hyper parameters for target encoding
 */
public class GridSearchTEParamsSelectionStrategy extends TEParamsSelectionStrategy<TargetEncoderModel.TargetEncoderParameters>{

  private Frame _leaderboardData;

  private PriorityQueue<TEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters>> _evaluatedQueue;
  private TargetEncodingHyperparamsEvaluator _evaluator = new TargetEncodingHyperparamsEvaluator();

  protected ModelValidationMode _validationMode;
  protected HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> _walker;

  protected transient String[] _columnNamesToEncode;
  private AutoMLBuildSpec.AutoMLTEControl _teBuildSpec;

  public GridSearchTEParamsSelectionStrategy(Frame leaderboard,
                                             String[] columnNamesToEncode,
                                             boolean theBiggerTheBetter,
                                             AutoMLBuildSpec.AutoMLTEControl teBuildSpec,
                                             ModelValidationMode validationMode) {
    _teBuildSpec = teBuildSpec;

    _leaderboardData = leaderboard;
    _columnNamesToEncode = columnNamesToEncode;

    _evaluatedQueue = new PriorityQueue<>(new EvaluatedComparator(theBiggerTheBetter));

    _validationMode = validationMode;
    setTESearchSpace(_validationMode);
  }

  private void setTESearchSpace(ModelValidationMode modelValidationMode) {

    HashMap<String, Object[]> grid = new HashMap<>();
    grid.put("blending", new Boolean[]{true, false});
    grid.put("noise_level", new Double[]{0.0, 0.01, 0.1});
    grid.put("k", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
    grid.put("f", new Double[]{5.0, 10.0, 20.0});

    switch (modelValidationMode) {
      case CV:
        grid.put("data_leakage_handling", new TargetEncoder.DataLeakageHandlingStrategy[]{TargetEncoder.DataLeakageHandlingStrategy.KFold});
        break;
      case VALIDATION_FRAME:
        // TODO apply filtering. When we choose holdoutType=None we don't need to search for noise
        grid.put("data_leakage_handling", new TargetEncoder.DataLeakageHandlingStrategy[]{TargetEncoder.DataLeakageHandlingStrategy.KFold, TargetEncoder.DataLeakageHandlingStrategy.LeaveOneOut, TargetEncoder.DataLeakageHandlingStrategy.None});
        break;
    }

    TargetEncoderModel.TargetEncoderParameters parameters = new TargetEncoderModel.TargetEncoderParameters();

    GridSearchHandler.DefaultModelParametersBuilderFactory<TargetEncoderModel.TargetEncoderParameters, TargetEncoderV3.TargetEncoderParametersV3> modelParametersBuilderFactory =
            new GridSearchHandler.DefaultModelParametersBuilderFactory<>();

    HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria hyperSpaceSearchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

    _walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, grid, modelParametersBuilderFactory, hyperSpaceSearchCriteria);

  }

  public TargetEncoderModel.TargetEncoderParameters getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public TEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = _walker.iterator();

    long attemptIdx = 0;
    while (_evaluatedQueue.size() < _teBuildSpec.te_max_models) {

      TargetEncoderModel.TargetEncoderParameters nextModelParameters = iterator.nextModelParameters(null);

      ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);

      clonedModelBuilder.init(false);

      double evaluationResult = _evaluator.evaluate(nextModelParameters, clonedModelBuilder, _validationMode, _leaderboardData, _columnNamesToEncode, _teBuildSpec.seed);

      _evaluatedQueue.add(new Evaluated<>(nextModelParameters, evaluationResult, ++attemptIdx));
    }

    return _evaluatedQueue.peek();
  }

  public PriorityQueue<Evaluated<TargetEncoderModel.TargetEncoderParameters>> getEvaluatedQueue() {
    return _evaluatedQueue;
  }


  public static void printOutFrameAsTable(Frame fr) {
    printOutFrameAsTable(fr, false, fr.numRows());
  }

  public static void printOutFrameAsTable(Frame fr, boolean rollups, long limit) {
    assert limit <= Integer.MAX_VALUE;
    TwoDimTable twoDimTable = fr.toTwoDimTable(0, (int) limit, rollups);
    System.out.println(twoDimTable.toString(2, true));
  }
}