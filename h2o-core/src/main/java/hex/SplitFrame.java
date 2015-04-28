package hex;

import jsr166y.CountedCompleter;
import water.*;
import water.fvec.*;

import static water.util.FrameUtils.generateNumKeys;

public class SplitFrame extends Transformer<SplitFrame> {
  /** Input dataset to split */
  public Frame dataset;
  /** Split ratios */
  public double[]  ratios;
  /** Output destination keys. */
  public Key<Frame>[] destination_frames;

  public SplitFrame() { this(Key.make()); }
  public SplitFrame(Key<SplitFrame> dest) { this(dest, "SplitFrame job"); }
  public SplitFrame(Key<SplitFrame> dest, String desc) { super(dest, desc); }

  @Override
  public SplitFrame execImpl() {
    if (ratios.length < 0)      throw new IllegalArgumentException("No ratio specified!");
    if (ratios.length > 100)    throw new IllegalArgumentException("Too many frame splits demanded!");
    for( double p : ratios )
      if( p < 0.0 || p > 1.0 )  throw new IllegalArgumentException("Ratios must be between 0 and 1!");
    if (destination_frames != null && ratios.length != destination_frames.length-1)
                                throw new IllegalArgumentException("Number of destination keys has to match to a number of split ratios!");

    // Create destinatio keys if not specified
    if (destination_frames == null) destination_frames = generateNumKeys(dataset._key, ratios.length+1);

    H2O.H2OCountedCompleter hcc = new H2O.H2OCountedCompleter() {
      @Override
      protected void compute2() {
        FrameSplitter fs = new FrameSplitter(this, dataset, ratios, destination_frames, _key);
        H2O.submitTask(fs);
      }

      @Override
      public void onCompletion(CountedCompleter caller) {
        FrameSplitter fs = (FrameSplitter) caller;
        Job j = DKV.getGet(_key);
        // Mark job as done
        // FIXME: the FrameSPlitter does not follow semantics of exception propagation
        if (fs.getErrors() != null) j.failed(fs.getErrors()[0]);
        else j.done();
      }

      @Override
      public boolean onExceptionalCompletion(Throwable ex, CountedCompleter caller) {
        // Mark job as failed in this case
        ((Job)DKV.getGet(_key)).failed(ex);
        return false;
      }
    };

    return (SplitFrame) start(hcc, ratios.length + 1);
  }
}
