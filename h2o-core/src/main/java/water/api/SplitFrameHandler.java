package water.api;

import hex.SplitFrame;

public class SplitFrameHandler extends Handler {

  public SplitFrameV2 run(int version, SplitFrameV2 sf) {
    SplitFrame splitFrame = sf.createAndFillImpl();
    return (SplitFrameV2) Schema.schema(version, SplitFrame.class).fillFromImpl(splitFrame.exec());
  }
}