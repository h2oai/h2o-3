package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncodingParams;

public class FixedTEParamsStrategy extends TEParamsSelectionStrategy {
  
  private TargetEncodingParams _params;
  
  public FixedTEParamsStrategy(TargetEncodingParams params) {
    this._params = params; 
  }

  @Override
  public TargetEncodingParams getBestParams() {
    return this._params;
  }

}
