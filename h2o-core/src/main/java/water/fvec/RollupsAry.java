package water.fvec;

import jsr166y.ForkJoinTask;
import water.Iced;
import water.util.IcedBitSet;
import water.util.PrettyPrint;

import java.util.Arrays;

/**
 * Created by tomas on 10/11/16.
 */
public class RollupsAry extends Iced {
  public int removedCnt() {return _removed.cardinality();}




  private enum State {ready, computing, mutating};

  protected final RollupStats [] _rs;
  private final IcedBitSet _removed;
  public transient final ForkJoinTask _tsk;
  public long _checksum;
  public final State _s;

  static RollupsAry makeMutating(int ncols){
    return new RollupsAry(ncols,State.mutating);
  }
  public RollupsAry(int ncols) {
    this(ncols, State.ready);
  }

  private RollupsAry(int ncols,State s){
    _rs = new RollupStats[ncols];
    for(int i = 0; i < _rs.length; ++i)
      _rs[i] = new RollupStats(0);
    _removed = new IcedBitSet(ncols);
    _s = s;
    _tsk = null;
  }

  public RollupsAry(RollupStats[]rs){
    _rs = rs; _removed = new IcedBitSet(rs.length);
    _s = State.ready;
    _tsk = null;
  }

  public RollupsAry(ForkJoinTask fjt) {
    _s = State.computing;
    _rs = null;
    _removed = null;
    _tsk = fjt;
  }
  public boolean isBad(int c, long numrows){return getRollups(c)._naCnt == numrows;}
  public double min(int c){return getRollups(c)._mins[0];}
  public double max(int c){return getRollups(c)._maxs[0];}
  public double mean(int c){return getRollups(c)._mean;}
  public double sigma(int c){return getRollups(c)._sigma;}
  public long naCnt(int c) {return getRollups(c)._naCnt;}

  public boolean isConst(int c) {
    RollupStats rs = getRollups(c);
    return rs._mins[0] == rs._maxs[0];
  }
  public boolean isMutating(int i){return _rs[i].isMutating();}

  public int numCols(){return _rs.length;}
  public boolean isRemoved(int i){return _removed.contains(i);}
  public RollupStats getRollups(int i){
    if(isMutating(i)) throw new IllegalArgumentException("Can not access rollups while the vec is being mutated.");
    if(isRemoved(i)) throw new IllegalArgumentException("Attempting to access rollups of a removed vec.");
    return _rs[i];
  }

  private static boolean computeHisto(byte t){
    switch(t){
      case Vec.T_BAD:
      case Vec.T_STR:
      case Vec.T_UUID:
        return false;
      default:
        return true;
    }
  }

  public void markRemoved(int c){
    _removed.set(c);
  }


  public boolean isReady(VecAry v,boolean computeHisto){
    if(_rs == null) return false;
    for(int i = 0; i < _rs.length; ++i){
      if(_rs == null || _rs[i] == null || _rs[i].isComputing() || !isRemoved(i) && computeHisto && computeHisto(v.getType(i)) && !_rs[i].hasHisto())
        return false;
    }
    return true;
  }

  public boolean isReady(Vec v,boolean computeHisto){
    if(_rs == null) return false;
    for(int i = 0; i < _rs.length; ++i){
      if(_rs == null || _rs[i] == null || _rs[i].isComputing() || !isRemoved(i) && computeHisto && computeHisto(v.getType(i)) && !_rs[i].hasHisto())
        return false;
    }
    return true;
  }

  @Override public String toString() {
    String [] res = new String[_rs.length];
    for(int i = 0; i < res.length; ++i) {
      if(_rs[i].isMutating())
        res[i] = "mutating";
      else
        res[i] = "[" + (_rs[i] == null ? ", {" : "," + _rs[i]._mins[0] + "/" + _rs[i]._mean + "/" + _rs[i]._maxs[0] + ", " + PrettyPrint.bytes(_rs[i]._size) + ", {");
    }
    return Arrays.toString(res);
  }

  public void reduce(RollupsAry rs) {
    for(int i = 0; i < _rs.length; ++i)
      _rs[i].reduce(rs._rs[i]);
  }

  void postGlobal(){
    for(int c = 0; c < _rs.length; ++c) {
      _rs[c]._sigma = Math.sqrt(_rs[c]._sigma / (_rs[c]._rows - 1));
      if (_rs[c]._rows == 1) _rs[c]._sigma = 0;
      if (_rs[c]._rows < 5) for (int i = 0; i < 5 - _rs[c]._rows; i++) {  // Fix PUBDEV-150 for files under 5 rows
        _rs[c]._maxs[4 - i] = Double.NaN;
        _rs[c]._mins[4 - i] = Double.NaN;
      }
    }
    for(int i = 0; i < _rs.length; ++i)
      _checksum ^= _rs[i]._checksum;
  }
  void setCategorical(int c){_rs[c]._mean = _rs[c]._sigma = Double.NaN;}

  public static RollupsAry makeComputing(ForkJoinTask fjt) {
    return new RollupsAry(fjt);
  }



  public boolean isComputing() {return _s == State.computing;}
  public boolean isMutating() {return _s == State.mutating;}
  public boolean isReady(){return _s == State.ready;}
}
