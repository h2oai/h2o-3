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
    return (super.atd_impl(i) + _bias)*_scale;
  }
  public long at8_impl(int i){
    return (long)((super.at8_impl(i)+_bias)*_scale);
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
