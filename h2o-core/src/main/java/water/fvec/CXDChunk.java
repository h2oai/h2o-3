package water.fvec;

import water.H2O;
import water.util.ArrayUtils;
import water.util.UnsafeUtils;

import java.util.Iterator;

public class CXDChunk extends CXIChunk {
  protected CXDChunk(int len, int valsz, byte [] buf){super(len,valsz,buf);}

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

  @Override public long at8(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    double d = getFValue(off);
    if(Double.isNaN(d)) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)d;
  }
  @Override public double atd(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    return getFValue(off);
  }

  @Override public boolean isNA( int i ) {
    int off = findOffset(i);
    return getId(off) == i && Double.isNaN(getFValue(off));
  }


  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {
    if( from > to) throw new NegativeArraySizeException();
    if(from == to)
      return nc;
    int prev = from-1;
    int id = nextNZ(prev);
    if(id == _len) {
      nc.addZeros(to-from);
      return nc;
    }
    int cnt = 0;
    for(int off = findOffset(id);(id = getId(off)) < to; off += _ridsz + _valsz) {
      cnt++;
      assert id > prev:" id = " + id + ", prev = " + prev + ", from = " + from + ", to = " + to  + ", cnt = " + cnt;
      if(isSparseNA())
        nc.addNAs(id-prev-1);
      else
        nc.addZeros(id-prev-1);
      nc.addNum(getFValue(off));
      prev = id;
    }
    if(prev < to-1) {
      if (isSparseNA())
        nc.addNAs(to - prev - 1);
      else
        nc.addZeros(to - prev - 1);
    }
    assert nc._len > 0;
    return nc;
  }
  @Override public NewChunk add2Chunk(NewChunk nc, int [] rows) {
    if (!ArrayUtils.isSorted(rows))
      throw H2O.unimpl();
    int from = rows[0];
    int to = rows[rows.length - 1] + 1;
    if (from == to)
      return nc;
    int prev = from - 1;
    int id = nextNZ(prev);
    if (id == _len) {
      nc.addZeros(rows.length);
      return nc;
    }
    int k = 0;
    int prevK = -1;
    for (int off = findOffset(id); (id = getId(off)) < to; off += _ridsz + _valsz) {
      while (k < rows.length && rows[k] < id) k++;
      if (isSparseNA())
        nc.addNAs(k - prevK - 1);
      else
        nc.addZeros(k - prevK - 1);
      if (id == rows[k])
        nc.addNum(getFValue(off));
      prevK = k;
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
