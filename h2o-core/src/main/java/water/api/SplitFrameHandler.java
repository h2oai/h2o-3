package water.api;

import hex.SplitFrame;

public class SplitFrameHandler extends Handler {

  public SplitFrameV3 run(int version, SplitFrameV3 sf) {
    SplitFrame splitFrame = sf.createAndFillImpl();
    return (SplitFrameV3) Schema.schema(version, SplitFrame.class).fillFromImpl(splitFrame.exec());
  }
}