package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncodingParams;

public interface TEParamsSelectionStrategy {
  TargetEncodingParams getBestParams();
}

