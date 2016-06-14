package water.api;

import water.api.KeyV3.FrameKeyV3;
import water.util.FrameUtils;

import java.util.Random;

public class MissingInserterV3 extends SchemaV3<FrameUtils.MissingInserter, MissingInserterV3> {
  @API(help="dataset", required = true)
  public FrameKeyV3 dataset;

  @API(help="Fraction of data to replace with a missing value", required=true)
  public double fraction;

  @API(help="Seed", required = false)
  public long seed = new Random().nextLong();

  @Override public FrameUtils.MissingInserter createImpl() {
    return new FrameUtils.MissingInserter(null, 0, 0); //fill dummy version
  }
}
