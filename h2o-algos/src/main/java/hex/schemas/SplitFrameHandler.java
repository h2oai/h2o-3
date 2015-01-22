package hex.schemas;

import hex.splitframe.SplitFrame;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class SplitFrameHandler extends Handler {
  public SplitFrameHandler() {}
  public SplitFrameV2 train(int version, SplitFrameV2 s) {
    SplitFrame e = s.createAndFillImpl();
    assert e._parms != null;
    e.trainModel();
    return s.fillFromImpl(e);
  }
}
