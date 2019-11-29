package ai.h2o.automl.targetencoder.strategy;

import water.util.Log;

import java.util.*;

public abstract class GridBasedTEParamsSelectionStrategy extends TEParamsSelectionStrategy {

  protected ModelValidationMode _modelValidationMode;
  protected RandomGridEntrySelector _randomGridEntrySelector;
  protected double _ratioOfHyperSpaceToExplore;
  protected double _earlyStoppingRatio;

  protected transient Map<String, Double> _columnNameToIdxMap;
  protected boolean _searchOverColumns;

  protected long _seed;

  // Note: representing every value in a grid as a double is convenient if we will want to store materialised version of a grid in a Frame for SMBO
  public void setTESearchSpace(ModelValidationMode modelValidationMode) {

    HashMap<String, Object[]> _grid = new HashMap<>();
    _grid.put("_withBlending", new Double[]{1.0/*, false*/}); // NOTE: we can postpone implementation of hierarchical hyperparameter spaces... as in most cases blending is helpful.
    _grid.put("_inflection_point", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
    _grid.put("_noise_level", new Double[]{0.0, 0.01,  0.1});
    _grid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});

    // Expanding columns to encode into multiple grid dimensions
    for (Map.Entry<String, Double> columnName2IdxMapping : _columnNameToIdxMap.entrySet()) {
      Double[] values = _searchOverColumns ? new Double[]{columnName2IdxMapping.getValue(), -1.0} : new Double[] {columnName2IdxMapping.getValue()};
      _grid.put("_column_to_encode_" + columnName2IdxMapping.getKey(), values);
    }

    switch (modelValidationMode) {
      case CV:
        _grid.put("_holdoutType", new Double[]{ 1.0});// consider LOO as well
        break;
      case VALIDATION_FRAME:
        //Note:  when we chose holdoutType=None we don't need to search for noise
        _grid.put("_holdoutType", new Double[]{0.0, 1.0, 2.0}); // see TargetEncoder.DataLeakageHandlingStrategy
        break;
    }

    _modelValidationMode = modelValidationMode;

    _randomGridEntrySelector = new RandomGridEntrySelector(_grid, _seed);
    Log.info("Size of TE hyperspace to explore " + _randomGridEntrySelector.spaceSize());
  }

  public static class EarlyStopper {
    private int _seqAttemptsBeforeStopping;
    private int _totalAttemptsBeforeStopping;
    private double _currentThreshold;
    private boolean _theBiggerTheBetter;
    private int _totalAttemptsCount = 0;
    private int _fruitlessAttemptsSinceLastResetCount = 0;

    public EarlyStopper(double earlyStoppingRatio, double ratioOfHyperspaceToExplore, int numberOfUnexploredEntries, double initialThreshold, boolean theBiggerTheBetter) {

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

  public RandomGridEntrySelector getRandomGridEntrySelector() {
    return _randomGridEntrySelector;
  }
}