package water.api;

import hex.SplitFrame;
import water.Key;
import water.api.KeyV1.FrameKeyV1;

public class SplitFrameV2 extends JobV2<SplitFrame, SplitFrameV2> {
  @API(help="dataset")
  public FrameKeyV1 dataset;

  @API(help="Split ratios - resulting number of split is ratios.length+1", json=true)
  public double[] ratios;

  @API(help="Destination keys for each output frame split.")
  public FrameKeyV1[] dest_keys;

  @Override public SplitFrame createImpl() { return new SplitFrame(Key.make(), null); }
}
