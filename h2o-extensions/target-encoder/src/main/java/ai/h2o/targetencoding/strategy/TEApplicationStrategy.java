package ai.h2o.targetencoding.strategy;

import water.Iced;

/**
 * Strategy that defines which columns of the frame should be encoded with TargetEncoder
 */
public abstract class TEApplicationStrategy extends Iced {
  public abstract String[] getColumnsToEncode();
}

