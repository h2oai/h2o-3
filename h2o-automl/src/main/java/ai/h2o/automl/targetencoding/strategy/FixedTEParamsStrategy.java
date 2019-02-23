package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.ModelBuilder;

public class FixedTEParamsStrategy extends TEParamsSelectionStrategy {
  
  private TargetEncodingParams _params;
  
  public FixedTEParamsStrategy(TargetEncodingParams params) {
    this._params = params; 
  }

  @Override
  public TargetEncodingParams getBestParams(ModelBuilder modelBuilder) {
    return this._params;
  }
}
