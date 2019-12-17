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
import water.util.Log;
import water.util.TwoDimTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

/**
 *  Random grid search for searching optimal hyperparameters for target encoding
 */
// TODO make it generic with MP type
public class GridSearchTEParamsSelectionStrategy extends TEParamsSelectionStrategy<TargetEncoderModel.TargetEncoderParameters>{

  private Frame _leaderboardData;
  private String _responseColumn;
  private boolean _theBiggerTheBetter;

  private PriorityQueue<TEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters>> _evaluatedQueue;
  private TargetEncodingHyperparamsEvaluator _evaluator = null;

  protected ModelValidationMode _modelValidationMode;
  protected HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> _walker;
  protected double _ratioOfHyperSpaceToExplore;
  protected double _earlyStoppingRatio;

  protected transient String[] _columnNamesToEncode;
  protected transient Map<String, Double> _columnNameToIdxMap;
  protected boolean _searchOverColumns;

  protected long _seed;

  public GridSearchTEParamsSelectionStrategy(Frame leaderboard,
                                             String responseColumn,
                                             String[] columnNamesToEncode,
                                             Map<String, Double> columnNameToIdxMap,
                                             boolean theBiggerTheBetter,
                                             AutoMLBuildSpec.AutoMLTEControl teBuildSpec) {

    this(leaderboard, responseColumn, columnNamesToEncode, columnNameToIdxMap,
            theBiggerTheBetter, teBuildSpec, new TargetEncodingHyperparamsEvaluator());
  }

  public GridSearchTEParamsSelectionStrategy(Frame leaderboard,
                                             String responseColumn,
                                             String[] columnNamesToEncode,
                                             Map<String, Double> columnNameToIdxMap,
                                             boolean theBiggerTheBetter,
                                             AutoMLBuildSpec.AutoMLTEControl teBuildSpec,
                                             TargetEncodingHyperparamsEvaluator evaluator) {
    _seed = teBuildSpec.seed;

    _evaluator = evaluator;

    _leaderboardData = leaderboard;
    _responseColumn = responseColumn;
    _columnNamesToEncode = columnNamesToEncode;
    _columnNameToIdxMap = columnNameToIdxMap;

    _ratioOfHyperSpaceToExplore = teBuildSpec.ratio_of_hyperspace_to_explore;
    _earlyStoppingRatio = teBuildSpec.early_stopping_ratio;
    _theBiggerTheBetter = theBiggerTheBetter;

  }

  public void setTESearchSpace(ModelValidationMode modelValidationMode) {
    _modelValidationMode = modelValidationMode;

    HashMap<String, Object[]> grid = new HashMap<>();
    grid.put("blending", new Boolean[]{true, false});
    grid.put("noise_level", new Double[]{0.0, 0.01, 0.1});
    grid.put("k", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
    grid.put("f", new Double[]{5.0, 10.0, 20.0});

    switch (_modelValidationMode) {
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
    HyperSpaceWalker.RandomDiscreteValueWalker<TargetEncoderModel.TargetEncoderParameters> walker = new HyperSpaceWalker.RandomDiscreteValueWalker<>(parameters, grid, modelParametersBuilderFactory, hyperSpaceSearchCriteria);

    _walker = walker;

    Log.info("Size of TE hyperspace to explore " + walker.getMaxHyperSpaceSize());
    int expectedNumberOfEntries = (int) (Math.max(1, walker.getMaxHyperSpaceSize() * _ratioOfHyperSpaceToExplore));
    _evaluatedQueue = new PriorityQueue<>(expectedNumberOfEntries, new EvaluatedComparator(_theBiggerTheBetter));
  }

  public TargetEncoderModel.TargetEncoderParameters getBestParams(ModelBuilder modelBuilder) {
    return getBestParamsWithEvaluation(modelBuilder).getItem();
  }

  public TEParamsSelectionStrategy.Evaluated<TargetEncoderModel.TargetEncoderParameters> getBestParamsWithEvaluation(ModelBuilder modelBuilder) {
    assert _modelValidationMode != null : "`setTESearchSpace()` method should has been called to setup appropriate grid search.";

    EarlyStopper earlyStopper = new EarlyStopper(_earlyStoppingRatio, _ratioOfHyperSpaceToExplore, _walker.getMaxHyperSpaceSize(), -1, _theBiggerTheBetter);

    HyperSpaceWalker.HyperSpaceIterator<TargetEncoderModel.TargetEncoderParameters> iterator = _walker.iterator();

//    try {
      while (earlyStopper.proceed()) {

        TargetEncoderModel.TargetEncoderParameters nextModelParameters = iterator.nextModelParameters(null);

        ModelBuilder clonedModelBuilder = ModelBuilder.make(modelBuilder._parms);

        clonedModelBuilder.init(false);

        double evaluationResult = _evaluator.evaluate(nextModelParameters, clonedModelBuilder, _modelValidationMode, _leaderboardData, _columnNamesToEncode, _seed);

        earlyStopper.update(evaluationResult);

        _evaluatedQueue.add(new Evaluated<>(nextModelParameters, evaluationResult, earlyStopper.getTotalAttemptsCount()));
      }
//    } catch (RandomGridEntrySelector.GridSearchCompleted ex) { // TODO consider to wrap in try-catch
      // just proceed by returning best gridEntry found so far
//    }

    return _evaluatedQueue.peek();
  }

  private String[] extractColumnsToEncodeFromGridEntry(Map<String, Object> gridEntry) {
    ArrayList<String> columnsIdxsToEncode = new ArrayList();
    for (Map.Entry<String, Object> entry : gridEntry.entrySet()) {
      String column_to_encode_prefix = "_column_to_encode_";
      if(entry.getKey().contains(column_to_encode_prefix)) {
        double entryValue = (double) entry.getValue();
        if(entryValue != -1.0)
          columnsIdxsToEncode.add(entry.getKey().substring(column_to_encode_prefix.length()));
      }

    }
    return columnsIdxsToEncode.toArray(new String[]{});
  }

  public String getResponseColumn() {
    return _responseColumn;
  }

  public boolean isTheBiggerTheBetter() {
    return _theBiggerTheBetter;
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

  public static class EarlyStopper {
    private int _seqAttemptsBeforeStopping;
    private int _totalAttemptsBeforeStopping;
    private double _currentThreshold;
    private boolean _theBiggerTheBetter;
    private int _totalAttemptsCount = 0;
    private int _fruitlessAttemptsSinceLastResetCount = 0;

    public EarlyStopper(double earlyStoppingRatio, double ratioOfHyperspaceToExplore, long numberOfUnexploredEntries, double initialThreshold, boolean theBiggerTheBetter) {

      _seqAttemptsBeforeStopping = (int) (numberOfUnexploredEntries * earlyStoppingRatio);
      _totalAttemptsBeforeStopping = (int) (numberOfUnexploredEntries * ratioOfHyperspaceToExplore );
      _currentThreshold = initialThreshold;
      _theBiggerTheBetter = theBiggerTheBetter;
    }

    public boolean proceed() {
      return _fruitlessAttemptsSinceLastResetCount < _seqAttemptsBeforeStopping && _totalAttemptsCount < _totalAttemptsBeforeStopping;
    };

    public void update(double newValue) {
      boolean conditionToContinue = _theBiggerTheBetter ? newValue <= _currentThreshold : newValue > _currentThreshold;
      if(conditionToContinue) _fruitlessAttemptsSinceLastResetCount++;
      else {
        _fruitlessAttemptsSinceLastResetCount = 0;
        _currentThreshold = newValue;
      }
      _totalAttemptsCount++;
    }

    public int getTotalAttemptsCount() {
      return _totalAttemptsCount;
    }

  }
}