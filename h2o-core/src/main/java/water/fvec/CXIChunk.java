package water.fvec;

// Sparse chunk.
public class CXIChunk extends Chunk {
  public final int _defaultVal;
  int [] _ids;
  // Sparse constructor
  protected CXIChunk(int [] ids, int defaultVal){_ids = ids; _defaultVal = defaultVal;}

  @Override
  public boolean isSparseZero(){return _defaultVal == 0;}
  public boolean isSparseNA(){return _defaultVal == C4Chunk._NA;}
  protected final int findId(int i){ // do binary search
    int lb = 0; int ub = len();
    while(lb < ub){
      int mid = lb + ((ub - lb) >> 1);
      int x = _ids[2*mid];
      if(x == i) return mid;
      if(x < i) lb = mid+1;
      else ub = mid;
    }
    return -ub-1;
  }
  @Override public final long at8(int idx){return at4(idx);}
  @Override public final int at4(int idx) {
    int id = findId(idx);
    if(id < 0) {
      if(_defaultVal == C4Chunk._NA) throw new RuntimeException("at4 but the value is missing!");
      return _defaultVal;
    }
    return _ids[2*id+1];
  }

  @Override public final double atd(int idx) {
    int id = findId(idx);
    if(id < 0) {
      if(_defaultVal == C4Chunk._NA) return Double.NaN;
      return _defaultVal;
    }
    return _ids[2*id+1];
  }

  @Override public final boolean isNA( int i ) {return Double.isNaN(atd(i));}

  @Override
  public DVal getInflated(int i, DVal v) {
    int x = findId(i);
    v._t = DVal.type.N;
    v._e = 0;
    v._m = x < 0?0:1;
    v._missing = x == C4Chunk._NA;
    return v;
  }

  @Override public boolean hasNA() { return true; }

  @Override
  public Chunk deepCopy() {return new CXIChunk(_ids.clone(),_defaultVal);}

  public final int len(){return _ids.length>>1;}

  @Override public int asSparseDoubles(double [] vals, int[] ids, double NA) {
    int len = _ids.length;
    for(int i = 0; i < len; i+=2) {
      ids[i>>1]  = _ids[i+0];
      int val = _ids[i+1];
      vals[i>>1] = (val == C4Chunk._NA)?Double.NaN:val;
    }
    return len >> 1;
  }

  public final SparseNum nextNZ(SparseNum sv) {
    if (sv._off == -1) sv._off = 0;
    if (sv._off == _ids.length) {
      sv._id = sv._len;
      sv._val = Double.NaN;
    } else {
      sv._id = _ids[sv._off++];
      int val = _ids[sv._off++];
      sv._val = (val == C4Chunk._NA) ? Double.NaN : val;
    }
    return sv;
  }

  @Override
  NewChunk add2Chunk(NewChunk nc, int from, int to){
    int prevId = from-1;
    int x = from == 0?0:findId(from);
    if(x < 0) x = -x-1;
    int len = _ids.length  >> 1;
    while(x < len){
      int idx = _ids[2*x+0];
      int val = _ids[2*x+1];
      if(idx >= to)break;
      if(_defaultVal == 0)
        nc.addZeros(idx-prevId-1);
      else {
        assert _defaultVal == C4Chunk._NA;
        nc.addNAs(idx-prevId-1);
      }
      nc.addNum(val,0);
      prevId = idx;
      x++;
    }
    if(_defaultVal == 0)
      nc.addZeros(to-prevId-1);
    else {
      assert _defaultVal == C4Chunk._NA;
      nc.addNAs(to-prevId-1);
    }
    return nc;
  }

  @Override
  NewChunk add2Chunk(NewChunk nc, int [] ids){
    int x = 0;
    int k = 0;
    int zeros = 0;
    while(k < ids.length){
      while(x < _ids.length && _ids[x] < ids[k])x+=2;
      if(x == _ids.length){
        if(_defaultVal == 0)
          nc.addZeros(zeros + ids.length-k);
        else {
          assert _defaultVal == C4Chunk._NA;
          nc.addNAs(zeros + ids.length-k);
        }
        return nc;
      }
      if(_ids[x] == ids[k]){
        if(zeros > 0){
          if(_defaultVal == 0)
            nc.addZeros(zeros);
          else {
            assert _defaultVal == C4Chunk._NA;
            nc.addNAs(zeros);
          }
          zeros = 0;
        }
        nc.addNum(_ids[x+1]);
        x+=2;
      } else
        zeros++;
      k++;
    }
    if(zeros > 0) {
      if (_defaultVal == 0)
        nc.addZeros(zeros);
      else {
        assert _defaultVal == C4Chunk._NA;
        nc.addNAs(zeros);
      }
    }
    return nc;
  }
}
