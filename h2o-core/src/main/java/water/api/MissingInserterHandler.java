package water.api;

import water.util.FrameUtils;

public class MissingInserterHandler extends Handler {

  public MissingInserterV3 run(int version, MissingInserterV3 mis) {
    FrameUtils.MissingInserter mi = mis.createAndFillImpl();
    mi.execImpl();
    return (MissingInserterV3)Schema.schema(version, FrameUtils.MissingInserter.class).fillFromImpl(mi);
  }
}