package water.api;

import hex.SplitFrame;
import water.Key;
import water.fvec.Frame;

public class SplitFrameV2 extends JobV2<SplitFrame, SplitFrameV2> {
  @API(help="dataset")
  public Key<Frame> dataset;

  @API(help="Split ratios - resulting number of split is ratios.length+1", json=true)
  public double[] ratios;

  @API(help="Destination keys for each output frame split.")
  public Key[] destKeys;

  @Override public SplitFrame createImpl() { return new SplitFrame(Key.make(), null); }
}
