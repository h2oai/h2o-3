package water.rapids.vals;

import water.fvec.Frame;
import water.rapids.Val;

import java.util.Map;

/**
 * Value that represents an H2O dataframe ({@link Frame}).
 */
public class ValMapFrame extends Val {
  private final Map<String, Frame> _map;

  public ValMapFrame(Map<String, Frame> fr) {
    _map = fr;
  }

  @Override public int type() { return MFRM; }
  @Override public boolean isMapFrame() { return true; }
  @Override public Map<String, Frame> getMapFrame() { return _map; }
  @Override public String toString() { return "ValMapFrame"; }

}
