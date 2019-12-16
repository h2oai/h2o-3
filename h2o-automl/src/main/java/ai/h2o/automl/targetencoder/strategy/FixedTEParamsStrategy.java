package ai.h2o.automl.targetencoder.strategy;

import ai.h2o.targetencoding.TargetEncoderModel;
import hex.ModelBuilder;

public class FixedTEParamsStrategy extends TEParamsSelectionStrategy {

  private TargetEncoderModel.TargetEncoderParameters _teParams;

  public FixedTEParamsStrategy(TargetEncoderModel.TargetEncoderParameters params) {
    _teParams = params;
  }

  @Override
  public TargetEncoderModel.TargetEncoderParameters getBestParams(ModelBuilder modelBuilder) {
    return _teParams;
  }
}