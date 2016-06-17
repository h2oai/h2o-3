package water.api;

import hex.SplitFrame;
import water.Job;

public class SplitFrameHandler extends Handler {

  public SplitFrameV3 run(int version, SplitFrameV3 sf) {
    SplitFrame splitFrame = sf.createAndFillImpl();
    Job job = splitFrame.exec();
    SplitFrameV3 spv3 = (SplitFrameV3) SchemaServer.schema(version, SplitFrame.class).fillFromImpl(splitFrame);
    spv3.key = new KeyV3.JobKeyV3(job._key);
    return spv3;
  }
}