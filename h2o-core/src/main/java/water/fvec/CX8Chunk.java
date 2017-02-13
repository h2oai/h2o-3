package water.fvec;

import water.util.UnsafeUtils;

public class CX8Chunk extends ByteArraySupportedChunk {
  private transient boolean _isDouble;
  private transient boolean _isNA;
  public static int _OFF = 2;

  // Sparse constructor
  protected CX8Chunk(byte[] bits) {
    _mem = bits;
    initFromBytes();
  }

  @Override
  public boolean isSparseZero() {return !_isNA;}

  public boolean isSparseNA() {
    return _isNA;
  }

  private int id(int i) {
    return UnsafeUtils.get4(_mem, _OFF + i * 12);
  }

  private double getValD(int off) {
    if (_isDouble) return UnsafeUtils.get8d(_mem, off);
    long l = UnsafeUtils.get8(_mem, off);
    return l == C8Chunk._NA ? Double.NaN : (double) l;
  }

  private long getVal8(int off) {
    if (_isDouble) {
      double d = UnsafeUtils.get8d(_mem, off);
      if (Double.isNaN(d)) throw new RuntimeException("value is missing");
      return (long) d;
    } else {
      long l = UnsafeUtils.get8(_mem, off);
      if (l == C8Chunk._NA) throw new RuntimeException("value is missing");
      return l;
    }
  }


  protected final int findId(int i){ // do binary search
    int lb = 0; int ub = len();
    while(lb < ub){
      int mid = lb + ((ub - lb) >> 1);
      int x = id(mid);
      if(x == i) return mid;
      if(x < i) lb = mid+1;
      else ub = mid;
    }
    return -ub-1;
  }

  @Override
  public final long at8(int idx) {
    int id = findId(idx);
    if (id < 0) {
      if (_isNA) throw new RuntimeException("at8 but value is missing");
      return 0;
    }
    return getVal8(_OFF + 12 * id + 4);
  }

  @Override
  public final double atd(int idx) {
    int id = findId(idx);
    if (id < 0) return _isNA ? Double.NaN : 0;
    return getValD(_OFF + 12 * id + 4);
  }

  @Override
  public final boolean isNA(int idx) {
    int id = findId(idx);
    if (id < 0) return _isNA;
    return Double.isNaN(getValD(_OFF + 12 * id + 4));
  }

  @Override
  public DVal getInflated(int i, DVal v) {
    int x = findId(i);
    v._t = DVal.type.N;
    v._e = 0;
    v._m = x < 0 ? 0 : 1;
    v._missing = x == C4Chunk._NA;
    return v;
  }

  @Override
  public boolean hasNA() {
    return true;
  }

  @Override
  protected void initFromBytes() {
    _isDouble = _mem[0] != 0;
    _isNA = _mem[1] != 0;
  }

  public final int len() {
    return (_mem.length - _OFF) / 12;
  }

  @Override
  public int asSparseDoubles(double[] vals, int[] ids, double NA) {
    int len = _mem.length;
    int j = 0;
    for (int i = _OFF; i < _mem.length; i += 12, j++) {
      ids[j] = UnsafeUtils.get4(_mem, i);
      vals[j] = getValD(i + 4);
    }
    return j;
  }

  @Override
  public SparseNum nextNZ(SparseNum sv) {
    if (sv._off == -1) sv._off = _OFF;
    if (sv._off == _mem.length) {
      sv._id = sv._len;
      sv._val = Double.NaN;
      sv._isLong = false;
    } else {
      sv._id = UnsafeUtils.get4(_mem, sv._off);
      if(!_isDouble) {
        sv._lval = UnsafeUtils.get8(_mem, sv._off+4);
        sv._val = sv._lval == C8Chunk._NA?Double.NaN:(double)sv._lval;
        sv._isLong = true;
      } else
        sv._val = getValD(sv._off + 4);
      sv._off += 12;
    }
    return sv;
  }


  @Override
  NewChunk add2Chunk(NewChunk nc, int from, int to){
    int prevId = from-1;
    int x = from == 0?0:findId(from);
    if(x < 0) x = -x-1;
    int len = len();
    while(x < len){
      int id = id(x);
      if(id >= to)break;
      if(_isNA) nc.addNAs(id-prevId-1);
      else nc.addZeros(id-prevId-1);
      if(_isDouble) nc.addNum(getValD(_OFF + 12*x + 4));
      else nc.addNum(getVal8(_OFF + 12*x + 4),0);
      prevId = id;
      x++;
    }
    if(_isNA) nc.addNAs(to-prevId-1);
    else nc.addZeros(to-prevId-1);
    return nc;
  }

  @Override
  NewChunk add2Chunk(NewChunk nc, int [] ids){
    int x = 0;
    int k = 0;
    int zeros = 0;
    int len = len();
    int id = -1;
    while(k < ids.length){
      while(x < len && (id = id(x)) < ids[k])x++;
      if(x == len){
        if(_isNA) nc.addNAs(zeros + ids.length-k);
        else nc.addZeros(zeros + ids.length-k);
        return nc;
      }
      if(id == ids[k]){
        if(zeros > 0){
          if(_isNA) nc.addNAs(zeros);
          else nc.addZeros(zeros);
          zeros = 0;
        }
        if(_isDouble) nc.addNum(getValD(_OFF + 12*x + 4));
        else nc.addNum(getVal8(_OFF + 12*x + 4),0);
        x++;
      } else
        zeros++;
      k++;
    }
    if(zeros > 0){
      if(_isNA) nc.addNAs(zeros);
      else nc.addZeros(zeros);
    }
    return nc;
  }

}
