package water.api;

import water.api.schemas3.JobV3;
import water.api.schemas3.MissingInserterV3;
import water.util.FrameUtils;

public class MissingInserterHandler extends Handler {

  public JobV3 run(int version, MissingInserterV3 mis) {
    FrameUtils.MissingInserter mi = mis.createAndFillImpl();
    return new JobV3(mi.execImpl());
  }
}
