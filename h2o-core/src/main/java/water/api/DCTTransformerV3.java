package water.api;

import water.Key;
import water.api.KeyV3.FrameKeyV3;
import water.fvec.Frame;
import water.util.DCTTransformer;

public class DCTTransformerV3 extends SchemaV3<DCTTransformer, DCTTransformerV3> {
  @API(help="Dataset", required = true)
  public FrameKeyV3 dataset;

  @API(help="Destination Frame ID")
  public FrameKeyV3 destination_frame;

  @API(help="Dimensions of the input array: Height, Width, Depth (Nx1x1 for 1D, NxMx1 for 2D)", required = true)
  public int[] dimensions;

  @API(help="Whether to do the inverse transform")
  public boolean inverse;

  @Override public DCTTransformer createImpl() {
    return new DCTTransformer(destination_frame == null ? Key.<Frame>make() : destination_frame.key());
  }
}
