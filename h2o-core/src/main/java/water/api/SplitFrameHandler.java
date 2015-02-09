package water.api;

import hex.SplitFrame;
import water.Job;


public class SplitFrameHandler extends Handler {

  public SplitFrameV2 run(int version, SplitFrameV2 sf) {
    SplitFrame sfr = new SplitFrame();
    sf.fillImpl(sfr);
    sfr.execImpl();
    return (SplitFrameV2)Schema.schema(version, SplitFrame.class).fillFromImpl(sfr);
  }
}