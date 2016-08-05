package water.fvec;

import jsr166y.CountedCompleter;
import jsr166y.ForkJoinTask;
import water.*;
import water.nbhm.NonBlockingHashMap;
import water.parser.Categorical;
import water.util.ArrayUtils;

import java.util.Arrays;

/**
 * Created by tomas on 7/12/16.
 */
public class RollupStatsBlock extends Iced {

  // Expensive histogram & percentiles
  // Computed in a 2nd pass, on-demand, by calling computeHisto
  private static final int MAX_SIZE = 1000; // Standard bin count; categoricals can have more bins

  RollupStats[] _rs;

  public RollupStatsBlock(){}

  public RollupStatsBlock(VecAry vecs){
    _rs = new RollupStats[vecs.len()];
    for(int i = 0; i < _rs.length; ++i)
      _rs[i] = new RollupStats(0);
  }


  volatile transient ForkJoinTask _tsk;

  volatile boolean _isComputing;

  public RollupStatsBlock(RollupStats[] rs) {
    _rs = rs;
  }

  public boolean isReady() {
    if (_rs == null) return false;
    for (int i = 0; i < _rs.length; ++i)
      if (!_rs[i].isReady())
        return false;
    return true;
  }

  private boolean isReady(int i) {
    RollupStats [] ary = _rs;
    return ary != null && ary[i].isReady();
  }

  public boolean hasHisto() {
    if (_rs == null) return false;
    for (int i = 0; i < _rs.length; ++i)
      if (_rs[i]._type != Vec.T_STR && !_rs[i].hasHisto())
        return false;
    return true;
  }

  private boolean hasHisto(int i) {
    RollupStats [] ary = _rs;
    return ary != null && ary[i].hasHisto();
  }

  private boolean isMutating(int i) {
    RollupStats [] ary = _rs;
    return ary != null && ary[i].isMutating();
  }

  public static RollupStats get(VecBlock vb, int i) {
    return get(vb,i,false);
  }

  private static NonBlockingHashMap<Key,RPC> _pendingRollups = new NonBlockingHashMap<>();

  public static RollupStats get(VecBlock vb, int i, boolean computeHisto) {
    if (DKV.get(vb._key) == null || !vb.hasVec(i))
      throw new RuntimeException("Rollups not possible, because Vec was deleted: " + vb._key + ", vec id = " + i);
    final Key rskey = vb.rollupStatsKey();
    RollupStatsBlock rs = DKV.getGet(rskey);
    while (rs == null || (!rs.isReady(i) || (computeHisto && !rs.hasHisto(i)))) {
      if (rs != null && rs.isMutating(i))
        throw new IllegalArgumentException("Can not compute rollup stats while vec is being modified. (1)");
      // 1. compute only once
      try {
        RPC rpcNew = new RPC(rskey.home_node(), new ComputeRollupsBlockTask(vb, computeHisto));
        RPC rpcOld = _pendingRollups.putIfAbsent(rskey, rpcNew);
        if (rpcOld == null) {  // no prior pending task, need to send this one
          rpcNew.call().get();
          _pendingRollups.remove(rskey);
        } else // rollups computation is already in progress, wait for it to finish
          rpcOld.get();
      } catch (Throwable t) {
        System.err.println("Remote rollups failed with an exception, wrapping and rethrowing: " + t);
        throw new RuntimeException(t);
      }
      // 2. fetch - done in two steps to go through standard DKV.get and enable local caching
      rs = DKV.getGet(rskey);
    }
    return rs._rs[i];
  }

  public static class RollMRBlock extends MRTask<RollMRBlock> {
    RollupStats[] _rs;

    @Override public void map(Chunk [] chks) {
      _rs = new RollupStats[_vecs.len()];
      for(int i = 0; i < chks.length; ++i)
        _rs[i] = RollupStats.computeRollups(chks[i],_vecs.isUUID(i),_vecs.isString(i));
    }
    @Override public void reduce(RollMRBlock r) {
      for(int i = 0; i < _vecs.len(); ++i)
        _rs[i].reduce(r._rs[i]);
    }
    @Override public void postGlobal(){
      for(int i = 0; i < _vecs.len(); ++i)
        _rs[i].postGlobal();
    }
  }

  public static class HistoMRBlock extends MRTask<HistoMRBlock> {
    // inputs
    double [] _base;
    double [] _stride;
    int    [] _nbins;
    // output
    long [][] _bins; // output


    HistoMRBlock(RollupStatsBlock rs, int [] toCompute, int [] nbins ) {
      _base = new double[toCompute.length];
      _stride = new double[toCompute.length];
      _nbins = new int[toCompute.length];
      for (int i = 0; i < toCompute.length; ++i) {
        _base[i] = rs._rs[i].h_base();
        _stride[i] = 1.0/rs._rs[i].h_stride(nbins[i]);
      }
    }

    private int idx( int i, double d ) { int idx = (int)((d-_base[i])*_stride[i]); return Math.min(idx,_bins.length-1); }

    // Just toooo common to report always.  Drowning in multi-megabyte log file writes.
    @Override public boolean logVerbose() { return false; }

    @Override public void map(Chunk [] chks) {
      for(int i = 0; i < chks.length; ++i) {
        _bins[i] = new long[_nbins[i]];
        Chunk c = chks[i];
        for( int j=c.nextNZ(-1); j< c._len; i=c.nextNZ(j) ) {
          double d = c.atd(j);
          if( !Double.isNaN(d) ) _bins[i][idx(i,d)]++;
        }
        // Sparse?  We skipped all the zeros; do them now
        if( c.isSparseZero() )
          _bins[i][idx(i,0.0)] += (c._len - c.sparseLenZero());
      }
    }
    @Override public void reduce(HistoMRBlock r) {
      for(int i = 0; i < _bins.length; ++i)
        ArrayUtils.add(_bins[i],r._bins[i]);
    }

    @Override public void postGlobal(){

    }

  }

  // Task to compute rollups on its homenode if needed.
  // Only computes the rollups, does not fetch them, caller should fetch them via DKV store (to preserve caching).
  // Only comutes the rollups if needed (i.e. are null or do not have histo and histo is required)
  // If rs computation is already in progress, it will wait for it to finish.
  // Throws IAE if the Vec is being modified (or removed) while this task is in progress.
  private static final class ComputeRollupsBlockTask extends DTask<ComputeRollupsBlockTask> {
    final Key _vecKey;
    final Key _rsKey;
    final boolean _computeHisto;

    public ComputeRollupsBlockTask(VecBlock vb, boolean computeHisto){
      super((byte)(Thread.currentThread() instanceof H2O.FJWThr ? currThrPriority()+1 : H2O.MIN_HI_PRIORITY-3));
      _vecKey = vb._key;
      _rsKey = vb.rollupStatsKey();
      _computeHisto = computeHisto;
    }


    private Value makeComputing(RollupStatsBlock rs){
      RollupStatsBlock newRs = (RollupStatsBlock) rs.clone();
      newRs._isComputing = true;
      CountedCompleter cc = getCompleter(); // should be null or RPCCall
      if(cc != null) assert cc.getCompleter() == null;
      newRs._tsk = cc == null?this:cc;
      return new Value(_rsKey,newRs);
    }
    private void installResponse(Value nnn, RollupStatsBlock rs) {
      Futures fs = new Futures();
      Value old = DKV.DputIfMatch(_rsKey, new Value(_rsKey, rs), nnn, fs);
      assert rs.isReady();
      if(old != nnn)
        throw new IllegalArgumentException("Can not compute rollup stats while vec is being modified. (2)");
      fs.blockForPending();
    }



    @Override
    public void compute2() {
      assert _rsKey.home();
      final VecBlock vb = DKV.getGet(_vecKey);
      while(true) {
        Value v = DKV.get(_rsKey);
        RollupStatsBlock rs = (v == null) ? null : v.<RollupStatsBlock>get();
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
              Value nnn = makeComputing(rs);
              Futures fs = new Futures();
              Value oldv = DKV.DputIfMatch(_rsKey, nnn, v, fs);
              fs.blockForPending();
              if(oldv == v){ // got the lock
                computeHisto(rs, vb, nnn);
                break;
              } // else someone else is modifying the rollups => try again
            } else
              break; // a.1 => do nothing
          } else if (rs._isComputing) { // b) => wait for current computation to finish
            rs._tsk.join();
          }
          // some vecs may be mutating while we can still get rollups for the rest.
          // let the caller throw IAE if appropriate (caller sees the mutating state as well, this was in fact duplicate test)
//          } else if(rs.isMutating()) // c) => throw IAE
//            throw new IllegalArgumentException("Can not compute rollup stats while vec is being modified. (3)");
        } else { // d) => compute the rollups
          int [] toCompute = rs.toComputeRollups();
          rs = (RollupStatsBlock) rs.clone();
          final Value nnn = makeComputing(rs);
          Futures fs = new Futures();
          Value oldv = DKV.DputIfMatch(_rsKey, nnn, v, fs);
          fs.blockForPending();
          if(oldv == v){ // got the lock, compute the rollups
            RollMRBlock r = new RollMRBlock().doAll(new VecAry(vb,toCompute));
            for(int i = 0; i < toCompute.length; ++i)
              rs._rs[toCompute[i]] = r._rs[i];
            // computed the stats, now compute histo if needed and install the response and quit
            if(_computeHisto)
              computeHisto(rs, vb, nnn);
            else
              installResponse(nnn, rs);
            break;
          } // else someone else is modifying the rollups => try again
        }
      }
      tryComplete();
    }

    final void computeHisto(RollupStatsBlock rs, VecBlock vb, final Value nnn) {
      rs = (RollupStatsBlock) rs.clone();
      // All NAs or non-math; histogram has zero bins
      int [] toCompute = rs.toComputeHisto();

      for(int i = 0; i < toCompute.length; ++i) {
        RollupStats r = rs._rs[toCompute[i]];
        if (r._naCnt == r._rows || r._type == Vec.T_UUID || r._type == Vec.T_STR) {
          r._bins = new long[0];
          rs._rs[toCompute[i]] = r;
        } else if(r._mins[0] == r._maxs[0]) {
          r._bins = new long[]{r._rows};
          rs._rs[toCompute[i]] = r;
        }
      }
      if(rs.hasHisto()) {
        installResponse(nnn, rs);
        return;
      }
      toCompute = rs.toComputeHisto();
      int [] nbins = new int[toCompute.length];
      // Constant: use a single bin
      for(int i = 0; i < nbins.length; ++i) {
        RollupStats r = rs._rs[i];
        double span = r._maxs[0] - r._mins[0];
        final long rows = r._rows;
        assert rows > 0;
        // Number of bins: MAX_SIZE by default.  For integers, bins for each unique int
        // - unless the count gets too high; allow a very high count for categoricals.
        nbins[i] = r._type == Vec.T_CAT?Categorical.MAX_CATEGORICAL_COUNT:MAX_SIZE;
        if (r._isInt && span < nbins[i])
          nbins[i] = (int)span + 1;      // 1 bin per int
      }
      HistoMRBlock histo = new HistoMRBlock(rs,toCompute,nbins).doAll(new VecAry(vb,toCompute));
      for(int i = 0; i < toCompute.length; ++i) {
        int j = toCompute[i];
        rs._rs[j]._bins = histo._bins[i];
        rs._rs[j].computePercentiles();
      }
      installResponse(nnn, rs);
    }
  }

  private int[] toComputeRollups() {
    if(isReady()) return new int[0];
    int [] res = new int[_rs.length];
    int k = 0;
    for(int i = 0; i < _rs.length; ++i) {
      RollupStats rs = _rs[i];
      if (rs == null || ((!rs.isReady()) && !rs.isMutating()))
        res[k++] = i;
    }
    return k == res.length?res: Arrays.copyOf(res,k);
  }

  private int[] toComputeHisto() {
    if(hasHisto()) return new int[0];
    int [] res = new int[_rs.length];
    int k = 0;
    for(int i = 0; i < _rs.length; ++i) {
      RollupStats rs = _rs[i];
      if (rs != null && rs._type != Vec.T_STR && !rs.isMutating() && !rs.hasHisto())
        res[k++] = i;
    }
    return k == res.length?res: Arrays.copyOf(res,k);
  }

}
