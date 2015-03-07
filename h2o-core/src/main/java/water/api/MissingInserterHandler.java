package water.api;

import water.util.FrameUtils;

public class MissingInserterHandler extends Handler {

  public MissingInserterV2 run(int version, MissingInserterV2 mis) {
    FrameUtils.MissingInserter mi = mis.createAndFillImpl();
    mi.execImpl();
    return (MissingInserterV2)Schema.schema(version, FrameUtils.MissingInserter.class).fillFromImpl(mi);
  }
}