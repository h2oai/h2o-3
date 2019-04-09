package ai.h2o.automl.targetencoding.strategy;

import water.util.Log;

import java.util.*;

public abstract class GridBasedTEParamsSelectionStrategy extends TEParamsSelectionStrategy {

  protected ModelValidationMode _modelValidationMode;
  protected RandomGridEntrySelector _randomGridEntrySelector;
  protected double _ratioOfHyperSpaceToExplore;
  protected String[] _columnsToEncode; // TODO maybe we don't need this as we have `_columnNameToIdxMap`
  protected transient Map<String, Double> _columnNameToIdxMap;
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
      _grid.put("_column_to_encode_" + columnName2IdxMapping.getKey(), new Double[]{columnName2IdxMapping.getValue(), -1.0});
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
}

