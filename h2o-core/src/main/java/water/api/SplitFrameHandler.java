package water.api;

import hex.FrameSplitter;
import hex.SplitFrame;
import water.fvec.Frame;


public class SplitFrameHandler extends Handler {

  public SplitFrameV2 run(int version, SplitFrameV2 sf) {
    // TODO: temporary hackery
    SplitFrame splitFrame = sf.createAndFillImpl();

    FrameSplitter splitter = new FrameSplitter(splitFrame.dataset, splitFrame.ratios, splitFrame.destKeys, null);
    water.H2O.submitTask(splitter);

    // hack in the destKeys, which might be generated inside FrameSplitter:
    Frame[] splits = splitter.getResult();
    sf.destKeys = new KeyV1.FrameKeyV1[splits.length];
    int i = 0;
    for (Frame f : splits)
      sf.destKeys[i++] = new KeyV1.FrameKeyV1(f._key);
    return sf;
  }
}