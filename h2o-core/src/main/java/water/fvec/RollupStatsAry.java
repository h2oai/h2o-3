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
public class RollupStatsAry extends Iced {

  public long byteSize(){
    long res = 0;
    for(RollupStats rs:_rs)
      res += rs._size;
    return res;
  }
  // Expensive histogram & percentiles
  // Computed in a 2nd pass, on-demand, by calling computeHisto
  private static final int MAX_SIZE = 1000; // Standard bin count; categoricals can have more bins

  public static RollupStatsAry makeComputing(){
    RollupStatsAry res = new RollupStatsAry();
    res._isComputing = true;
    return res;
  }
  public static RollupStatsAry makeMutating(){
    RollupStatsAry res = new RollupStatsAry();
    res._isMutating = true;
    return res;
  }

  RollupStats[] _rs;

  public RollupStatsAry(){}

  public RollupStatsAry(VecAry vecs){
    _rs = new RollupStats[vecs.len()];
    for(int i = 0; i < _rs.length; ++i)
      _rs[i] = new RollupStats(0);
  }


  volatile transient ForkJoinTask _tsk;

  volatile boolean _isComputing;
  volatile boolean _isMutating;

  public RollupStatsAry(RollupStats[] rs) {
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
  
  private static NonBlockingHashMap<Key,RPC> _pendingRollups = new NonBlockingHashMap<>();


  public boolean isMutating() {
    return false;
  }

  public int numCols() {
    int res = 0;
    for(RollupStats rs:_rs)
      if(rs.isRemoved())
        res++;
    return res;
  }

  public static class RollMRBlock extends MRTask<RollMRBlock> {
    RollupStats[] _rs;

    @Override public void map(Chunks chks) {
      _rs = new RollupStats[_vecs.len()];
      for(int i = 0; i < chks.numCols(); ++i)
        _rs[i] = RollupStats.computeRollups(chks.start(),chks.getChunk(i),_vecs.isUUID(i),_vecs.isString(i));
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
}
