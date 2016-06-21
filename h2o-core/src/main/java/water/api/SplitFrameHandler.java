package water.api;

import hex.SplitFrame;
import water.Job;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SplitFrameV3;

public class SplitFrameHandler extends Handler {

  public SplitFrameV3 run(int version, SplitFrameV3 sf) {
    SplitFrame splitFrame = sf.createAndFillImpl();
    Job job = splitFrame.exec();
    SplitFrameV3 spv3 = new SplitFrameV3(splitFrame);
    spv3.key = new KeyV3.JobKeyV3(job._key);
    return spv3;
  }
}