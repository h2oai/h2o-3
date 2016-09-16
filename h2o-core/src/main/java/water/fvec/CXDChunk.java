package water.fvec;

import water.H2O;
import water.util.UnsafeUtils;

import java.util.Iterator;

public class CXDChunk extends CXIChunk {
  protected CXDChunk(int len, int valsz, byte [] buf, boolean sparseNA){super(len,valsz,buf,sparseNA);}

  // extract fp value from an (byte)offset
  protected final double getFValue(int off){
    if(valsz() == 8) return UnsafeUtils.get8d(_mem, off + ridsz());
    throw H2O.fail();
  }

  @Override public int asSparseDoubles(double [] vals, int[] ids, double NA) {
    if(vals.length < _sparseLen) throw new IllegalArgumentException();
    int off = _OFF;
    final int inc = 8 + _ridsz;
    if(_ridsz == 2){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get2(_mem,off) & 0xFFFF;
        double d = UnsafeUtils.get8d(_mem,off+2);
        vals[i] = Double.isNaN(d)?NA:d;
      }
    } else if(_ridsz == 4){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get4(_mem,off);
        double d = UnsafeUtils.get8d(_mem,off+4);
        vals[i] = Double.isNaN(d)?NA:d;

      }
    } else throw H2O.unimpl();
    return _sparseLen;
  }

  @Override public int asSparseDoubles(double [] vals, int[] ids) {
    if(vals.length < _sparseLen) throw new IllegalArgumentException();
    int off = _OFF;
    final int inc = 8 + _ridsz;
    if(_ridsz == 2){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get2(_mem,off)  & 0xFFFF;
        vals[i] = UnsafeUtils.get8d(_mem,off+2);
      }
    } else if(_ridsz == 4){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get4(_mem,off);
        vals[i] = UnsafeUtils.get8d(_mem,off+4);
      }
    } else throw H2O.unimpl();
    return _sparseLen;
  }

  @Override
  public long at8_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx){
      if(_sparseNA)
        throw new IllegalArgumentException("at8_abs but value is missing");
      return 0;
    }
    double d = getFValue(off);
    if(Double.isNaN(d)) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)d;
  }
  @Override
  public double atd_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return _sparseNA?Double.NaN:0;
    return getFValue(off);
  }

  @Override
  public boolean isNA_impl(int i) {
    int off = findOffset(i);
    return getId(off) == i && Double.isNaN(getFValue(off));
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
    int off = _OFF;
    int prev = from - 1;
    for( int i = getId(findOffset(from)); i < getId(findOffset(to)); ++i, off += _ridsz + _valsz) {
      int id = getId(off);
      int zeroCnt = id - prev - 1;
      if(zeroCnt > 0 && isSparseZero()) {
        nc.addZeros(zeroCnt);
      } else if(isSparseNA()) {
        nc.addNAs(zeroCnt);
      } else throw H2O.unimpl();
      nc.addNum(getFValue(off));
    }
    return nc;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
    for(int i:lines) {
      int off = findOffset(lines[i]);
      int j = getId(off);
      if(i == j) {
        nc.addNum(getFValue(off));
      } else if( isSparseZero()){
        nc.addNum(0.0);
      } else if(isSparseNA()) {
        nc.addNA();
      } else
        throw H2O.unimpl();
    }
    return nc;
  }

  public Iterator<Value> values(){
    return new SparseIterator(new Value(){
      @Override public final long asLong(){
        double d = getFValue(_off);
        if(Double.isNaN(d)) throw new IllegalArgumentException("at8_abs but value is missing");
        return (long)d;
      }
      @Override public final double asDouble() {return getFValue(_off);}
      @Override public final boolean isNA(){
        double d = getFValue(_off);
        return Double.isNaN(d);
      }
    });
  }
  @Override
  public boolean hasFloat() {return true;}

//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }

}
