package hex.encoding;

import water.H2O;
import water.Key;
import water.fvec.Frame;

public class NoopCategoricalEncoder extends BaseCategoricalEncoder {
  @Override
  H2O.H2OCountedCompleter newDriver(Frame fr, Key<Frame> destKey, String[] skipCols) {
    return null;
  }

  @Override
  public Frame encode(Frame fr, String[] skipCols) {
    return fr;
  }
}
