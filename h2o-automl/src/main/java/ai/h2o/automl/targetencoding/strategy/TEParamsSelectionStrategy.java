package ai.h2o.automl.targetencoding.strategy;

import ai.h2o.automl.targetencoding.TargetEncodingParams;
import hex.ModelBuilder;
import water.Iced;

//TODO Either perform random grid search over parameters or introduce evolutionary selection algo, or just fixed/default values
public abstract class TEParamsSelectionStrategy extends Iced {
  public abstract TargetEncodingParams getBestParams(ModelBuilder modelBuilder);
}

