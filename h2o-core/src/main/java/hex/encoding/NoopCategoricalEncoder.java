package hex.encoding;

import hex.DataTransformSupport;
import water.H2O;
import water.Key;
import water.fvec.Frame;

public class NoopCategoricalEncoder extends BaseCategoricalEncoder {
  @Override
  H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skippedCols) {
    return null;
  }

  @Override
  public Frame encode(Frame fr, String[] skippedCols, Stage stage, DataTransformSupport params) {
    return fr;
  }
}
