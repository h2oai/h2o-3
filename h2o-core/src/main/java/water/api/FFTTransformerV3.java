package water.api;

import water.Key;
import water.api.KeyV3.FrameKeyV3;
import water.util.FFTTransformer;

public class FFTTransformerV3 extends JobV3<FFTTransformer, FFTTransformerV3> {
  @API(help="Dataset", required = true)
  public FrameKeyV3 dataset;

  @API(help="Destination Frame ID")
  public FrameKeyV3 destination_frame;

  @API(help="Dimensions of the input array: Length, Width, Depth", required = true)
  public int[] dimensions;

  @API(help="Whether to do the inverse FFT")
  public boolean inverse;

  @Override public FFTTransformer createImpl() {
    return new FFTTransformer(Key.<FFTTransformer>make(), "FFTTransformer job");
  }
}
