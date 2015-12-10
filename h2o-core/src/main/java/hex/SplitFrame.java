package hex;

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
public class SplitFrame extends Transformer<SplitFrame.Frames> {
  /** Input dataset to split */
  public Frame _dataset;
  /** Split ratios */
  public double[] _ratios;
  /** Output destination keys. */
  public Key<Frame>[] _destination_frames;

  public SplitFrame(Frame dataset, double[] ratios, Key<Frame>[] destination_frames) {
    this();
    _dataset = dataset;
    _ratios = ratios;
    _destination_frames = destination_frames;
  }
  public SplitFrame() { super(null, "hex.SplitFrame$Frames", "SplitFrame"); }

  @Override public Job<Frames> execImpl() {
    if (_ratios.length < 0)      throw new IllegalArgumentException("No ratio specified!");
    if (_ratios.length > 100)    throw new IllegalArgumentException("Too many frame splits demanded!");
    // Check the case for single ratio - FIXME in /4 version change this to throw exception
    for (double r : _ratios)
      if (r <= 0.0) new IllegalArgumentException("Ratio must be > 0!");
    if (_ratios.length == 1)
      if( _ratios[0] < 0.0 || _ratios[0] > 1.0 )  throw new IllegalArgumentException("Ratio must be between 0 and 1!");
    if (_destination_frames != null &&
            !((_ratios.length == 1 && _destination_frames.length == 2) || (_ratios.length == _destination_frames.length)))
                                throw new IllegalArgumentException("Number of destination keys has to match to a number of split ratios!");
    // If array of ratios is given scale them and take first n-1 and pass them to FrameSplitter
    final double[] computedRatios;
    if (_ratios.length > 1) {
      double sum = ArrayUtils.sum(_ratios);
      if (sum <= 0.0) throw new IllegalArgumentException("Ratios sum has to be > 0!");
      if( sum < 1 ) computedRatios = _ratios;
      else {
        computedRatios = new double[_ratios.length - 1];
        for (int i = 0; i < _ratios.length - 1; i++) computedRatios[i] = _ratios[i] / sum;
      }
    } else {
      computedRatios = _ratios;
    }

    // Create destination keys if not specified
    if (_destination_frames == null) _destination_frames = generateNumKeys(_dataset._key, computedRatios.length+1);

    FrameSplitter fs = new FrameSplitter(_dataset, computedRatios, _destination_frames, _job._key);
    return _job.start(fs, computedRatios.length + 1);
  }
  public static class Frames extends Keyed { public Key<Frame>[] _keys; }
}

