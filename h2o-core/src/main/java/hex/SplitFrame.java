package hex;

import jsr166y.CountedCompleter;
import water.*;
import water.fvec.*;
import water.util.ArrayUtils;

import static water.util.FrameUtils.generateNumKeys;

/**
 * Split given frame based on given ratio.
 *
 * If single number is given then it splits a given frame into two frames (FIXME: will throw exception)
 * if N ratios are given then then N-splits are produced.
 */
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
    // Check the case for single ratio - FIXME in /4 version change this to throw exception
    for (double r : ratios)
      if (r <= 0.0) new IllegalArgumentException("Ratio must be > 0!");
    if (ratios.length == 1)
      if( ratios[0] < 0.0 || ratios[0] > 1.0 )  throw new IllegalArgumentException("Ratio must be between 0 and 1!");
    if (destination_frames != null &&
            !((ratios.length == 1 && destination_frames.length == 2) || (ratios.length == destination_frames.length)))
                                throw new IllegalArgumentException("Number of destination keys has to match to a number of split ratios!");
    // If array of ratios is given scale them and take first n-1 and pass them to FrameSplitter
    final double[] computedRatios;
    if (ratios.length > 1) {
      double sum = ArrayUtils.sum(ratios);
      if (sum <= 0.0) throw new IllegalArgumentException("Ratios sum has to be > 0!");
      if( sum <= 1 ) computedRatios = ratios;
      else {
        computedRatios = new double[ratios.length - 1];
        for (int i = 0; i < ratios.length - 1; i++) computedRatios[i] = ratios[i] / sum;
      }
    } else {
      computedRatios = ratios;
    }

    // Create destination keys if not specified
    if (destination_frames == null) destination_frames = generateNumKeys(dataset._key, computedRatios.length+1);

    H2O.H2OCountedCompleter hcc = new H2O.H2OCountedCompleter() {
      @Override
      protected void compute2() {
        FrameSplitter fs = new FrameSplitter(this, dataset, computedRatios, destination_frames, _key);
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

    return (SplitFrame) start(hcc, computedRatios.length + 1);
  }
}
