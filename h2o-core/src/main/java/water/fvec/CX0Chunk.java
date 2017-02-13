package water.fvec;

/** specialized subtype of SPARSE chunk for boolean (bitvector); no NAs.  contains just a list of rows that are non-zero. */
public class CX0Chunk extends Chunk {
  int [] _ids;
  // Sparse constructor
  protected CX0Chunk(int [] ids){_ids = ids;}

  protected double con(){return 1;}

  @Override public boolean isSparseZero() {return true;}
  protected final int findId(int i){ // do binary search
    int lb = 0; int ub = _ids.length;
    while(lb < ub){
      int mid = lb + ((ub - lb) >> 1);
      int x = _ids[mid];
      if(x == i) return mid;
      if(x < i) lb = mid+1;
      else ub = mid;
    }
    return -ub-1;
  }

  @Override public final long at8(int idx) {return findId(idx) >= 0?(long)con():0;}
  @Override public final double atd(int idx) { return findId(idx) >= 0?con():0; }
  @Override public final boolean isNA( int i ) { return false; }
  @Override double min() { return 0; }
  @Override double max() { return 1; }

  @Override
  public DVal getInflated(int i, DVal v) {
    int x = findId(i);
    v._t = DVal.type.N;
    v._e = 0;
    v._m = x < 0?0:1;
    return v;
  }

  @Override public boolean hasNA() { return false; }

  @Override
  public Chunk deepCopy() {return new CX0Chunk(_ids.clone());}

  public final int len(){return _ids.length;}

  @Override public int asSparseDoubles(double [] vals, int[] ids, double NA) {
    int len = len();
    double con = con();
    if(vals.length < len) throw new IllegalArgumentException();
    for(int i = 0; i < len; i++) {
      ids[i] = _ids[i];
      vals[i] = con;
    }
    return len;
  }

  public final SparseNum nextNZ(SparseNum sv){
    if(sv._off == -1){
      sv._id = _ids[0];
      sv._val = con();
      sv._off = 1;
    } else  if(sv._off == _ids.length) {
      sv._id = sv._len;
      sv._val = Double.NaN;
    } else {
      sv._id = _ids[sv._off++];
    }
    return sv;
  }

  @Override
  public final NewChunk add2Chunk(NewChunk nc, int from, int to){
    int prevId = from-1;
    int x = from == 0?0:findId(from);
    if(x < 0) x = -x-1;
    double d = con();
    long l = -1;
    boolean isInt = false;
    if((long)d == d) {
      l = (long)d;
      isInt = true;
    }
    while(x < _ids.length){
      int id = _ids[x];
      if(id >= to)break;
      nc.addZeros(id-prevId-1);
      if(isInt) nc.addNum(l,0);
      else nc.addNum(d);
      prevId = id;
      x++;
    }
    nc.addZeros(to-prevId-1);
    return nc;
  }

  @Override
  public final NewChunk add2Chunk(NewChunk nc, int [] ids){
    int x = 0;
    int k = 0;
    int zeros = 0;
    double d = con();
    long l = -1;
    boolean isInt = false;
    if((long)d == d) {
      l = (long)d;
      isInt = true;
    }
    while(k < ids.length){
      assert ids[k] >= 0 && (k == 0 || ids[k] > ids[k-1]);
      while(x < _ids.length && _ids[x] < ids[k])x++;
      if(x == _ids.length){
        nc.addZeros(zeros+ids.length - k);
        return nc;
      }
      if(_ids[x] == ids[k]){
        if(zeros > 0) {
          nc.addZeros(zeros);
          zeros = 0;
        }
        if(isInt)
          nc.addNum(l,0);
        else
          nc.addNum(d);
        x++;
      } else
        zeros++;
      k++;
    }
    if(zeros > 0)nc.addZeros(zeros);
    return nc;
  }

}
