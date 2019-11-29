package ai.h2o.automl.targetencoder.strategy;


import ai.h2o.automl.targetencoder.TargetEncodingParams;
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