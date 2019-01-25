package ai.h2o.automl.targetencoding.strategy;

import water.Iced;

/**
 * Strategy that specifies which columns of the frame should be target encoded
 */
public abstract class TEApplicationStrategy extends Iced {
  public abstract String[] getColumnsToEncode();
}

