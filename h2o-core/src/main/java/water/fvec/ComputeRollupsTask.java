package water.fvec;

import jsr166y.CountedCompleter;
import water.*;
import water.parser.Categorical;
import water.util.ArrayUtils;

/**
 * Created by tomas on 9/1/16.
 */
// Task to compute rollups on its homenode if needed.
// Only computes the rollups, does not fetch them, caller should fetch them via DKV store (to preserve caching).
// Only comutes the rollups if needed (i.e. are null or do not have histo and histo is required)
// If rs computation is already in progress, it will wait for it to finish.
// Throws IAE if the Vec is being modified (or removed) while this task is in progress.
final class ComputeRollupsTask extends DTask<ComputeRollupsTask> {
  final Key _vecKey;
  final Key _rsKey;
  final boolean _computeHisto;

  public ComputeRollupsTask(AVec v, boolean computeHisto){
    super((byte)(Thread.currentThread() instanceof H2O.FJWThr ? currThrPriority()+1 : H2O.MIN_HI_PRIORITY-3));
    _vecKey = v._key;
    _rsKey = v.rollupStatsKey();
    _computeHisto = computeHisto;
  }

  private Value makeComputing(){
    RollupStatsAry newRs = RollupStatsAry.makeComputing();
    CountedCompleter cc = getCompleter(); // should be null or RPCCall
    if(cc != null) assert cc.getCompleter() == null;
    newRs._tsk = cc == null?this:cc;
    return new Value(_rsKey,newRs);
  }
  private void installResponse(Value nnn, RollupStats [] rs) {
    Futures fs = new Futures();
    RollupStatsAry rAry = new RollupStatsAry(rs);
    Value old = DKV.DputIfMatch(_rsKey, new Value(_rsKey, rAry), nnn, fs);
    assert rAry.isReady();
    if(old != nnn)
      throw new IllegalArgumentException("Can not compute rollup stats while vec is being modified. (2)");
    fs.blockForPending();
  }

  @Override
  public void compute2() {
    assert _rsKey.home();
    final Vec vec = DKV.getGet(_vecKey);
    while(true) {
      Value v = DKV.get(_rsKey);
      RollupStatsAry rs = (v == null) ? null : v.<RollupStatsAry>get();
      // Fetched current rs from the DKV, rs can be:
      //   a) computed
      //        a.1) has histo or histo not required => do nothing
      //        a.2) no histo and histo is required  => only compute histo
      //   b) computing => wait for the task computing it to finish and check again
      //   c) mutating  => throw IAE
      //   d) null      => compute new rollups
      if (rs != null) {
        if (rs.isReady()) {
          if (_computeHisto && !rs.hasHisto()) { // a.2 => compute rollups
            CountedCompleter cc = getCompleter(); // should be null or RPCCall
            if(cc != null) assert cc.getCompleter() == null;
            // note: if cc == null then onExceptionalCompletion tasks waiting on this may be woken up before exception handling iff exception is thrown.
            Value nnn = makeComputing();
            Futures fs = new Futures();
            Value oldv = DKV.DputIfMatch(_rsKey, nnn, v, fs);
            fs.blockForPending();
            if(oldv == v){ // got the lock
              computeHisto(rs._rs, vec, nnn);
              break;
            } // else someone else is modifying the rollups => try again
          } else
            break; // a.1 => do nothing
        } else if (rs._isComputing) { // b) => wait for current computation to finish
          rs._tsk.join();
        } else if(rs.isMutating()) // c) => throw IAE
          throw new IllegalArgumentException("Can not compute rollup stats while vec is being modified. (3)");
      } else { // d) => compute the rollups
        final Value nnn = makeComputing();
        Futures fs = new Futures();
        Value oldv = DKV.DputIfMatch(_rsKey, nnn, v, fs);
        fs.blockForPending();
        if(oldv == v){ // got the lock, compute the rollups
          RollupStatsAry.RollMRBlock r = new RollupStatsAry.RollMRBlock().doAll(vec);
          // computed the stats, now compute histo if needed and install the response and quit
          long len = vec.length();
          for(RollupStats x:r._rs)
            x._checksum ^= len;
          if(_computeHisto)
            computeHisto(r._rs, vec, nnn);
          else
            installResponse(nnn, r._rs);
          break;
        } // else someone else is modifying the rollups => try again
      }
    }
    tryComplete();
  }


  // Compute expensive histogram
  private static class Histo extends MRTask<Histo> {
    final double [] _base, _stride; // Inputs
    final int [] _nbins;            // Inputs
    long[][] _bins;                // Outputs
    Histo(H2O.H2OCountedCompleter cmp, RollupStats [] rs, int [] nbins ) {
      super(cmp);
      _base = new double[rs.length];
      _stride = new double[rs.length];
      for(int i =0; i < rs.length; ++i) {
        _base[i] = rs[i].h_base();
        _stride[i] = rs[i].h_stride();
      }
      _nbins = nbins;
    }
    @Override public void map( Chunk [] cs ) {
      _bins = new long[_nbins.length][];
      for(int i = 0; i < _bins.length; ++i) {
        long [] bins = _bins[i] = new long[_nbins[i]];
        if(bins.length == 0) continue;
        double base = _base[i];
        double stride = _stride[i];
        Chunk c = cs[i];
        for (int r = c.nextNZ(-1); r < c._len; r = c.nextNZ(r)) {
          double d = c.atd(r);
          if (!Double.isNaN(d)) bins[idx(d,base,stride,bins.length)]++;
        }
        // Sparse?  We skipped all the zeros; do them now
        if (c.isSparseZero())
          _bins[i][idx(0.0,base,stride,bins.length)] += (c._len - c.sparseLenZero());
      }
    }

    private int idx( double d , double base, double stride, int nbins) { int idx = (int)((d-base)/stride); return Math.min(idx,nbins-1); }

    @Override public void reduce( Histo h ) { ArrayUtils.add(_bins,h._bins); }
    // Just toooo common to report always.  Drowning in multi-megabyte log file writes.
    @Override public boolean logVerbose() { return false; }
  }

  private static final int MAX_NBINS = 1000; // Standard bin count; categoricals can have more bins
  final void computeHisto(final RollupStats [] rsa, AVec vec, final Value nnn) {
    // All NAs or non-math; histogram has zero bins

    int [] nbins = new int[vec.numCols()];
    for(int i = 0; i < nbins.length; ++i) {
      RollupStats rs = rsa[i];
      if (rs._naCnt == vec.length() || vec.isUUID(i)) {
        nbins[i] = 0;
        continue;
      }
      // Constant: use a single bin
      double span = rs._maxs[0] - rs._mins[0];
      final long rows = vec.length() - rs._naCnt;
      assert rows > 0 : "rows = " + rows + ", vec.len() = " + vec.length() + ", naCnt = " + rs._naCnt;
      if (span == 0) {
        nbins[i] = 1;
        continue;
      }
      // Number of bins: MAX_SIZE by default.  For integers, bins for each unique int
      // - unless the count gets too high; allow a very high count for categoricals.
      nbins[i] = MAX_NBINS;
      if (rs._isInt && span < Integer.MAX_VALUE) {
        nbins[i] = (int) span + 1;      // 1 bin per int
        int lim = vec.isCategorical(i) ? Categorical.MAX_CATEGORICAL_COUNT : MAX_NBINS;
        nbins[i] = Math.min(lim, nbins[i]); // Cap nbins at sane levels
      }
    }
    Histo histo = new Histo(null, rsa, nbins).doAll(vec);
    long rows = vec.length();
    for(long [] bins:histo._bins)
      assert ArrayUtils.sum(bins) == rows;
    for(int x = 0; x < rsa.length; ++x) {
      RollupStats rs = rsa[x];
      rs._bins = histo._bins[x];
      rs._pctiles = new double[Vec.PERCENTILES.length];
      // Compute percentiles from histogram
      int j = 0;                 // Histogram bin number
      int k = 0;                 // The next non-zero bin after j
      long hsum = 0;             // Rolling histogram sum
      double base = rs.h_base();
      double stride = rs.h_stride();
      double lastP = -1.0;       // any negative value to pass assert below first time
      for (int i = 0; i < Vec.PERCENTILES.length; i++) {
        final double P = Vec.PERCENTILES[i];
        assert P >= 0 && P <= 1 && P >= lastP;   // rely on increasing percentiles here. If P has dup then strange but accept, hence >= not >
        lastP = P;
        double pdouble = 1.0 + P * (rows - 1);   // following stats:::quantile.default type 7
        long pint = (long) pdouble;          // 1-based into bin vector
        double h = pdouble - pint;           // any fraction h to linearly interpolate between?
        assert P != 1 || (h == 0.0 && pint == rows);  // i.e. max
        while (hsum < pint) hsum += rs._bins[j++];
        // j overshot by 1 bin; we added _bins[j-1] and this goes from too low to either exactly right or too big
        // pint now falls in bin j-1 (the ++ happened even when hsum==pint), so grab that bin value now
        rs._pctiles[i] = base + stride * (j - 1);
        if (h > 0 && pint == hsum) {
          // linearly interpolate between adjacent non-zero bins
          //      i) pint is the last of (j-1)'s bin count (>1 when either duplicates exist in input, or stride makes dups at lower accuracy)
          // AND ii) h>0 so we do need to find the next non-zero bin
          if (k < j) k = j; // if j jumped over the k needed for the last P, catch k up to j
          // Saves potentially winding k forward over the same zero stretch many times
          while (rs._bins[k] == 0) k++;  // find the next non-zero bin
          rs._pctiles[i] += h * stride * (k - j + 1);
        } // otherwise either h==0 and we know which bin, or fraction is between two positions that fall in the same bin
        // this guarantees we are within one bin of the exact answer; i.e. within (max-min)/MAX_SIZE
      }
    }
    installResponse(nnn, rsa);
  }
}