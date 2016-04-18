package water.fvec;

import water.H2O;
import water.util.UnsafeUtils;

import java.util.Arrays;
import java.util.Iterator;

// Sparse chunk.
public class CXIChunk extends Chunk {
  private transient int _valsz; // byte size of stored value
  protected final int valsz() { return _valsz; }
  private transient int _valsz_log; //
  protected transient int _ridsz; // byte size of stored (chunk-relative) row nums
  protected final int ridsz() { return _ridsz; }
  protected transient int _sparseLen;
  protected static final int _OFF = 6;
  private transient int _lastOff = _OFF;

  private static final long [] NAS = {C1Chunk._NA,C2Chunk._NA,C4Chunk._NA,C8Chunk._NA};

  protected CXIChunk(int len, int valsz, byte [] buf){
    assert (valsz == 0 || valsz == 1 || valsz == 2 || valsz == 4 || valsz == 8);
    set_len(len);
    int log = 0;
    while((1 << log) < valsz)++log;
    assert valsz == 0 || (1 << log) == valsz;
    _valsz = valsz;
    _valsz_log = log;
    _ridsz = (len >= 65535)?4:2;
    UnsafeUtils.set4(buf, 0, len);
    byte b = (byte) _ridsz;
    buf[4] = b;
    buf[5] = (byte) _valsz;
    _mem = buf;
    _sparseLen = (_mem.length - _OFF) / (_valsz+_ridsz);
    assert (_mem.length - _OFF) % (_valsz+_ridsz) == 0:"unexpected mem buffer length: mem.length = " + _mem.length + ", off = " + _OFF + ", valSz = " + _valsz + "ridsz = " + _ridsz;
  }

  @Override public double [] getDoubles(double [] vals,int from, int to, double NA){
    double fill = isSparseNA()?Double.NaN:0;
    if(from == 0 && to == _len) {
      Arrays.fill(vals,fill);
      double [] svals = new double[_sparseLen];
      int [] sids = new int[_sparseLen];
      asSparseDoubles(svals,sids, NA);
      for(int i = 0; i < sids.length && sids[i] < to; ++i)
        vals[sids[i]] = svals[i];
    } else {
      for(int i = from; i < to; ++i)
        vals[i-from] = fill;
      for(int i= nextNZ(from-1); i < to; i = nextNZ(i))
        vals[i-from] = atd(i);
    }
    return vals;
  }

  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.alloc_nums(_sparseLen);
    nc.alloc_indices(_sparseLen);
    int off = _OFF;
    for( int i = 0; i < _sparseLen; ++i, off += _ridsz + _valsz) {
      int id = getId(off);
      nc.addZeros(id-nc._len);
      long v = getIValue(off);
      if(v == NAS[_valsz_log])
        nc.addNA();
      else
        nc.addNum(v,0);
    }
    nc.set_len(_len);
    assert nc._sparseLen == _sparseLen;
    return nc;
  }

  @Override public int asSparseDoubles(double [] vals, int[] ids, double NA) {
    if(vals.length < _sparseLen)throw new IllegalArgumentException();
    int off = _OFF;
    final int inc = _valsz + _ridsz;
    if(_ridsz == 2){
      switch(_valsz){
        case 1:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get2(_mem,off) & 0xFFFF;
            long v = _mem[off+2]&0xFF;
            vals[i] = v == NAS[_valsz_log]?NA:v;
          }
          break;
        case 2:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get2(_mem,off) & 0xFFFF;
            long v = UnsafeUtils.get2(_mem,off+2);
            vals[i] = v == NAS[_valsz_log]?NA:v;
          }
          break;
        case 4:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get2(_mem,off) & 0xFFFF;
            long v = UnsafeUtils.get4(_mem,off+2);
            vals[i] = v == NAS[_valsz_log]?NA:v;
          }
          break;
        case 8:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get2(_mem,off) & 0xFFFF;
            long v = UnsafeUtils.get8(_mem,off+2);
            vals[i] = v == C8Chunk._NA?Double.NaN:v;
          }
          break;
      }
    } else if(_ridsz == 4){
      switch(_valsz){
        case 1:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get4(_mem,off);
            long v = _mem[off+4]&0xFF;
            vals[i] = v == C1Chunk._NA?NA:v;
          }
          break;
        case 2:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get4(_mem,off);
            long v = UnsafeUtils.get2(_mem,off+4);
            vals[i] = v == C2Chunk._NA?NA:v;
          }
          break;
        case 4:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get4(_mem,off);
            long v = UnsafeUtils.get4(_mem,off+4);
            vals[i] = v == C4Chunk._NA?NA:v;
          }
          break;
        case 8:
          for (int i = 0; i < _sparseLen; ++i, off += inc) {
            ids[i] = UnsafeUtils.get4(_mem,off);
            long v = UnsafeUtils.get8(_mem,off+4);
            vals[i] = v == C8Chunk._NA?NA:v;
          }
          break;
      }
    } else throw H2O.unimpl();
    return sparseLenZero();
  }

  @Override public boolean isSparseZero() {return true;}
  @Override public int sparseLenZero(){ return _sparseLen; }
  @Override public int nextNZ(int rid){
    final int off = rid == -1?_OFF:findOffset(rid);
    int x = getId(off);
    if(x > rid)return x;
    if(off < _mem.length - _ridsz - _valsz)
      return getId(off + _ridsz + _valsz);
    return _len;
  }
  /** Fills in a provided (recycled/reused) temp array of the NZ indices, and
   *  returns the count of them.  Array must be large enough. */
  @Override public int nonzeros(int [] arr){
    int off = _OFF;
    final int inc = _valsz + _ridsz;
    for(int i = 0; i < _sparseLen; ++i, off += inc) arr[i] = getId(off);
    return _sparseLen;
  }
  
  @Override boolean set_impl(int idx, long l)   { return false; }
  @Override boolean set_impl(int idx, double d) { return false; }
  @Override boolean set_impl(int idx, float f ) { return false; }
  @Override boolean setNA_impl(int idx)         { return false; }

  @Override protected long at8_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    long v = getIValue(off);
    if( v== NAS[_valsz_log])
      throw new IllegalArgumentException("at8_abs but value is missing");
    return v;
  }
  @Override protected double atd_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    long v =  getIValue(off);
    return (v == NAS[_valsz_log])?Double.NaN:v;
  }

  @Override protected boolean isNA_impl( int i ) {
    int off = findOffset(i);
    return getId(off) == i && getIValue(off) == NAS[_valsz_log];
  }



  // get id of nth (chunk-relative) stored element
  protected final int getId(int off){
    return _ridsz == 2
      ?UnsafeUtils.get2(_mem,off)&0xFFFF
      :UnsafeUtils.get4(_mem,off);
  }
  // get offset of nth (chunk-relative) stored element
  private int getOff(int n){return _OFF + (_ridsz + _valsz)*n;}
  // extract integer value from an (byte)offset
  protected double getFValue(int off){return getIValue(off);}
  protected final long getIValue(int off){
    switch(_valsz){
      case 1: return _mem[off+ _ridsz]&0xFF;
      case 2: return UnsafeUtils.get2(_mem, off + _ridsz);
      case 4: return UnsafeUtils.get4(_mem, off + _ridsz);
      case 8: return UnsafeUtils.get8(_mem, off + _ridsz);
      default: throw H2O.fail();
   } 
  }

  // find offset of the chunk-relative row id, or -1 if not stored (i.e. sparse zero)
  protected final int findOffset(int idx) {
    final byte [] mem = _mem;
    if(idx >= _len)throw new IndexOutOfBoundsException();
    if(idx <= getId(_OFF))  // easy cut off accessing the zeros prior first nz
      return _OFF;
    int last = mem.length - _ridsz - _valsz;
    if(idx >= getId(last))  // easy cut off accessing of the tail zeros
      return last;
//    if(sparseLen == 0)return 0;
    final int off = _lastOff;
    int lastIdx = getId(off);
    // check the last accessed elem + one after
    if( idx == lastIdx ) return off;
    if(idx > lastIdx){
      // check the next one (no need to check bounds, already checked at the beginning)
      final int nextOff = off + _ridsz + _valsz;
      int nextId =  getId(nextOff);
      if(idx < nextId) return off;
      if(idx == nextId){
        _lastOff = nextOff;
        return nextOff;
      }
    }
    // binary search
    int lo=0, hi = _sparseLen;
    while( lo+1 != hi ) {
      int mid = (hi+lo)>>>1;
      if( idx < getId(getOff(mid))) hi = mid;
      else          lo = mid;
    }
    int y =  getOff(lo);
    _lastOff = y;
    return y;
  }

  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len(UnsafeUtils.get4(_mem,0));
    _ridsz = _mem[4];
    _valsz = _mem[5];
    int x = _valsz;
    int log = 0;
    while(x > 1){
      x = x >>> 1;
      ++log;
    }
    _valsz_log = log;
    _sparseLen = (_mem.length - _OFF) / (_valsz+_ridsz);
    assert (_mem.length - _OFF) % (_valsz+_ridsz) == 0:"unexpected mem buffer length: meme.length = " + _mem.length + ", off = " + _OFF + ", valSz = " + _valsz + "ridsz = " + _ridsz;
  }

  public abstract  class Value {
    protected int _off = 0;
    public int rowInChunk(){return getId(_off);}
    public abstract long asLong();
    public abstract double asDouble();
    public abstract boolean isNA();
  }

  public final class SparseIterator implements Iterator<Value> {
    final Value _val;
    public SparseIterator(Value v){_val = v;}
    @Override public final boolean hasNext(){return _val._off < _mem.length - (_ridsz + _valsz);}
    @Override public final Value next(){
      if(_val._off == 0)_val._off = _OFF;
      else _val._off += (_ridsz + _valsz);
      return _val;
    }
    @Override public final void remove(){throw new UnsupportedOperationException();}
  }
  public Iterator<Value> values(){
    return new SparseIterator(new Value(){
      @Override public final long asLong(){
        long v = getIValue(_off);
        if(v == NAS[(_valsz >>> 1) - 1]) throw new IllegalArgumentException("at8_abs but value is missing");
        return v;
      }
      @Override public final double asDouble() {
      long v = getIValue(_off);
      return (v == NAS[_valsz_log -1])?Double.NaN:v;
      }
      @Override public final boolean isNA(){
        long v = getIValue(_off);
        return (v == NAS[_valsz_log]);
      }
    });
  }
  @Override
  public boolean hasFloat() {return false;}
}
