package water.fvec;

import water.util.PrettyPrint;
import water.util.UnsafeUtils;

/**
 * Created by tomas on 3/13/17.
 * Sparse fixed point
 */
public class CXSChunk extends CXIChunk {
  private transient double _scale;
  private transient long _bias;
  private transient int _exponent;

  protected CXSChunk(byte[] mem) {
    super(mem);
  }
  public static final int _OFF = CXIChunk._OFF+16; // double bias, int scale log
  public int OFF(){return _OFF;}

  @Override
  public void initFromBytes(){
    super.initFromBytes();
    _exponent = UnsafeUtils.get4(_mem,CXIChunk._OFF);
    _bias = UnsafeUtils.get8(_mem,CXIChunk._OFF+4);
    _scale = PrettyPrint.pow10(_exponent);
  }
  public double atd_impl(int i){
    int x = findOffset(i);
    if(x < 0) return _isNA?Double.NaN:0;
    return (getFVal(x)+_bias)*_scale;
  }
  public long at8_impl(int i){
    int x = findOffset(i);
    if(x < 0) {
      if(_isNA) throw new RuntimeException("at8 but the value is missing");
      return 0;
    }
    long l = getVal(x);
    if(l == _NAS[_val_sz])
      throw new RuntimeException("at8 but the value is missing");
    return (long)((l+_bias)*_scale);
  }

  protected void processRow(ChunkVisitor v, int x){
    long val = getVal(x);
    if(val ==_NAS[_val_sz])
      v.addNAs(1);
    else
      v.addValue(val + _bias,_exponent);
  }
  @Override public String toString() { return super.toString() + "(elmsz=" + _elem_sz + ",len=" + _len + ",sparseLen="+sparseLen()+",bytesize=" + _mem.length + ")";}
}
