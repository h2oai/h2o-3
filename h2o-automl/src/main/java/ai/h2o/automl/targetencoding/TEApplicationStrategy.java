package ai.h2o.automl.targetencoding;

import water.fvec.Frame;

public interface TEApplicationStrategy {
  String[] getColumnsToEncode();
}

