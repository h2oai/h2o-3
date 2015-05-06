package hex;

import water.DKV;
import water.Job;
import water.Key;
import water.fvec.CreateInteractions;
import water.fvec.Frame;
import water.util.Log;
import water.util.PrettyPrint;

import java.util.Arrays;

/**
 * Create new factors that represent interactions of the given factors
 */
public class Interaction extends Job<Frame> {
  public Key<Frame> _source_frame;
  public int[] _factors = new int[0];
  public boolean _pairwise = false;
  public int _max_factors = 100;
  public int _min_occurrence = 1;

  public Interaction(Key<Frame> dest, String desc) { super(dest, (desc == null ? "CreateFrame" : desc)); }
  public Interaction() { super(Key.make(), "CreateFrame"); }

  public Frame execImpl() {
    try {
      Frame source_frame = DKV.getGet(_source_frame);
      assert(source_frame != null);
      if (_factors.length == 0) throw new IllegalArgumentException("factors must be non-empty.");
      if (_pairwise && _factors.length < 3) Log.info("Ignoring the pairwise option, requires 3 or more factors.");
      for (int v: _factors) {
        if (!source_frame.vecs()[v].isEnum()) {
          throw new IllegalArgumentException("Column " + source_frame.names()[v] + " is not a factor.");
        }
      }
      CreateInteractions in = new CreateInteractions(this, this._key);
      return start(in, in.work()).get();
    } catch( Throwable t ) {
      throw t;
    } finally {
    }
  }


  @Override public String toString() {
    Frame res = get();
    if (res == null) return "Output frame not found";

    if (!_pairwise)
      return "Created interaction feature " + res.names()[0]
              + " (order: " + _factors.length + ") with " + res.lastVec().domain().length + " factor levels"
              + " in" + PrettyPrint.msecs(_end_time-_start_time, true);
    else
      return "Created " + res.numCols() + " pair-wise interaction features " + Arrays.deepToString(res.names())
              + " (order: 2) in" + PrettyPrint.msecs(_end_time-_start_time, true);
  }
}
