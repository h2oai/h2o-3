package hex;

import water.*;
import water.fvec.*;

public class SplitFrame extends Job<SplitFrame> {
  public Frame    dataset;
  public double[]  ratios;
  public Key<Frame>[]    destKeys;

  public SplitFrame(Key<SplitFrame> dest, String desc) { super(dest, desc); }
  public SplitFrame() { super(Key.make(), null); }

  public void execImpl() {
    if (ratios.length < 0)      throw new IllegalArgumentException("No ratio specified!");
    if (ratios.length > 100)    throw new IllegalArgumentException("Too many frame splits demanded!");
    for( double p : ratios )
      if( p < 0.0 || p > 1.0 )  throw new IllegalArgumentException("Ratios must be between 0 and 1");
    FrameSplitter fs = new FrameSplitter(null, this.dataset, this.ratios, this.destKeys, this._key);
    start(fs, ratios.length + 1);
  }
}
