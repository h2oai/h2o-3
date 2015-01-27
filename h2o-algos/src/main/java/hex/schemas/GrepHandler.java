package hex.schemas;

import hex.grep.Grep;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class GrepHandler extends Handler {
  public GrepHandler() {}
  public GrepV2 train(int version, GrepV2 s) {
    Grep e = s.createAndFillImpl();
    assert e._parms != null;
    e.trainModel();
    return s.fillFromImpl(e);
  }
}
