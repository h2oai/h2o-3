package ai.h2o.automl.targetencoding.strategy;

import water.util.Log;

import java.util.*;

public abstract class GridBasedTEParamsSelectionStrategy extends TEParamsSelectionStrategy {

  protected ModelValidationMode _modelValidationMode;
  protected RandomSelector _randomSelector;
  protected double _ratioOfHyperSpaceToExplore;
  protected long _seed;

  public void setTESearchSpace(ModelValidationMode modelValidationMode) {
    HashMap<String, Object[]> _grid = new HashMap<>();

    switch (modelValidationMode) {
      case CV: // TODO move up common parameter' ranges
        _grid.put("_withBlending", new Double[]{1.0/*, false*/}); // NOTE: we can postpone implementation of hierarchical hyperparameter spaces... as in most cases blending is helpful.
        _grid.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
        _grid.put("_inflection_point", new Double[]{1.0, 2.0, 3.0, 5.0, 10.0, 50.0, 100.0});
        _grid.put("_smoothing", new Double[]{5.0, 10.0, 20.0});
        _grid.put("_holdoutType", new Double[]{ 1.0});
        break;
      case VALIDATION_FRAME:
        _grid.put("_withBlending", new Double[]{1.0/*, 0.0*/}); // NOTE: we can postpone implementation of hierarchical hyperparameter spaces... as in most cases blending is helpful.
        _grid.put("_noise_level", new Double[]{0.0, 0.01, 0.05, 0.07, 0.1, 0.15 });
//        _grid.put("_noise_level", new Double[]{0.0, 0.1, 0.01});
        _grid.put("_inflection_point", new Double[]{1.0, 2.0, 3.0, 5.0, 8.0, 10.0, 20.0, 30.0, 40.0, 50.0, 100.0});
        _grid.put("_smoothing", new Double[]{1.0, 2.0, 3.0, 4.0, 5.0, 10.0, 20.0});
        
        //Note:  when we chose holdoutType=None we don't need to search for noise
        _grid.put("_holdoutType", new Double[]{0.0, 1.0, 2.0}); // see TargetEncoder.DataLeakageHandlingStrategy
        break;
    }

    _modelValidationMode = modelValidationMode;

    _randomSelector = new RandomSelector(_grid, _seed);
    Log.info("Size of TE hyperspace to explore " + _randomSelector.spaceSize());
  }
}

