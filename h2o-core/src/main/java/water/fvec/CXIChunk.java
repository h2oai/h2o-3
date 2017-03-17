package water.fvec;

import water.H2O;
import water.util.UnsafeUtils;

// Sparse chunk.
public class CXIChunk extends Chunk {
  protected static long [] _NAS = new long[]{-1/* not used, binary chunks can't have NAs */,/* not used*/-1,C2Chunk._NA,-1,C4Chunk._NA,-1,-1,-1,C8Chunk._NA};
  public static long NA(int val_sz){return _NAS[val_sz];}
  transient boolean _isNA; // na sparse or zero sparse
  transient int _val_sz;
  transient int _elem_sz;
//  transient int _elemsz_log; // 2 or 3 for 4 or 8 byte per element
  static final int _OFF = 8; // 4B of len, 1B id sz 1B value, 1B isNA, 1B padding


  protected CXIChunk(byte [] mem){
    _mem = mem;
    initFromBytes();
  }

  @Override public int sparseLenZero(){
    return isSparseZero()?sparseLen():_len;
  }

  @Override public int sparseLenNA(){
    return isSparseNA()?sparseLen():_len;
  }

  protected final int getId(int x){
    if(x == _mem.length) return _len;
    int id_sz = _elem_sz - _val_sz;
    return id_sz == 4?UnsafeUtils.get4(_mem,x):0xFFFF&UnsafeUtils.get2(_mem,x);
  }

  protected long getVal(int x){
    switch(_val_sz){
      case 0: return 1;
      case 2: return UnsafeUtils.get2(_mem,x+2);
      case 4: return UnsafeUtils.get4(_mem,x+4);
      case 8: return UnsafeUtils.get8(_mem,x+4);
      default: throw H2O.unimpl();
    }
  }

  protected double getFVal(int x){
    long ival = getVal(x);
    return ival == _NAS[_val_sz]?Double.NaN:ival;
  }

  @Override
  public final boolean isSparseNA(){return _isNA;}

  @Override
  public final boolean isSparseZero(){return !_isNA;}


  @Override
  protected void initFromBytes() {
    _start = -1;  _cidx = -1;
    _len = UnsafeUtils.get4(_mem,0);
    int id_sz = _mem[4]&0xFF;
    _val_sz = _mem[5]&0xFF;
    _elem_sz = _val_sz + id_sz;
    _isNA = (0xFF&_mem[6]) == 1;
    _previousOffset = OFF();
  }

  protected final int sparseLen(){
    return (_mem.length - OFF()) / _elem_sz;
  }
  protected final int getOff(int id, int OFF){return OFF + _elem_sz*id;}
  protected final int getIdx(int off, int OFF){return (off-OFF)/_elem_sz;}

  protected int OFF(){return _OFF;}

  protected final int findOffset(int i) { // do binary search
    int off = _previousOffset;
    int id = getId(off);
    int OFF = OFF();
    if(id == i) return off;
    if(id < i && (id = getId(off+=_elem_sz)) == i) {
      _previousOffset = off;
      return off;
    }
    int lb = id < i?getIdx(off,OFF):0;
    int ub = id > i?getIdx(off,OFF):sparseLen();
    while (lb < ub) {
      int mid = lb + ((ub - lb) >> 1);
      off = getOff(mid,OFF);
      int x = getId(off);
      if (x == i) {
        _previousOffset = off;
        return off;
      }
      if (x < i) lb = mid + 1;
      else ub = mid;
    }
    return -getOff(ub,OFF)-1;
  }

  @Override public long at8_impl(int idx){
    int x = findOffset(idx);
    if(x < 0) {
      if(_isNA) throw new RuntimeException("at8 but the value is missing!");
      return 0;
    }
    long val = getVal(x);
    if(val == _NAS[_val_sz])
      throw new RuntimeException("at4 but the value is missing!");
    return val;
  }

  @Override public double atd_impl(int idx) {
    int x = findOffset(idx);
    if(x < 0) return _isNA?Double.NaN:0;
    return getFVal(x);
  }

  @Override public final boolean isNA_impl( int i ) {return Double.isNaN(atd(i));}

  @Override
  boolean set_impl(int idx, long l) {
    return false;
  }

  @Override
  boolean set_impl(int idx, double d) {
    return false;
  }

  @Override
  boolean set_impl(int idx, float f) {
    return false;
  }

  @Override
  boolean setNA_impl(int idx) {
    return false;
  }


  @Override public boolean hasNA() { return true; }

  @Override
  public Chunk deepCopy() {return new CXIChunk(_mem.clone());}

  public final int len(){return _len;}

  private transient int _previousOffset;

  @Override public int nextNZ(int i){
    int x = findOffset(i);
    if(x < 0) x = -x-1-_elem_sz;
    _previousOffset = x += _elem_sz;
    return getId(x);
  }

  protected void processRow(ChunkVisitor v, int x){
    long val = getVal(x);
    if(val ==_NAS[_val_sz])
      v.addNAs(1);
    else
      v.addValue(val);
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to){
    int prevId = from-1;
    int x = from == 0?OFF(): findOffset(from);
    if(x < 0) x = -x-1;
    while(x < _mem.length){
      int id = getId(x);
      if(id >= to)break;
      if(_isNA) v.addNAs(id-prevId-1);
      else v.addZeros(id-prevId-1);
      processRow(v,x);
      prevId = id;
      x+=_elem_sz;
    }
    if(_isNA) v.addNAs(to-prevId-1);
    else v.addZeros(to-prevId-1);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int [] ids){
    int x = OFF();
    int k = 0;
    int zeros = 0;
    while(k < ids.length) {
      int idk = ids[k];
      assert ids[k] >= 0 && (k == 0 || ids[k] > ids[k-1]);
      int idx = ids[ids.length-1]+1;
      while(x < _mem.length && (idx = getId(x)) < idk) x += _elem_sz;
      if(x == _mem.length){
        zeros += ids.length - k;
        break;
      }
      if(idx == idk){
        if(_isNA) v.addNAs(zeros);
        else v.addZeros(zeros);
        processRow(v,x);
        zeros = 0;
        x+=_elem_sz;
      } else
        zeros++;
      k++;
    }
    if(zeros > 0){
      if(_isNA) v.addNAs(zeros);
      else v.addZeros(zeros);
    }
    return v;
  }

  @Override
  public boolean hasFloat(){return false;}
}
