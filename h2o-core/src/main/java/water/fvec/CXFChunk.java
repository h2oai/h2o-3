package water.fvec;

import water.H2O;
import water.util.UnsafeUtils;

// Sparse chunk.
public class CXFChunk extends CXIChunk {
  protected CXFChunk(byte [] mem){
    super(mem);
  }

  @Override public long getVal(int x){
    throw H2O.unimpl();
  }
  private double getValD(int x){
    switch(_elem_sz) {
      case 8:  return UnsafeUtils.get4f(_mem, x + 4);
      case 12: return UnsafeUtils.get8d(_mem, x + 4);
      default: throw H2O.unimpl();
    }
  }

  @Override public long at8_impl(int idx){
    int x = findOffset(idx);
    if(x < 0) {
      if(_isNA) throw new RuntimeException("at4 but the value is missing!");
      return 0;
    }
    double val = getValD(x);
    if(Double.isNaN(val)) throw new RuntimeException("at4 but the value is missing!");
    return (long)val;
  }

  @Override public double atd_impl(int idx) {
    int x = findOffset(idx);
    if(x < 0)
      return _isNA?Double.NaN:0;
    return getValD(x);
  }

  @Override
  public Chunk deepCopy() {return new CXFChunk(_mem.clone());}


  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to){
    int prevId = from-1;
    int x = from == 0?_OFF: findOffset(from);
    if(x < 0) x = -x-1;
    while(x < _mem.length){
      int id = getId(x);
      if(id >= to)break;
      if(_isNA) v.addNAs(id-prevId-1);
      else v.addZeros(id-prevId-1);
      v.addValue(getValD(x));
      prevId = id;
      x+=_elem_sz;
    }
    if(_isNA) v.addNAs(to-prevId-1);
    else v.addZeros(to-prevId-1);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int [] ids){
    int x = _OFF;
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
        v.addValue(getValD(x));
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
  public boolean hasFloat(){return true;}
}
