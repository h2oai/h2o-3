package water.api;

import hex.SplitFrame;
import water.Key;
import water.api.KeyV3.FrameKeyV3;

public class SplitFrameV3 extends JobV3<SplitFrame, SplitFrameV3> {
  @API(help="Dataset")
  public FrameKeyV3 dataset;

  @API(help="Split ratios - resulting number of split is ratios.length+1", json=true)
  public double[] ratios;

  @API(help="Destination keys for each output frame split.", direction = API.Direction.INOUT)
  public FrameKeyV3[] destination_frames;

  @Override public SplitFrame createImpl() {
    return new SplitFrame(Key.<SplitFrame>make(), "SplitFrame job");
  }
}
