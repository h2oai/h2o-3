package water.parser;

import water.Job;
import water.fvec.Frame;
import water.fvec.Vec;

public abstract class ParseFinalizer {
  
  public abstract Frame finalize(Job<Frame> job, Vec[] parsedVecs, ParseSetup setup, int[] fileChunkOffsets);
  
  private static final ParseFinalizer DEFAULT = new ParseFinalizer() {
    @Override
    public Frame finalize(Job<Frame> job, Vec[] parsedVecs, ParseSetup setup, int[] fileChunkOffsets) {
      return new Frame(job._result, setup._column_names, parsedVecs);
    }
  };

  public static ParseFinalizer get(ParseSetup setup) {
    if (setup._synthetic_column_names != null) {
      return new SyntheticColumnGenerator();
    } else {
      return DEFAULT;
    }
  }
  
}
