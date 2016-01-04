package water.api;

import water.util.FrameUtils;

public class MissingInserterHandler extends Handler {

  public JobV3 run(int version, MissingInserterV3 mis) {
    FrameUtils.MissingInserter mi = mis.createAndFillImpl();
    return (JobV3)Schema.schema(version, water.Job.class).fillFromImpl(mi.execImpl());
  }
}
