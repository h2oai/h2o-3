package water.fvec;

import water.util.PrettyPrint;
import water.util.UnsafeUtils;

/**
 * The scale/bias function, where data is in SIGNED bytes before scaling.
 */
public class C2SChunk extends CSChunk {
  C2SChunk( byte[] bs, long bias, int scale ) {
    super(bs,bias,scale,1);
    if(scale < 0) { // check precision
      double div = PrettyPrint.pow10(1, -scale);
      for (int i = 0; i < _len; ++i) {
        int x = getMantissa(i);
        if (x == C2Chunk._NA) continue;
        if ((getD(x, C2Chunk._NA, Double.NaN)) != (x+bias)/div){
          setDecimal();
          break;
        }
      }
    }
  }

  @Override protected final long at8_impl( int i ) {
    int x = getMantissa(i);
    if( x==C2Chunk._NA )
      throw new IllegalArgumentException("at8_abs but value is missing");
    return get8(x);
  }

  private int getMantissa(int i){return UnsafeUtils.get2(_mem,_OFF+2*i);}
  private void setMantissa(int i, short s){
    UnsafeUtils.set2(_mem,(i*2)+_OFF,s);
  }

  @Override protected final double atd_impl( int i ) {return getD(getMantissa(i),C2Chunk._NA);}
  @Override protected final boolean isNA_impl( int i ) { return getMantissa(i) == C2Chunk._NA; }

  @Override boolean set_impl(int i, double x) {
    if(Double.isNaN(x)) return setNA_impl(i);
    int y = getScaledValue(x, C2Chunk._NA);
    short s = (short)y;
    if(getD(s,C2Chunk._NA, Double.NaN) != x)
      return false;
    setMantissa(i,s);
    assert !isNA_impl(i);
    return true;
  }

  @Override boolean setNA_impl(int idx) {setMantissa(idx,(short)C2Chunk._NA); return true; }

  @Override public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; i++)
      vals[i-from] = getD(getMantissa(i),C2Chunk._NA,NA);
    return vals;
  }

  @Override public double [] getDoubles(double [] vals, int [] ids){
    int k = 0;
    for(int i:ids)
      vals[k++] = getD(getMantissa(i),C2Chunk._NA);
    return vals;
  }

  private <T extends ChunkVisitor> void processRow(T v, int i, long bias, int exp){
    long x = getMantissa(i);
    if(x == C2Chunk._NA) v.addNAs(1);
    else v.addValue(x + bias, exp);
  }

  @Override
  protected <T extends ChunkVisitor> T processRows2(T v, int from, int to, long bias, int exp) {
    for(int i = from; i < to; ++i)
      processRow(v,i,bias,exp);
    return v;
  }

  @Override
  protected <T extends ChunkVisitor> T processRows2(T v, int from, int to) {
    for(int i = from; i < to; ++i)
      v.addValue(getD(getMantissa(i),C2Chunk._NA));
    return v;
  }

  @Override
  protected <T extends ChunkVisitor> T processRows2(T v, int [] ids, long bias, int exp) {
    for(int i:ids) processRow(v,i,bias,exp);
    return v;
  }

  @Override
  protected <T extends ChunkVisitor> T processRows2(T v, int [] ids) {
    for(int i:ids)
      v.addValue(getD(getMantissa(i),C2Chunk._NA));
    return v;
  }
}
