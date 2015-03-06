package water.api;

import water.api.KeyV1.FrameKeyV1;
import water.util.FrameUtils;

public class MissingInserterV2 extends JobV2<FrameUtils.MissingInserter, MissingInserterV2> {
  @API(help="dataset", required = true)
  public FrameKeyV1 dataset;

  @API(help="Seed", required = true)
  public long seed;

  @API(help="Fraction of data to replace with a missing value", required=true)
  public double fraction;

  @Override public FrameUtils.MissingInserter createImpl() {
    return new FrameUtils.MissingInserter(null, 0, 0);
  }
}
