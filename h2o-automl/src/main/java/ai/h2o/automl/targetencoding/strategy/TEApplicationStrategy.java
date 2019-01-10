package ai.h2o.automl.targetencoding.strategy;

import water.Iced;

public abstract class TEApplicationStrategy extends Iced {
  public abstract String[] getColumnsToEncode();
}

