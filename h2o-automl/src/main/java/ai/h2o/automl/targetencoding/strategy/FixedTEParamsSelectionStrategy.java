package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncodingParams;

public class FixedTEParamsSelectionStrategy implements TEParamsSelectionStrategy {
  
  private TargetEncodingParams _params;
  
  public FixedTEParamsSelectionStrategy(TargetEncodingParams params) {
    this._params = params; 
  }

  @Override
  public TargetEncodingParams getBestParams() {
    return this._params;
  }

}
